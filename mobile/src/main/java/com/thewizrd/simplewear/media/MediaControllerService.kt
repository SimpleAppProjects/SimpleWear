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
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.wearable.WearableManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
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
            setContentText(context.getString(R.string.not_content_mediacontroller_tap2disable))
            setOnlyAlertOnce(true)
            setNotificationSilent()
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getService(
                    context, 0,
                    Intent(context, MediaControllerService::class.java)
                        .setAction(ACTION_DISCONNECTCONTROLLER),
                    PendingIntent.FLAG_UPDATE_CURRENT
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
        //mBrowseMediaItemsAdapter = BrowseMediaItemsAdapter(WearableHelper.MediaItemsPath)
        //mBrowseMediaItemsExtraSuggestedAdapter = BrowseMediaItemsAdapter(WearableHelper.MediaItemsExtraSuggestedPath)
        mQueueItemsAdapter = QueueItemsAdapter()

        playFromSearchTimer = object : CountDownTimer(3000, 500) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                if (!mAudioManager.isMusicActive) {
                    scope.launch {
                        mWearableManager.sendMessage(
                            null,
                            WearableHelper.MediaPlayPath,
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
        Logger.writeLine(Log.INFO, "$TAG: Intent action = ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECTCONTROLLER -> {
                mSelectedPackageName = intent.getStringExtra(EXTRA_PACKAGENAME)
                val isAutoLaunch = intent.getBooleanExtra(EXTRA_AUTOLAUNCH, false)

                scope.launch {
                    if ((isAutoLaunch || mSelectedPackageName == mSelectedMediaApp?.packageName) && mController != null) return@launch

                    mSelectedMediaApp = if (mSelectedPackageName.isNullOrBlank()) {
                        mAvailableMediaApps.firstOrNull()
                    } else {
                        mAvailableMediaApps.find {
                            it.packageName == mSelectedPackageName
                        }
                    }

                    connectMediaSession()
                }
            }
            ACTION_DISCONNECTCONTROLLER -> {
                stopSelf()
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
            Log.d(TAG, "removeMediaState")
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(WearableHelper.MediaPlayerStatePath)
            ).await()
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(WearableHelper.MediaBrowserItemsPath)
            ).await()
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(WearableHelper.MediaActionsPath)
            ).await()
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(WearableHelper.MediaQueueItemsPath)
            ).await()
        }
    }

    private fun removeBrowserItems() {
        scope.launch {
            Log.d(TAG, "removeBrowserItems")
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(WearableHelper.MediaBrowserItemsPath)
            ).await()
        }
    }

    private fun disconnectMedia(invalidateData: Boolean = false) {
        if (mController != null) {
            mController?.unregisterCallback(mCallback)
            mController = null
        }

        if (mBrowser?.isConnected == true) {
            mBrowser?.disconnect()
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

    private fun connectMediaSession() {
        if (mSelectedMediaApp != null) {
            launchApp()
            setupMedia()
        }
    }

    private fun setupMedia() {
        if (Looper.myLooper() == null) Looper.prepare()

        // Should now have a viable details.. connect to browser and service as needed.
        if (mSelectedMediaApp?.componentName != null) {
            // This has to be on main thread
            mMainHandler.post {
                mBrowser = MediaBrowserCompat(
                    this.applicationContext,
                    mSelectedMediaApp!!.componentName,
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

                mBrowserExtraSuggested = MediaBrowserCompat(this, mSelectedMediaApp!!.componentName, object : MediaBrowserCompat.ConnectionCallback() {
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
            Log.d(TAG, "MediaBrowser connection failed")
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

            Log.d(TAG, "MediaControllerCompat created")
            return true
        } catch (e: Exception) {
            // Failed to create MediaController from session token
            Log.d(TAG, "MediaBrowser connection failed")
            return false
        }
    }

    private val mCallback = object : MediaControllerCompat.Callback() {
        private var updateJob: Job? = null

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "Callback: onPlaybackStateChanged")
            playFromSearchTimer.cancel()
            updateJob?.cancel()
            updateJob = scope.launch {
                delay(250)

                if (!isActive) return@launch

                onUpdate()
                onUpdateQueue()
            }
            if (state != null && mController != null) {
                mCustomControlsAdapter.setActions(mController!!, state.actions, state.customActions)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "Callback: onMetadataChanged")
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
            scope.launch {
                mWearableManager.sendAudioModeStatus(null, AudioStreamType.MUSIC)
            }
        }

        override fun onSessionDestroyed() {
            disconnectMedia()
        }

        private fun onUpdate() {
            sendMediaInfo()
        }

        private fun onUpdateQueue() {
            if (mController == null) {
                Log.e(TAG, "Failed to update queue info, null MediaController.")
                scope.launch {
                    mDataClient.deleteDataItems(
                        WearableHelper.getWearDataUri(WearableHelper.MediaQueueItemsPath)
                    ).await()
                }
                return
            }

            mQueueItemsAdapter.setQueueItems(mController!!, mController!!.queue)
        }
    }

    private fun sendControllerUnavailable() {
        val mapRequest = PutDataMapRequest.create(WearableHelper.MediaPlayerStatePath)

        mapRequest.dataMap.putString(
            WearableHelper.KEY_MEDIA_PLAYBACKSTATE,
            PlaybackState.NONE.name
        )

        // Check if supports play from search
        mapRequest.dataMap.putBoolean(
            WearableHelper.KEY_MEDIA_SUPPORTS_PLAYFROMSEARCH,
            supportsPlayFromSearch()
        )

        val request = mapRequest.asPutDataRequest()
        request.setUrgent()

        scope.launch {
            Log.d(TAG, "Making request: ${mapRequest.uri}")
            mDataClient.deleteDataItems(mapRequest.uri).await()
            mDataClient.putDataItem(request).await()
        }
    }

    private fun sendMediaInfo() {
        val mapRequest = PutDataMapRequest.create(WearableHelper.MediaPlayerStatePath)

        if (mController == null) {
            Log.e(TAG, "Failed to update media info, null MediaController.")
            scope.launch {
                mDataClient.deleteDataItems(mapRequest.uri).await()
            }
            return
        }

        val playbackState = mController?.playbackState?.state ?: PlaybackStateCompat.STATE_NONE

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

        mapRequest.dataMap.putString(WearableHelper.KEY_MEDIA_PLAYBACKSTATE, stateName.name)

        val mediaMetadata = mController?.metadata
        if (mediaMetadata != null && !mediaMetadata.isNullOrEmpty()) {
            mapRequest.dataMap.putString(
                WearableHelper.KEY_MEDIA_METADATA_TITLE,
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            )
            mapRequest.dataMap.putString(
                WearableHelper.KEY_MEDIA_METADATA_ARTIST,
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            )

            val art = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            if (art != null) {
                mapRequest.dataMap.putAsset(
                    WearableHelper.KEY_MEDIA_METADATA_ART,
                    ImageUtils.createAssetFromBitmap(art)
                )
            }
        } else {
            mapRequest.dataMap.putString(
                WearableHelper.KEY_MEDIA_PLAYBACKSTATE,
                PlaybackState.NONE.name
            )
        }

        val request = mapRequest.asPutDataRequest()
        request.setUrgent()

        scope.launch {
            Log.d(TAG, "Making request: ${mapRequest.uri}")
            mDataClient.deleteDataItems(mapRequest.uri).await()
            mDataClient.putDataItem(request).await()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableHelper.MediaActionsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                val actionId = messageEvent.data.bytesToString()
                mCustomControlsAdapter.onItemClicked(actionId)
            }
            WearableHelper.MediaQueueItemsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                val queueId = messageEvent.data.bytesToString().toLong()
                mQueueItemsAdapter.onItemClicked(queueId)
            }
            WearableHelper.MediaBrowserItemsClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                /*
                val mediaId = messageEvent.data.bytesToString()
                mBrowseMediaItemsAdapter.onItemClicked(mediaId)
                */
            }
            WearableHelper.MediaBrowserItemsExtraSuggestedClickPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                /*
                val mediaId = messageEvent.data.bytesToString()
                mBrowseMediaItemsExtraSuggestedAdapter.onItemClicked(mediaId)
                */
            }
            WearableHelper.MediaBrowserItemsBackPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsAdapter.onBackPressed()
            }
            WearableHelper.MediaBrowserItemsExtraSuggestedBackPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                //mBrowseMediaItemsExtraSuggestedAdapter.onBackPressed()
            }
            WearableHelper.MediaPlayPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                if (mController != null && !mController!!.metadata.isNullOrEmpty()) {
                    mController!!.transportControls.play()
                    playFromSearchTimer.start()
                } else {
                    // Play random
                    if (mController != null) {
                        playFromSearchController(null)
                    } else {
                        playFromSearchIntent(null)
                    }
                }
            }
            WearableHelper.MediaPausePath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.pause()
            }
            WearableHelper.MediaPreviousPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.skipToPrevious()
            }
            WearableHelper.MediaNextPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                mController?.transportControls?.skipToNext()
            }
            WearableHelper.MediaPlayFromSearchPath -> {
                if (!isNotificationListenerEnabled(messageEvent)) return
                playFromSearchController(null)
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
        return mController != null && mController!!.playbackState.actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH != 0L
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
        if (mController != null) {
            mController!!.transportControls.playFromSearch(query, null)
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

            startActivity(intent)

            // Notify wear device if successful
            scope.launch {
                delay(3000)
                mWearableManager.sendMessage(
                    null, WearableHelper.PlayCommandPath,
                    PhoneStatusHelper.sendPlayMusicCommand(this@MediaControllerService).name.stringToBytes()
                )
            }
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

                val mapRequest = PutDataMapRequest.create(WearableHelper.MediaActionsPath)

                if (mActions.isEmpty() && !supportsPlayFromSearch) {
                    // Remove all items (datamap)
                    scope.launch {
                        mDataClient.deleteDataItems(mapRequest.uri).await()
                    }
                    return@launch
                }

                // Send action items to datamap
                val dataMapList =
                    ArrayList<DataMap>(mActions.size + if (supportsPlayFromSearch) 1 else 0)

                if (supportsPlayFromSearch) {
                    val d = DataMap().apply {
                        putString(
                            WearableHelper.KEY_MEDIA_ACTIONITEM_ACTION,
                            WearableHelper.ACTIONITEM_PLAY
                        )
                        putString(
                            WearableHelper.KEY_MEDIA_ACTIONITEM_TITLE,
                            getString(R.string.action_musicplayback)
                        )

                        val iconDrawable = ContextCompat.getDrawable(
                            applicationContext,
                            R.drawable.ic_baseline_play_circle_filled_24
                        )
                        val size = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            36f,
                            resources.displayMetrics
                        ).toInt()

                        putAsset(
                            WearableHelper.KEY_MEDIA_ACTIONITEM_ICON,
                            ImageUtils.createAssetFromBitmap(
                                ImageUtils.bitmapFromDrawable(iconDrawable!!, size, size)
                            )
                        )
                    }

                    dataMapList.add(d)
                }

                mActions.forEach {
                    val d = DataMap().apply {
                        putString(WearableHelper.KEY_MEDIA_ACTIONITEM_ACTION, it.action)
                        putString(WearableHelper.KEY_MEDIA_ACTIONITEM_TITLE, it.name.toString())
                        if (mMediaAppResources != null) {
                            val iconDrawable = ResourcesCompat.getDrawable(
                                mMediaAppResources!!, it.icon,  /* theme = */null
                            )
                            if (iconDrawable != null) {
                                val size = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    36f,
                                    resources.displayMetrics
                                ).toInt()

                                putAsset(
                                    WearableHelper.KEY_MEDIA_ACTIONITEM_ICON,
                                    ImageUtils.createAssetFromBitmap(
                                        ImageUtils.bitmapFromDrawable(iconDrawable, size, size)
                                    )
                                )
                            }
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(WearableHelper.KEY_MEDIAITEMS, dataMapList)

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Log.d(TAG, "Making request: ${mapRequest.uri}")
                mDataClient.deleteDataItems(mapRequest.uri).await()
                mDataClient.putDataItem(request).await()
            }
        }

        fun onItemClicked(actionID: String) {
            if (actionID == WearableHelper.ACTIONITEM_PLAY) {
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
                Log.e(TAG, "Failed to fetch resources from media app", e)
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

                if (mNodes.size == 0) {
                    // Remove all items (datamap)
                    scope.launch {
                        mDataClient.deleteDataItems(mapRequest.uri).await()
                    }
                    return@launch
                }
                if (mItems == null) {
                    // Remove all items (datamap)
                    scope.launch {
                        mDataClient.deleteDataItems(mapRequest.uri).await()
                    }
                    return@launch
                }
                if (mItems!!.isEmpty()) {
                    // Remove all items (datamap)
                    scope.launch {
                        mDataClient.deleteDataItems(mapRequest.uri).await()
                    }
                    return@launch
                }

                // Send media items to datamap
                val dataMapList = ArrayList<DataMap>(mItems!!.size)
                mItems!!.forEach {
                    val d = DataMap().apply {
                        putString(WearableHelper.KEY_MEDIAITEM_ID, it.mediaId)
                        putString(
                            WearableHelper.KEY_MEDIAITEM_TITLE,
                            it.description.title.toString()
                        )
                        if (it.description.iconBitmap != null) {
                            putAsset(
                                WearableHelper.KEY_MEDIAITEM_ICON,
                                ImageUtils.createAssetFromBitmap(it.description.iconBitmap!!)
                            )
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(WearableHelper.KEY_MEDIAITEMS, dataMapList)
                mapRequest.dataMap.putBoolean(WearableHelper.KEY_MEDIAITEM_ISROOT, treeDepth() <= 1)

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Log.d(TAG, "Making request: ${mapRequest.uri}")
                mDataClient.deleteDataItems(mapRequest.uri).await()
                mDataClient.putDataItem(request).await()
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

                val mapRequest = PutDataMapRequest.create(WearableHelper.MediaQueueItemsPath)

                if (mQueueItems.isEmpty()) {
                    // Remove all items (datamap)
                    scope.launch {
                        mDataClient.deleteDataItems(mapRequest.uri).await()
                    }
                    return@launch
                }

                // Send action items to datamap
                val dataMapList = ArrayList<DataMap>(mQueueItems.size)

                mQueueItems.forEach {
                    val d = DataMap().apply {
                        putLong(WearableHelper.KEY_MEDIAITEM_ID, it.queueId)
                        putString(
                            WearableHelper.KEY_MEDIAITEM_TITLE,
                            it.description.title.toString()
                        )
                        if (it.description.iconBitmap != null) {
                            putAsset(
                                WearableHelper.KEY_MEDIAITEM_ICON,
                                ImageUtils.createAssetFromBitmap(it.description.iconBitmap!!)
                            )
                        }
                    }

                    dataMapList.add(d)
                }

                mapRequest.dataMap.putDataMapArrayList(WearableHelper.KEY_MEDIAITEMS, dataMapList)
                mapRequest.dataMap.putLong(
                    WearableHelper.KEY_MEDIA_ACTIVEQUEUEITEM_ID,
                    mActiveQueueItemId
                )

                val request = mapRequest.asPutDataRequest()
                request.setUrgent()

                Log.d(TAG, "Making request: ${mapRequest.uri}")
                mDataClient.deleteDataItems(mapRequest.uri).await()
                mDataClient.putDataItem(request).await()
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
            mActiveQueueItemId = controller.playbackState?.activeQueueItemId
                ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            onDatasetChanged()
        }
    }

    private fun MediaMetadataCompat?.isNullOrEmpty(): Boolean {
        return this?.size() == null || this.size() <= 0
    }
}