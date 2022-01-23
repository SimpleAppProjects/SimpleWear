package com.thewizrd.simplewear.media

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.*
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.wearable.WearableManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class MediaControllerService : Service(), MessageClient.OnMessageReceivedListener,
    MediaSessionManager.OnActiveSessionsChangedListener {
    private lateinit var mAudioManager: AudioManager
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mMediaSessionManager: MediaSessionManager

    private var mSelectedMediaApp: MediaAppDetails? = null
    private var mSelectedPackageName: String? = null

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var disconnectJob: Job? = null
    private var volumeJob: Job? = null
    private lateinit var mMainHandler: Handler

    private lateinit var mAvailableMediaApps: MutableSet<MediaAppDetails>

    private lateinit var mWearableManager: WearableManager
    private lateinit var mDataClient: DataClient
    private lateinit var mMessageClient: MessageClient

    private var mController: MediaControllerCompat? = null
    private var mBrowser: MediaBrowserCompat? = null
    //private var mBrowserExtraSuggested: MediaBrowserCompat? = null

    private lateinit var mCustomControlsAdapter: CustomControlsAdapter

    //private lateinit var mBrowseMediaItemsAdapter: BrowseMediaItemsAdapter
    //private lateinit var mBrowseMediaItemsExtraSuggestedAdapter: BrowseMediaItemsAdapter
    private lateinit var mQueueItemsAdapter: QueueItemsAdapter

    private lateinit var playFromSearchTimer: CountDownTimer

    companion object {
        private const val TAG = "MediaControllerService"

        private const val JOB_ID = 1002
        private const val NOT_CHANNEL_ID = "SimpleWear.mediacontrollerservice"

        const val ACTION_CONNECTCONTROLLER = "SimpleWear.Droid.action.CONNECT_CONTROLLER"
        const val ACTION_DISCONNECTCONTROLLER = "SimpleWear.Droid.action.DISCONNECT_CONTROLLER"

        const val EXTRA_PACKAGENAME = "SimpleWear.Droid.extra.PACKAGE_NAME"
        const val EXTRA_AUTOLAUNCH = "SimpleWear.Droid.extra.AUTO_LAUNCH"
        const val EXTRA_FORCEDISCONNECT = "SimpleWear.Droid.extra.FORCE_DISCONNECT"
        const val EXTRA_SOFTLAUNCH = "SimpleWear.Droid.extra.SOFT_LAUNCH"

        fun enqueueWork(context: Context, work: Intent) {
            if (NotificationListener.isEnabled(context)) {
                ContextCompat.startForegroundService(context, work)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initChannel() {
        var mChannel = mNotificationManager.getNotificationChannel(NOT_CHANNEL_ID)
        val notChannelName = applicationContext.getString(R.string.not_channel_name_mediacontroller)
        if (mChannel == null) {
            mChannel = NotificationChannel(
                NOT_CHANNEL_ID,
                notChannelName,
                NotificationManager.IMPORTANCE_LOW
            )
        }

        // Configure channel
        mChannel.name = notChannelName
        mChannel.setShowBadge(false)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mNotificationManager.createNotificationChannel(mChannel)
    }

    private fun createForegroundNotification(context: Context): Notification {
        val notif = NotificationCompat.Builder(context, NOT_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_baseline_music_note_24)
            setContentTitle(context.getString(R.string.not_title_mediacontroller_running))
            setOnlyAlertOnce(true)
            setSilent(true)
            priority = NotificationCompat.PRIORITY_DEFAULT
            addAction(
                0,
                context.getString(R.string.action_disconnect),
                PendingIntent.getService(
                    context, 0,
                    Intent(context, MediaControllerService::class.java)
                        .setAction(ACTION_DISCONNECTCONTROLLER),
                    PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
                )
            )
        }

        return notif.build()
    }

    override fun onCreate() {
        super.onCreate()

        mMainHandler = Handler(Looper.getMainLooper())
        mAudioManager = getSystemService(AudioManager::class.java)
        mNotificationManager = getSystemService(NotificationManager::class.java)
        mMediaSessionManager = getSystemService(MediaSessionManager::class.java)

        mMediaSessionManager.addOnActiveSessionsChangedListener(
            this,
            NotificationListener.getComponentName(this)
        )

        mWearableManager = WearableManager(this)
        mDataClient = Wearable.getDataClient(this)
        mMessageClient = Wearable.getMessageClient(this)
        mMessageClient.addListener(this)

        mCustomControlsAdapter = CustomControlsAdapter()
        //mBrowseMediaItemsAdapter = BrowseMediaItemsAdapter(MediaHelper.MediaItemsPath)
        //mBrowseMediaItemsExtraSuggestedAdapter = BrowseMediaItemsAdapter(MediaHelper.MediaItemsExtraSuggestedPath)
        mQueueItemsAdapter = QueueItemsAdapter()

        playFromSearchTimer = object : CountDownTimer(3000, 500) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                if (!mAudioManager.isMusicActive) {
                    scope.launch {
                        mWearableManager.sendMessage(
                            null,
                            MediaHelper.MediaPlayPath,
                            ActionStatus.TIMEOUT.name.stringToBytes()
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }

        startForeground(JOB_ID, createForegroundNotification(applicationContext))

        mAvailableMediaApps = mutableSetOf()
        getMediaAppControllers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        disconnectJob?.cancel()
        startForeground(JOB_ID, createForegroundNotification(applicationContext))

        Logger.writeLine(Log.INFO, "$TAG: Intent action = ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECTCONTROLLER -> {
                mSelectedPackageName = intent.getStringExtra(EXTRA_PACKAGENAME)
                val isAutoLaunch = intent.getBooleanExtra(EXTRA_AUTOLAUNCH, false)
                val isSoftLaunch = intent.getBooleanExtra(EXTRA_SOFTLAUNCH, false)

                scope.launch {
                    if ((isAutoLaunch || mSelectedPackageName == mSelectedMediaApp?.packageName) && mController != null) return@launch

                    if (!mSelectedPackageName.isNullOrBlank()) {
                        mSelectedMediaApp = mAvailableMediaApps.find {
                            it.packageName == mSelectedPackageName
                        }
                        connectMediaSession(isSoftLaunch)
                    } else {
                        findActiveMediaSession()
                    }
                }
            }
            ACTION_DISCONNECTCONTROLLER -> {
                val disconnect = intent.getBooleanExtra(EXTRA_FORCEDISCONNECT, true)
                if (disconnect) {
                    disconnectJob = scope.launch {
                        // Delay in case service was just started as foreground
                        delay(1500)
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeMediaState()

        mMessageClient.removeListener(this)
        mWearableManager.unregister()

        mMediaSessionManager.removeOnActiveSessionsChangedListener(this)

        disconnectMedia()

        stopForeground(true)
        scope.cancel()
        super.onDestroy()
    }

    private fun removeMediaState() {
        scope.launch {
            Timber.tag(TAG).d("removeMediaState")
            runCatching {
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaPlayerStateBridgePath)
                ).await()
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaPlayerStatePath)
                ).await()
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaBrowserItemsPath)
                ).await()
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaActionsPath)
                ).await()
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaQueueItemsPath)
                ).await()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    private fun removeBrowserItems() {
        scope.launch {
            Timber.tag(TAG).d("removeBrowserItems")
            runCatching {
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(MediaHelper.MediaBrowserItemsPath)
                ).await()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    private fun disconnectMedia(invalidateData: Boolean = false) {
        if (mController != null) {
            runCatching {
                mController?.unregisterCallback(mCallback)
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
            mController = null
        }

        if (mBrowser?.isConnected == true) {
            runCatching {
                mBrowser?.disconnect()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
        mBrowser = null

        /*
        if (mBrowserExtraSuggested?.isConnected == true) {
            mBrowserExtraSuggested?.disconnect()
        }
        mBrowserExtraSuggested = null
        */

        if (invalidateData) {
            removeMediaState()
        }
    }

    private fun findActiveMediaSession() {
        findActiveMediaSession(
            mMediaSessionManager.getActiveSessions(
                NotificationListener.getComponentName(this@MediaControllerService)
            )
        )
    }

    private fun findActiveMediaSession(activeSessions: List<MediaController>) {
        scope.launch {
            val firstActiveCtrlr = activeSessions.firstOrNull()
            if (firstActiveCtrlr != null) {
                // Check if active session has changed
                val isPlaybackActive = isPlaybackStateActive(firstActiveCtrlr.playbackState?.state)
                if (firstActiveCtrlr.packageName != mSelectedPackageName || !isPlaybackActive) {
                    // If so reset
                    disconnectMedia(invalidateData = !isPlaybackActive)
                    mSelectedPackageName = firstActiveCtrlr.packageName
                    mSelectedMediaApp = null
                }
            }

            if (mSelectedMediaApp?.sessionToken != null || mBrowser?.isConnected == true) {
                if (setupMediaController()) return@launch
            }

            val actionSessionDetails =
                MediaAppControllerUtils.getMediaAppsFromControllers(
                    activeSessions, packageManager, resources
                )

            val activeMediaApp =
                actionSessionDetails.find { it.packageName == mSelectedPackageName }

            if (activeMediaApp?.sessionToken != null || mBrowser?.isConnected == true) {
                if (activeMediaApp != null) mSelectedMediaApp = activeMediaApp
                setupMediaController()
            } else {
                // No active sessions available
                sendControllerUnavailable()
            }
        }
    }

    private fun isPlaybackStateActive(state: Int?): Boolean {
        return when (state) {
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_CONNECTING,
            PlaybackStateCompat.STATE_FAST_FORWARDING,
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.STATE_REWINDING,
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> {
                true
            }
            else -> false
        }
    }

    private fun getMediaAppControllers() {
        scope.launch {
            val actionSessionDetails = MediaAppControllerUtils.getMediaAppsFromControllers(
                mMediaSessionManager.getActiveSessions(NotificationListener.getComponentName(this@MediaControllerService)),
                packageManager,
                resources
            )

            if (!isActive) return@launch

            mAvailableMediaApps.addAll(actionSessionDetails)

            val mediaBrowserServices = packageManager.queryIntentServices(
                Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE),
                PackageManager.GET_RESOLVED_FILTER
            )

            if (!isActive) return@launch

            if (!mediaBrowserServices.isNullOrEmpty()) {
                mediaBrowserServices.forEach { s ->
                    mAvailableMediaApps.add(
                        MediaAppDetails(
                            s.serviceInfo,
                            packageManager,
                            resources
                        )
                    )
                }
            }

            if (!mSelectedPackageName.isNullOrBlank()) {
                mAvailableMediaApps.find {
                    it.packageName == mSelectedPackageName
                }

                connectMediaSession()
            }
        }
    }

    private fun launchApp() {
        if (mSelectedMediaApp != null) {
            val i = this.packageManager.getLaunchIntentForPackage(mSelectedMediaApp!!.packageName)
            if (i?.component != null) {
                scope.launch {
                    mWearableManager.launchApp(
                        null,
                        i.component!!.packageName,
                        i.component!!.className
                    )
                }
            }
        }
    }

    private fun connectMediaSession(softLaunch: Boolean = false) {
        if (mSelectedMediaApp != null) {
            if (!softLaunch) {
                launchApp()
            }
            setupMedia()
        }
    }

    private fun setupMedia() {
        if (Looper.myLooper() == null) Looper.prepare()

        // Should now have a viable details.. connect to browser and service as needed.
        if (mSelectedMediaApp?.componentName != null) {
            val mediaAppCmpName = mSelectedMediaApp!!.componentName
            // This has to be on main thread
            mMainHandler.post {
                mBrowser = MediaBrowserCompat(
                    this.applicationContext,
                    mediaAppCmpName,
                    object : MediaBrowserCompat.ConnectionCallback() {
                        override fun onConnected() {
                            setupMediaController()
                            //mBrowseMediaItemsAdapter.setRoot(mBrowser!!.root)
                        }

                        override fun onConnectionSuspended() {
                            //mBrowseMediaItemsAdapter.setRoot(null)
                        }

                        override fun onConnectionFailed() {
                            // Try connecting to active session
                            //removeBrowserItems()
                            findActiveMediaSession()
                        }
                    },
                    null
                )
                mBrowser!!.connect()

                /*
                val bundle = Bundle().apply {
                    putBoolean(MediaBrowserServiceCompat.BrowserRoot.EXTRA_SUGGESTED, true)
                }

                mBrowserExtraSuggested = MediaBrowserCompat(this, mediaAppCmpName, object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        mBrowseMediaItemsExtraSuggestedAdapter.setRoot(mBrowserExtraSuggested!!.root)
                    }

                    override fun onConnectionSuspended() {
                        mBrowseMediaItemsExtraSuggestedAdapter.setRoot(null)
                    }

                    override fun onConnectionFailed() {
                        // Try connecting to active session
                        removeBrowserExtraItems()
                        findActiveMediaSession()
                    }
                }, bundle)
                mBrowserExtraSuggested!!.connect()
                */
            }
        } else if (mSelectedMediaApp?.sessionToken != null) {
            setupMediaController()
        } else {
            // failed
            Timber.tag(TAG).d("MediaBrowser connection failed")
        }
    }

    private fun setupMediaController(): Boolean {
        if (mSelectedMediaApp == null) return false

        try {
            var token = mSelectedMediaApp!!.sessionToken
            if (token == null) {
                token = mBrowser!!.sessionToken
            }
            mController = MediaControllerCompat(this, token)
            mController!!.registerCallback(mCallback, mMainHandler) // This has to be on main thread

            // Force update on connect
            mCallback.onPlaybackStateChanged(mController!!.playbackState)
            mCallback.onMetadataChanged(mController!!.metadata)
            mCallback.onAudioInfoChanged(mController!!.playbackInfo)

            Timber.tag(TAG).d("MediaControllerCompat created")
            return true
        } catch (e: Exception) {
            // Failed to create MediaController from session token
            Timber.tag(TAG).d("MediaBrowser connection failed")
            return false
        }
    }

    private val mCallback = object : MediaControllerCompat.Callback() {
        private var updateJob: Job? = null

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Timber.tag(TAG).d("Callback: onPlaybackStateChanged")
            playFromSearchTimer.cancel()
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                onUpdate()
                onUpdateQueue()
            }
            scope.launch {
                if (state != null) {
                    mController?.let {
                        mCustomControlsAdapter.setActions(it, state.actions, state.customActions)
                    }
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Timber.tag(TAG).d("Callback: onMetadataChanged")
            playFromSearchTimer.cancel()
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                onUpdate()
                onUpdateQueue()
            }
        }

        override fun onAudioInfoChanged(info: MediaControllerCompat.PlaybackInfo?) {
            sendVolumeStatus()
        }

        override fun onSessionDestroyed() {
            disconnectMedia()
        }

        private fun onUpdate() {
            sendMediaInfo()
        }

        private fun onUpdateQueue() {
            mController?.let {
                mQueueItemsAdapter.setQueueItems(it, it.queue)
            } ?: run {
                Timber.tag(TAG).e("Failed to update queue info, null MediaController.")
                scope.launch {
                    runCatching {
                        mDataClient.deleteDataItems(
                            WearableHelper.getWearDataUri(MediaHelper.MediaQueueItemsPath)
                        ).await()
                    }.onFailure {
                        Logger.writeLine(Log.ERROR, it)
                    }
                }
            }
        }
    }

    private fun sendControllerUnavailable() {
        val mapRequest = PutDataMapRequest.create(MediaHelper.MediaPlayerStatePath)

        mapRequest.dataMap.putString(
            MediaHelper.KEY_MEDIA_PLAYBACKSTATE,
            PlaybackState.NONE.name
        )

        // Check if supports play from search
        mapRequest.dataMap.putBoolean(
            MediaHelper.KEY_MEDIA_SUPPORTS_PLAYFROMSEARCH,
            supportsPlayFromSearch()
        )

        val request = mapRequest.asPutDataRequest()
        request.setUrgent()

        scope.launch {
            runCatching {
                Timber.tag(TAG).d("Making request: %s", mapRequest.uri)
                mDataClient.deleteDataItems(mapRequest.uri).await()
                mDataClient.putDataItem(request).await()
                Timber.tag(TAG).d("Removing media bridge")
                mDataClient.deleteDataItems(WearableHelper.getWearDataUri(MediaHelper.MediaPlayerStateBridgePath))
                    .await()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    private fun sendMediaInfo() {
        val mapRequest = PutDataMapRequest.create(MediaHelper.MediaPlayerStatePath)

        if (mController == null) {
            Timber.tag(TAG).e("Failed to update media info, null MediaController.")
            scope.launch {
                runCatching {
                    mDataClient.deleteDataItems(mapRequest.uri).await()
                    mDataClient.deleteDataItems(WearableHelper.getWearDataUri(MediaHelper.MediaPlayerStateBridgePath))
                        .await()
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
            return
        }

        val playbackState = runCatching {
            mController?.playbackState?.state
        }.onFailure {
            Logger.writeLine(Log.ERROR, it)
        }.getOrDefault(PlaybackStateCompat.STATE_NONE)

        val stateName = when (playbackState) {
            PlaybackStateCompat.STATE_NONE -> {
                PlaybackState.NONE
            }
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_CONNECTING -> {
                PlaybackState.LOADING
            }
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.STATE_FAST_FORWARDING,
            PlaybackStateCompat.STATE_REWINDING,
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> {
                PlaybackState.PLAYING
            }
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED -> {
                PlaybackState.PAUSED
            }
            else -> {
                PlaybackState.NONE
            }
        }

        mapRequest.dataMap.putString(MediaHelper.KEY_MEDIA_PLAYBACKSTATE, stateName.name)

        val mediaMetadata = mController?.metadata
        var songTitle: String? = null
        if (mediaMetadata != null && !mediaMetadata.isNullOrEmpty()) {
            mapRequest.dataMap.putString(
                MediaHelper.KEY_MEDIA_METADATA_TITLE,
                (mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                    ?: mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)).also {
                    songTitle = it
                }
            )
            mapRequest.dataMap.putString(
                MediaHelper.KEY_MEDIA_METADATA_ARTIST,
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                    ?: mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
            )

            val art = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                ?: mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            if (art != null) {
                mapRequest.dataMap.putAsset(
                    MediaHelper.KEY_MEDIA_METADATA_ART,
                    ImageUtils.createAssetFromBitmap(art)
                )
            }
        } else {
            mapRequest.dataMap.putString(
                MediaHelper.KEY_MEDIA_PLAYBACKSTATE,
                PlaybackState.NONE.name
            )
        }

        val request = mapRequest.asPutDataRequest()
        request.setUrgent()

        scope.launch {
            runCatching {
                Timber.tag(TAG).d("Making request: %s", mapRequest.uri)

                mDataClient.deleteDataItems(mapRequest.uri).await()
                mDataClient.putDataItem(request).await()

                if (Settings.isBridgeMediaEnabled()) {
                    if (isPlaybackStateActive(playbackState)) {
                        Timber.tag(TAG).d("Create media bridge request")

                        mDataClient.putDataItem(
                            PutDataMapRequest.create(MediaHelper.MediaPlayerStateBridgePath).apply {
                                dataMap.putString(MediaHelper.KEY_MEDIA_METADATA_TITLE, songTitle)
                                dataMap.putLong("time", SystemClock.uptimeMillis())
                            }
                                .setUrgent()
                                .asPutDataRequest()
                        ).await()
                    } else {
                        Timber.tag(TAG).d("Removing media bridge; playbackstate inactive")

                        mDataClient.deleteDataItems(
                            WearableHelper.getWearDataUri(MediaHelper.MediaPlayerStateBridgePath)
                        ).await()
                    }
                }
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            MediaHelper.MediaActionsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                val actionId = messageEvent.data.bytesToString()
                mCustomControlsAdapter.onItemClicked(actionId)
            }
            MediaHelper.MediaQueueItemsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                val queueId = messageEvent.data.bytesToString().toLong()
                mQueueItemsAdapter.onItemClicked(queueId)
            }
            MediaHelper.MediaBrowserItemsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                /*
                val mediaId = messageEvent.data.bytesToString()
                mBrowseMediaItemsAdapter.onItemClicked(mediaId)
                */
            }
            MediaHelper.MediaBrowserItemsExtraSuggestedClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                /*
                val mediaId = messageEvent.data.bytesToString()
                mBrowseMediaItemsExtraSuggestedAdapter.onItemClicked(mediaId)
                */
            }
            MediaHelper.MediaBrowserItemsBackPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsAdapter.onBackPressed()
            }
            MediaHelper.MediaBrowserItemsExtraSuggestedBackPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsExtraSuggestedAdapter.onBackPressed()
            }
            MediaHelper.MediaPlayPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.let {
                    if (!it.metadata.isNullOrEmpty()) {
                        it.transportControls.play()
                        playFromSearchTimer.start()
                    } else {
                        // Play random
                        playFromSearchController(null)
                    }
                } ?: playFromSearchIntent(null)
            }
            MediaHelper.MediaPausePath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.pause()
            }
            MediaHelper.MediaPreviousPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.skipToPrevious()
            }
            MediaHelper.MediaNextPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.skipToNext()
            }
            MediaHelper.MediaPlayFromSearchPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                playFromSearchController(null)
            }
            MediaHelper.MediaVolumeUpPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                if (mController != null) {
                    mController!!.adjustVolume(AudioManager.ADJUST_RAISE, 0)
                } else {
                    PhoneStatusHelper.setVolume(this, ValueDirection.UP, AudioStreamType.MUSIC)
                }
                sendVolumeStatus(messageEvent.sourceNodeId)
            }
            MediaHelper.MediaVolumeDownPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                if (mController != null) {
                    mController!!.adjustVolume(AudioManager.ADJUST_LOWER, 0)
                } else {
                    PhoneStatusHelper.setVolume(this, ValueDirection.DOWN, AudioStreamType.MUSIC)
                }
                sendVolumeStatus(messageEvent.sourceNodeId)
            }
            MediaHelper.MediaVolumeStatusPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                sendVolumeStatus(messageEvent.sourceNodeId)
            }
            MediaHelper.MediaSetVolumePath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                volumeJob?.cancel()
                volumeJob = scope.launch {
                    val value = messageEvent.data.bytesToInt()

                    if (!isActive) return@launch

                    if (mController != null) {
                        mController!!.setVolumeTo(value, 0)
                    } else {
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                    }
                    sendVolumeStatus(messageEvent.sourceNodeId)
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(messageEvent: MessageEvent): Boolean {
        if (!NotificationListener.isEnabled(this)) {
            scope.launch {
                mWearableManager.sendMessage(
                    messageEvent.sourceNodeId, messageEvent.path,
                    ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                )
            }
            return false
        }

        return true
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        playFromSearchTimer.cancel()
        findActiveMediaSession(controllers ?: emptyList())
    }

    private fun controllerSupportsPlayFromSearch(): Boolean {
        return runCatching {
            mController?.playbackState != null && mController!!.playbackState.actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH != 0L
        }.onFailure {
            Logger.writeLine(Log.ERROR, it)
        }.getOrDefault(false)
    }

    private fun supportsPlayFromSearch(): Boolean {
        if (controllerSupportsPlayFromSearch()) {
            return true
        }

        if (mSelectedMediaApp?.searchableActivityComponentName != null) {
            return true
        }

        return false
    }

    private fun playFromSearchController(query: String?) {
        mController?.let {
            it.transportControls.playFromSearch(query, null)
            playFromSearchTimer.start()
        }
    }

    private fun playFromSearchIntent(query: String?) {
        if (mSelectedMediaApp?.searchableActivityComponentName != null) {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                component = mSelectedMediaApp!!.searchableActivityComponentName
                addCategory(Intent.CATEGORY_DEFAULT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, ContentResolver.ANY_CURSOR_ITEM_TYPE)
                putExtra(SearchManager.QUERY, query)
            }

            runCatching {
                startActivity(intent)
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }

            // Notify wear device if successful
            scope.launch {
                delay(3000)
                mWearableManager.sendMessage(
                    null, MediaHelper.PlayCommandPath,
                    PhoneStatusHelper.sendPlayMusicCommand(this@MediaControllerService).name.stringToBytes()
                )
            }
        }
    }

    private fun sendVolumeStatus(nodeID: String? = null) {
        scope.launch {
            val volStatus = mController?.playbackInfo?.let {
                AudioStreamState(
                    it.currentVolume,
                    0,
                    it.maxVolume,
                    AudioStreamType.MUSIC
                )
            } ?: PhoneStatusHelper.getStreamVolume(
                this@MediaControllerService,
                AudioStreamType.MUSIC
            )

            mWearableManager.sendMessage(
                nodeID, MediaHelper.MediaVolumeStatusPath,
                JSONParser.serializer(
                    volStatus,
                    AudioStreamState::class.java
                )?.stringToBytes()
            )
        }
    }

    private inner class CustomControlsAdapter {
        private var mActions: List<PlaybackStateCompat.CustomAction> = emptyList()
        private var mControls: MediaControllerCompat.TransportControls? = null
        private var mMediaAppResources: Resources? = null
        private var supportsPlayFromSearch: Boolean = false

        private var updateJob: Job? = null

        fun onDatasetChanged() {
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                val mapRequest = PutDataMapRequest.create(MediaHelper.MediaActionsPath)

                if (mActions.isEmpty() && !supportsPlayFromSearch) {
                    // Remove all items (datamap)
                    scope.launch {
                        runCatching {
                            mDataClient.deleteDataItems(mapRequest.uri).await()
                        }.onFailure {
                            Logger.writeLine(Log.ERROR, it)
                        }
                    }
                    return@launch
                }

                // Send action items to datamap
                val dataMapList =
                    ArrayList<DataMap>(mActions.size + if (supportsPlayFromSearch) 1 else 0)

                if (supportsPlayFromSearch) {
                    val d = DataMap().apply {
                        putString(
                            MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION,
                            MediaHelper.ACTIONITEM_PLAY
                        )
                        putString(
                            MediaHelper.KEY_MEDIA_ACTIONITEM_TITLE,
                            getString(R.string.action_musicplayback)
                        )

                        val iconDrawable = ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.ic_baseline_play_circle_filled_24
                        )
                        val size = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            24f,
                            resources.displayMetrics
                        ).toInt()

                        putAsset(
                            MediaHelper.KEY_MEDIA_ACTIONITEM_ICON,
                            ImageUtils.createAssetFromBitmap(
                                ImageUtils.bitmapFromDrawable(iconDrawable!!, size, size)
                            )
                        )
                    }

                    dataMapList.add(d)
                }

                mActions.forEach {
                    val d = DataMap().apply {
                        putString(MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION, it.action)
                        putString(MediaHelper.KEY_MEDIA_ACTIONITEM_TITLE, it.name.toString())
                        if (mMediaAppResources != null) {
                            val iconDrawable = try {
                                ResourcesCompat.getDrawable(
                                    mMediaAppResources!!, it.icon,  /* theme = */null
                                )
                            } catch (e: Exception) {
                                Logger.writeLine(Log.ERROR, e)
                                null
                            }

                            if (iconDrawable != null) {
                                val size = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    24f,
                                    resources.displayMetrics
                                ).toInt()

                                putAsset(
                                    MediaHelper.KEY_MEDIA_ACTIONITEM_ICON,
                                    ImageUtils.createAssetFromBitmap(
                                        ImageUtils.bitmapFromDrawable(iconDrawable, size, size)
                                    )
                                )
                            }
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(MediaHelper.KEY_MEDIAITEMS, dataMapList)

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Timber.tag(TAG).d("Making request: %s", mapRequest.uri)
                runCatching {
                    mDataClient.deleteDataItems(mapRequest.uri).await()
                    mDataClient.putDataItem(request).await()
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }

        fun onItemClicked(actionID: String) {
            if (actionID == MediaHelper.ACTIONITEM_PLAY) {
                mControls?.playFromSearch(null, Bundle.EMPTY) ?: playFromSearchIntent(null)
                playFromSearchTimer.start()
            } else {
                val action = mActions.find { it.action == actionID } ?: return
                mControls?.sendCustomAction(action, Bundle.EMPTY)
            }
        }

        fun setActions(
            controller: MediaControllerCompat,
            actions: Long,
            customActions: List<PlaybackStateCompat.CustomAction>
        ) {
            mControls = controller.transportControls
            try {
                mMediaAppResources = packageManager
                    .getResourcesForApplication(controller.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Shouldn't happen, because the controller must come from an installed app.
                Timber.tag(TAG).e(e, "Failed to fetch resources from media app")
            }

            supportsPlayFromSearch = (actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) != 0L

            mActions = customActions
            onDatasetChanged()
        }
    }

    /**
     * Helper class which manages a MediaBrowser tree. Handles modifying the adapter when selecting
     * an item would cause the browse tree to change or play a media item. Only subscribes to a
     * single level at once.
     *
     *
     * The class keeps track of two pieces of data. (1) The Items to be displayed in mItems and
     * (2) the stack of mNodes from the root to the current node. Depending on the current state
     * different values are displayed in the adapter.
     * (a) mItems == null and mNodes.size() == 0 -> No Browser.
     * (b) mItems == null and mNodes.size() > 0 -> Loading.
     * (c) mItems != null && mItems.size() == 0 -> Empty.
     * (d) mItems.
     */
    open inner class BrowseMediaItemsAdapter(private val itemNodePath: String) {
        private var mItems: List<MediaBrowserCompat.MediaItem>? = null
        private val mNodes = Stack<String>()

        private var updateJob: Job? = null

        var callback: MediaBrowserCompat.SubscriptionCallback =
            object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(
                    parentId: String,
                    children: List<MediaBrowserCompat.MediaItem>
                ) {
                    updateItemsEmptyIfNull(children)
                }
            }

        private fun onDatasetChanged() {
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                val mapRequest = PutDataMapRequest.create(itemNodePath)

                if (mNodes.size == 0 || mItems.isNullOrEmpty()) {
                    // Remove all items (datamap)
                    scope.launch {
                        runCatching {
                            mDataClient.deleteDataItems(mapRequest.uri).await()
                        }.onFailure {
                            Logger.writeLine(Log.ERROR, it)
                        }
                    }
                    return@launch
                }

                // Send media items to datamap
                val dataMapList = ArrayList<DataMap>(mItems!!.size)
                mItems!!.forEach {
                    val d = DataMap().apply {
                        putString(MediaHelper.KEY_MEDIAITEM_ID, it.mediaId)
                        putString(
                            MediaHelper.KEY_MEDIAITEM_TITLE,
                            it.description.title.toString()
                        )
                        if (it.description.iconBitmap != null) {
                            putAsset(
                                MediaHelper.KEY_MEDIAITEM_ICON,
                                ImageUtils.createAssetFromBitmap(it.description.iconBitmap!!)
                            )
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(MediaHelper.KEY_MEDIAITEMS, dataMapList)
                mapRequest.dataMap.putBoolean(MediaHelper.KEY_MEDIAITEM_ISROOT, treeDepth() <= 1)

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Timber.tag(TAG).d("Making request: %s", mapRequest.uri)
                runCatching {
                    mDataClient.deleteDataItems(mapRequest.uri).await()
                    mDataClient.putDataItem(request).await()
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }

        fun onItemClicked(mediaID: String) {
            val item = mItems?.find { it.mediaId == mediaID } ?: return

            if (item.isBrowsable) {
                unsubscribe()
                mNodes.push(item.mediaId)
                subscribe()
            }
            if (item.isPlayable && mController != null) {
                mController!!.transportControls.playFromMediaId(
                    item.mediaId,
                    null
                )
                scope.launch {
                    mWearableManager.sendMessage(
                        null,
                        "$itemNodePath/click",
                        ActionStatus.SUCCESS.name.stringToBytes()
                    )
                }
            }
        }

        fun onBackPressed() {
            if (mNodes.size > 1) {
                unsubscribe()
                mNodes.pop()
                subscribe()
            }
        }

        fun updateItemsEmptyIfNull(items: List<MediaBrowserCompat.MediaItem>?) {
            if (items == null) {
                updateItems(emptyList())
            } else {
                updateItems(items)
            }
        }

        fun updateItems(items: List<MediaBrowserCompat.MediaItem>?) {
            mItems = items
            onDatasetChanged()
        }

        protected open fun subscribe() {
            if (mNodes.size > 0) {
                mBrowser!!.subscribe(mNodes.peek(), callback)
            }
        }

        protected open fun unsubscribe() {
            if (mNodes.size > 0) {
                mBrowser!!.unsubscribe(mNodes.peek(), callback)
            }
            updateItems(null)
        }

        fun treeDepth(): Int {
            return mNodes.size
        }

        fun getCurrentNode(): String = mNodes.peek()

        fun setRoot(root: String?) {
            unsubscribe()
            mNodes.clear()
            if (root != null) {
                mNodes.push(root)
                subscribe()
            }
        }
    }

    private inner class QueueItemsAdapter {
        private var mQueueItems: List<MediaSessionCompat.QueueItem> = emptyList()
        private var mControls: MediaControllerCompat.TransportControls? = null
        private var mActiveQueueItemId: Long = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()

        private var updateJob: Job? = null

        fun onDatasetChanged() {
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                val mapRequest = PutDataMapRequest.create(MediaHelper.MediaQueueItemsPath)

                if (mQueueItems.isEmpty()) {
                    // Remove all items (datamap)
                    scope.launch {
                        runCatching {
                            mDataClient.deleteDataItems(mapRequest.uri).await()
                        }.onFailure {
                            Logger.writeLine(Log.ERROR, it)
                        }
                    }
                    return@launch
                }

                // Send action items to datamap
                val dataMapList = ArrayList<DataMap>(mQueueItems.size)

                mQueueItems.forEach {
                    val d = DataMap().apply {
                        putLong(MediaHelper.KEY_MEDIAITEM_ID, it.queueId)
                        putString(
                            MediaHelper.KEY_MEDIAITEM_TITLE,
                            it.description.title.toString()
                        )
                        if (it.description.iconBitmap != null) {
                            putAsset(
                                MediaHelper.KEY_MEDIAITEM_ICON,
                                ImageUtils.createAssetFromBitmap(it.description.iconBitmap!!)
                            )
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(MediaHelper.KEY_MEDIAITEMS, dataMapList)
                mapRequest.dataMap.putLong(
                    MediaHelper.KEY_MEDIA_ACTIVEQUEUEITEM_ID,
                    mActiveQueueItemId
                )

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Timber.tag(TAG).d("Making request: %s", mapRequest.uri)
                runCatching {
                    mDataClient.deleteDataItems(mapRequest.uri).await()
                    mDataClient.putDataItem(request).await()
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }

        fun onItemClicked(queueID: Long) {
            mControls?.skipToQueueItem(queueID)
        }

        fun setQueueItems(
            controller: MediaControllerCompat,
            queueItems: List<MediaSessionCompat.QueueItem>?
        ) {
            mControls = controller.transportControls
            mQueueItems = queueItems ?: emptyList()
            mActiveQueueItemId = runCatching {
                controller.playbackState?.activeQueueItemId
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }.getOrNull() ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            onDatasetChanged()
        }
    }

    private fun MediaMetadataCompat?.isNullOrEmpty(): Boolean {
        return this?.size() == null || this.size() <= 0
    }
}