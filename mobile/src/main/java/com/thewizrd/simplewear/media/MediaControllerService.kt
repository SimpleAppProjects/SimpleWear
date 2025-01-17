package com.thewizrd.simplewear.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
import androidx.core.graphics.scale
import androidx.media.MediaBrowserServiceCompat
import androidx.media.VolumeProviderCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.ValueDirection
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.media.ActionItem
import com.thewizrd.shared_resources.media.BrowseMediaItems
import com.thewizrd.shared_resources.media.CustomControls
import com.thewizrd.shared_resources.media.MediaItem
import com.thewizrd.shared_resources.media.MediaMetaData
import com.thewizrd.shared_resources.media.MediaPlayerState
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState
import com.thewizrd.shared_resources.media.QueueItem
import com.thewizrd.shared_resources.media.QueueItems
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToInt
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.sequenceEqual
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.wearable.WearableManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.lang.reflect.Type
import java.util.Stack
import java.util.concurrent.Executors

class MediaControllerService : Service(), MessageClient.OnMessageReceivedListener,
    MediaSessionManager.OnActiveSessionsChangedListener {
    private lateinit var mAudioManager: AudioManager
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mMediaSessionManager: MediaSessionManager
    private lateinit var mPowerManager: PowerManager

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
    private lateinit var mMessageClient: MessageClient

    private lateinit var connectedNodes: MutableSet<String>

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

        private const val UPDATE_DELAY_MS = 500L

        fun enqueueWork(context: Context, work: Intent) {
            if (NotificationListener.isEnabled(context)) {
                ContextCompat.startForegroundService(context, work)
            }
        }
    }

    private fun startForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                JOB_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(JOB_ID, notification)
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
        mPowerManager = getSystemService(PowerManager::class.java)

        mMediaSessionManager.addOnActiveSessionsChangedListener(
            this,
            NotificationListener.getComponentName(this)
        )

        mWearableManager = WearableManager(this)
        mMessageClient = Wearable.getMessageClient(this)
        mMessageClient.addListener(this)

        connectedNodes = mutableSetOf()

        mCustomControlsAdapter = CustomControlsAdapter()
        //mBrowseMediaItemsAdapter = BrowseMediaItemsAdapter(MediaHelper.MediaBrowserItemsPath)
        //mBrowseMediaItemsExtraSuggestedAdapter = BrowseMediaItemsAdapter(MediaHelper.MediaBrowserItemsExtraSuggestedPath)
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

        startForeground(createForegroundNotification(applicationContext))

        mAvailableMediaApps = mutableSetOf()
        getMediaAppControllers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        disconnectJob?.cancel()
        startForeground(createForegroundNotification(applicationContext))

        Logger.writeLine(Log.INFO, "$TAG: Intent action = ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECTCONTROLLER -> {
                val selectedPackageName = intent.getStringExtra(EXTRA_PACKAGENAME)
                val isAutoLaunch = intent.getBooleanExtra(EXTRA_AUTOLAUNCH, false)
                val isSoftLaunch = intent.getBooleanExtra(EXTRA_SOFTLAUNCH, false)

                scope.launch {
                    if ((isAutoLaunch || selectedPackageName == mSelectedMediaApp?.packageName) && mController != null) return@launch

                    if (!selectedPackageName.isNullOrBlank()) {
                        mSelectedMediaApp = mAvailableMediaApps.find {
                            it.packageName == selectedPackageName
                        }
                        mSelectedPackageName = mSelectedMediaApp?.packageName
                        connectMediaSession(isSoftLaunch)
                    } else {
                        mSelectedPackageName = null
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

        connectedNodes.clear()
        mMessageClient.removeListener(this)
        mWearableManager.unregister()

        mMediaSessionManager.removeOnActiveSessionsChangedListener(this)

        disconnectMedia(invalidateData = true)

        stopForeground(true)
        scope.cancel()
        super.onDestroy()
    }

    private fun removeMediaState() {
        scope.launch {
            Logger.debug(TAG, "removeMediaState")
            runCatching {
                sendMediaPlayerState()
                sendMediaArtwork(bitmap = null)
                sendAppInfo(mediaAppDetails = null)
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
                if (mSelectedPackageName == null) {
                    // If so reset
                    disconnectMedia(invalidateData = true)
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
            Logger.debug(TAG, "MediaBrowser connection failed")
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

            sendAppInfo()

            Logger.debug(TAG, "MediaControllerCompat created")
            return true
        } catch (e: Exception) {
            // Failed to create MediaController from session token
            Logger.debug(TAG, "MediaBrowser connection failed")
            return false
        }
    }

    private val mCallback = object : MediaControllerCompat.Callback() {
        private var updateJob: Job? = null

        override fun onSessionReady() {
            sendMediaInfo()
            sendAppInfo()
        }

        override fun onSessionDestroyed() {
            disconnectMedia()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Logger.debug(TAG, "Callback: onPlaybackStateChanged")
            playFromSearchTimer.cancel()
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(UPDATE_DELAY_MS)

                if (!isActive) return@launch

                onUpdate()
                onUpdateQueue()
            }

            if (state != null) {
                mController?.let {
                    mCustomControlsAdapter.setActions(it, state.actions, state.customActions)
                }
            } else {
                mCustomControlsAdapter.clearActions()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Logger.debug(TAG, "Callback: onMetadataChanged")
            playFromSearchTimer.cancel()
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(UPDATE_DELAY_MS)

                if (!isActive) return@launch

                onUpdate()
                onUpdateQueue()
            }
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            mController?.let {
                mQueueItemsAdapter.setQueueItems(it, queue)
            }
        }

        override fun onAudioInfoChanged(info: MediaControllerCompat.PlaybackInfo?) {
            scope.launch { sendVolumeStatus() }
        }

        private fun onUpdate() {
            sendMediaInfo()
        }

        private fun onUpdateQueue() {
            mController?.let {
                mQueueItemsAdapter.setQueueItems(it, it.queue)
            } ?: run {
                Logger.error(TAG, "Failed to update queue info, null MediaController.")
                mQueueItemsAdapter.clear()
            }
        }
    }

    private fun sendControllerUnavailable() {
        scope.launch {
            sendMediaPlayerState()
        }
    }

    private fun sendMediaInfo() {
        if (mController == null) {
            Logger.error(TAG, "Failed to update media info, null MediaController.")
            scope.launch {
                sendMediaPlayerState()
            }
            return
        }

        val playbackState = mController?.playbackState
        val stateName = playbackState?.toPlaybackState() ?: PlaybackState.NONE

        val mediaMetadata = mController?.metadata
        val playerState: MediaPlayerState
        var mediaMetaData: MediaMetaData? = null
        var artBitmap: Bitmap? = null

        if (mediaMetadata != null && !mediaMetadata.isNullOrEmpty()) {
            artBitmap = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                ?: mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)

            val positionState = mController?.let {
                val durationMs = mediaMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                if (durationMs > 0) {
                    PositionState(
                        durationMs,
                        it.playbackState?.position ?: 0,
                        it.playbackState?.playbackSpeed ?: 1f
                    )
                } else {
                    null
                }
            }

            mediaMetaData = MediaMetaData(
                title = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                    ?: mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE),
                artist = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                    ?: mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST),
                positionState = positionState ?: PositionState()
            )

            playerState = MediaPlayerState(
                playbackState = stateName,
                mediaMetaData = mediaMetaData
            )
        } else {
            playerState = MediaPlayerState()
        }

        scope.launch {
            runCatching {
                Logger.debug(TAG, "sending media info")

                sendMediaPlayerState(playerState = playerState)
                sendMediaArtwork(bitmap = artBitmap)

                if (Settings.isBridgeMediaEnabled()) {
                    if (playbackState?.isPlaybackStateActive() == true) {
                        Logger.debug(TAG, "Create media bridge request")
                        mWearableManager.sendMessage(
                            null,
                            MediaHelper.MediaPlayerStateBridgePath,
                            JSONParser.serializer(mediaMetaData, MediaMetaData::class.java)
                                ?.stringToBytes()
                        )
                    } else {
                        Logger.debug(TAG, "Removing media bridge; playbackstate inactive")
                        mWearableManager.sendMessage(
                            null,
                            MediaHelper.MediaPlayerStateBridgePath,
                            null
                        )
                    }
                }
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    private fun sendAppInfo(mediaAppDetails: MediaAppDetails? = mSelectedMediaApp) {
        scope.launch {
            val appInfo = mediaAppDetails?.let {
                val size = dpToPx(48f).toInt()

                AppItemData(
                    label = it.appName,
                    packageName = it.packageName,
                    activityName = runCatching {
                        packageManager.getLaunchIntentForPackage(it.packageName)?.component?.className
                    }.getOrNull(),
                    iconBitmap = it.icon.scale(size, size).toByteArray()
                )
            }

            val jsonData = JSONParser.serializer(appInfo, AppItemData::class.java)?.stringToBytes()

            Logger.debug(TAG, "sendAppInfo - bytes (${jsonData?.size ?: 0})")
            mWearableManager.sendMessage(null, MediaHelper.MediaPlayerAppInfoPath, jsonData)
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

                var flags = AudioManager.FLAG_PLAY_SOUND
                if (mPowerManager.isInteractive) flags = flags or AudioManager.FLAG_SHOW_UI

                mController?.takeIf {
                    it.playbackInfo?.volumeControl == VolumeProviderCompat.VOLUME_CONTROL_RELATIVE || it.playbackInfo?.volumeControl == VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE
                }?.adjustVolume(AudioManager.ADJUST_RAISE, flags) ?: run {
                    PhoneStatusHelper.setVolume(this, ValueDirection.UP, AudioStreamType.MUSIC)
                }

                scope.launch { sendVolumeStatus(messageEvent.sourceNodeId) }
            }
            MediaHelper.MediaVolumeDownPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return

                var flags = AudioManager.FLAG_PLAY_SOUND
                if (mPowerManager.isInteractive) flags = flags or AudioManager.FLAG_SHOW_UI

                mController?.takeIf {
                    it.playbackInfo?.volumeControl == VolumeProviderCompat.VOLUME_CONTROL_RELATIVE || it.playbackInfo?.volumeControl == VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE
                }?.adjustVolume(AudioManager.ADJUST_LOWER, flags) ?: run {
                    PhoneStatusHelper.setVolume(this, ValueDirection.DOWN, AudioStreamType.MUSIC)
                }

                scope.launch { sendVolumeStatus(messageEvent.sourceNodeId) }
            }
            MediaHelper.MediaVolumeStatusPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                scope.launch { sendVolumeStatus(messageEvent.sourceNodeId) }
            }
            MediaHelper.MediaSetVolumePath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                volumeJob?.cancel()
                volumeJob = scope.launch {
                    val value = messageEvent.data.bytesToInt()

                    if (!isActive) return@launch

                    var flags = AudioManager.FLAG_PLAY_SOUND
                    if (mPowerManager.isInteractive) flags = flags or AudioManager.FLAG_SHOW_UI

                    mController?.takeIf {
                        it.playbackInfo?.volumeControl == VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE
                    }?.setVolumeTo(value, flags) ?: run {
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, flags)
                    }

                    if (!isActive) return@launch

                    sendVolumeStatus(messageEvent.sourceNodeId)
                }
            }
            MediaHelper.MediaActionsPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mCustomControlsAdapter.onDatasetChanged()
            }

            MediaHelper.MediaBrowserItemsPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsAdapter.onDatasetChanged()
            }

            MediaHelper.MediaBrowserItemsExtraSuggestedPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsExtraSuggestedAdapter.onDatasetChanged()
            }

            MediaHelper.MediaQueueItemsPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mQueueItemsAdapter.onDatasetChanged()
            }

            MediaHelper.MediaPlayerStatePath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                sendMediaInfo()
            }

            MediaHelper.MediaPlayerConnectPath -> {
                connectedNodes.add(messageEvent.sourceNodeId)
            }

            MediaHelper.MediaPlayerDisconnectPath -> {
                connectedNodes.remove(messageEvent.sourceNodeId)
            }

            MediaHelper.MediaPlayerAppInfoPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                sendAppInfo()
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

    private suspend fun sendDataByChannel(path: String, data: Any?, type: Type) {
        val jobs = connectedNodes.toList().map { node ->
            scope.async(Dispatchers.IO) {
                runCatching {
                    Wearable.getChannelClient(this@MediaControllerService).run {
                        val channel = openChannel(node, path).await()
                        val outputStream = getOutputStream(channel).await()

                        outputStream.bufferedWriter().use { writer ->
                            writer.write("data: ${JSONParser.serializer(data, type)}")
                            writer.newLine()
                            writer.flush()
                        }
                        close(channel)
                    }
                }.onFailure {
                    Logger.error(TAG, it, "error sending data to channel; path = $path")
                }
            }
        }.toTypedArray()

        awaitAll(*jobs)
    }

    private suspend fun sendMediaPlayerState(
        nodeID: String? = null,
        playerState: MediaPlayerState = MediaPlayerState()
    ) {
        mWearableManager.sendMessage(
            nodeID,
            MediaHelper.MediaPlayerStatePath,
            JSONParser.serializer(playerState, MediaPlayerState::class.java).stringToBytes()
        )
    }

    private suspend fun sendMediaArtwork(nodeID: String? = null, bitmap: Bitmap? = null) {
        val artworkBytes = bitmap?.toByteArray(format = Bitmap.CompressFormat.JPEG, quality = 50)
        Logger.debug(TAG, "sendArtwork - bytes (${artworkBytes?.size ?: 0})")
        mWearableManager.sendMessage(nodeID, MediaHelper.MediaPlayerArtPath, artworkBytes)
    }

    private suspend fun sendVolumeStatus(nodeID: String? = null) {
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

    private inner class CustomControlsAdapter {
        private var mActions: List<PlaybackStateCompat.CustomAction> = emptyList()
        private var mControls: MediaControllerCompat.TransportControls? = null
        private var mMediaAppResources: Resources? = null
        private var supportsPlayFromSearch: Boolean = false

        private var updateJob: Job? = null

        fun onDatasetChanged() {
            updateJob?.cancel()
            updateJob = scope.launch(Dispatchers.Default) {
                delay(UPDATE_DELAY_MS)

                if (!isActive) return@launch

                if (mActions.isEmpty() && !supportsPlayFromSearch) {
                    // Remove all items (datamap)
                    sendDataByChannel(
                        MediaHelper.MediaActionsPath,
                        null,
                        CustomControls::class.java
                    )
                    return@launch
                }

                // Send action items to datamap
                val actions =
                    ArrayList<ActionItem>(mActions.size + if (supportsPlayFromSearch) 1 else 0)

                val iconSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    24f,
                    resources.displayMetrics
                ).toInt()

                if (supportsPlayFromSearch) {
                    runCatching {
                        val iconDrawable = ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.ic_baseline_play_circle_filled_24
                        )
                        actions.add(
                            ActionItem(
                                action = MediaHelper.ACTIONITEM_PLAY,
                                title = getString(R.string.action_musicplayback),
                                icon = ImageUtils.bitmapFromDrawable(
                                    iconDrawable!!,
                                    iconSize,
                                    iconSize
                                ).toByteArray()
                            )
                        )
                    }
                }

                actions.addAll(
                    mActions.map {
                        ActionItem(
                            action = it.action,
                            title = it.name.toString(),
                            icon = mMediaAppResources?.let { mediaResources ->
                                val iconDrawable = try {
                                    ResourcesCompat.getDrawable(
                                        mediaResources, it.icon,  /* theme = */null
                                    )
                                } catch (e: Exception) {
                                    Logger.writeLine(Log.ERROR, e)
                                    null
                                }

                                iconDrawable?.let { drw ->
                                    ImageUtils.bitmapFromDrawable(drw, iconSize, iconSize)
                                        .toByteArray()
                                }
                            }
                        )
                    }
                )

                Logger.debug(TAG, "Sending media custom actions")

                val customControls = CustomControls(actions)
                sendDataByChannel(
                    MediaHelper.MediaActionsPath,
                    customControls,
                    CustomControls::class.java
                )
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

            mMediaAppResources = runCatching {
                packageManager.getResourcesForApplication(controller.packageName)
            }.onFailure {
                // Expected: PackageManager.NameNotFoundException
                // Shouldn't happen, because the controller must come from an installed app.
                Timber.tag(TAG).e(it, "Failed to fetch resources from media app")
            }.getOrNull()

            supportsPlayFromSearch = (actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) != 0L

            mActions = customActions
            onDatasetChanged()
        }

        fun clearActions() {
            mControls = null
            mMediaAppResources = null
            supportsPlayFromSearch = false
            mActions = emptyList()
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
            updateJob = scope.launch(Dispatchers.Default) {
                delay(UPDATE_DELAY_MS)

                if (!isActive) return@launch

                if (mNodes.size == 0 || mItems.isNullOrEmpty()) {
                    // Remove all items (datamap)
                    sendDataByChannel(itemNodePath, null, BrowseMediaItems::class.java)
                    return@launch
                }

                // Send media items to datamap
                val mediaItems = mItems?.map {
                    MediaItem(
                        mediaId = it.mediaId ?: "",
                        title = it.description.title.toString(),
                        icon = it.description.iconBitmap?.toByteArray()
                    )
                }

                Logger.debug(TAG, "Sending media browser items for path = $itemNodePath")

                val browserItems = BrowseMediaItems(
                    isRoot = treeDepth() <= 1,
                    mediaItems = mediaItems ?: emptyList()
                )
                sendDataByChannel(itemNodePath, browserItems, BrowseMediaItems::class.java)
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
            updateJob = scope.launch(Dispatchers.Default) {
                delay(UPDATE_DELAY_MS)

                if (!isActive) return@launch

                if (mQueueItems.isEmpty()) {
                    // Remove all items (datamap)
                    sendDataByChannel(MediaHelper.MediaQueueItemsPath, null, QueueItems::class.java)
                    return@launch
                }

                // Send action items to datamap
                val queueItems = mQueueItems.map {
                    QueueItem(
                        queueId = it.queueId,
                        title = it.description.title.toString(),
                        icon = it.description.iconBitmap?.toByteArray()
                    )
                }

                Logger.debug(TAG, "Sending media queue items")

                sendDataByChannel(
                    MediaHelper.MediaQueueItemsPath, QueueItems(
                        activeQueueItemId = mActiveQueueItemId,
                        queueItems = queueItems
                    ), QueueItems::class.java
                )
            }
        }

        fun onItemClicked(queueID: Long) {
            mControls?.skipToQueueItem(queueID)
        }

        fun setQueueItems(
            controller: MediaControllerCompat,
            queueItems: List<MediaSessionCompat.QueueItem>?
        ) {
            if (queueItems == null || !sequenceEqual(mQueueItems, queueItems)) {
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

        fun clear() {
            mControls = null
            mQueueItems = emptyList()
            mActiveQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            onDatasetChanged()
        }
    }

    private fun MediaMetadataCompat?.isNullOrEmpty(): Boolean {
        return this?.size() == null || this.size() <= 0
    }
}