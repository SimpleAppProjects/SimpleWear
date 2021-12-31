package com.thewizrd.simplewear.wearable

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.google.android.clockwork.tiles.TileData
import com.google.android.clockwork.tiles.TileProviderService
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.MediaPlayerListActivity
import com.thewizrd.simplewear.R
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class MediaPlayerTileProviderService : TileProviderService(),
    MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {
    companion object {
        private const val TAG = "MediaPlayerTileProviderService"
    }

    private var mInFocus = false
    private var id = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var mPhoneNodeWithApp: Node? = null
    private var mConnectionStatus = WearConnectionStatus.DISCONNECTED

    private var mAudioStreamState: AudioStreamState? = null
    private var mPlayerStateData: PlayerStateData? = null

    private data class PlayerStateData(
        val title: String?,
        val artist: String?,
        val artwork: Bitmap?,
        val playbackState: PlaybackState
    )

    private var deleteJob: Job? = null

    override fun onDestroy() {
        Timber.tag(TAG).d("destroying service...")
        super.onDestroy()
        scope.cancel()
    }

    override fun onTileUpdate(tileId: Int) {
        Timber.tag(TAG).d("onTileUpdate called with: tileId = $tileId")

        if (!isIdForDummyData(tileId)) {
            id = tileId
            sendRemoteViews()
        }
    }

    override fun onTileFocus(tileId: Int) {
        super.onTileFocus(tileId)

        Timber.tag(TAG).d("${TAG}: onTileFocus called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            id = tileId
            mInFocus = true
            sendRemoteViews()

            Wearable.getCapabilityClient(this)
                .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
            Wearable.getMessageClient(this).addListener(this)
            Wearable.getDataClient(this).addListener(this)

            scope.launch {
                checkConnectionStatus()
                requestPlayerConnect()
                requestVolumeStatus()
                updatePlayerState()
            }
        }
    }

    override fun onTileBlur(tileId: Int) {
        super.onTileBlur(tileId)

        Timber.tag(TAG).d("${TAG}: onTileBlur called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            mInFocus = false

            Wearable.getCapabilityClient(this)
                .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getDataClient(this).removeListener(this)

            requestPlayerDisconnect()
        }
    }

    private fun sendRemoteViews() {
        Timber.tag(TAG).d("${TAG}: sendRemoteViews")
        scope.launch {
            val updateViews = buildUpdate()

            val tileData = TileData.Builder()
                .setRemoteViews(updateViews)
                .build()

            sendUpdate(id, tileData)
        }
    }

    private fun buildUpdate(): RemoteViews {
        val views: RemoteViews

        if (mConnectionStatus != WearConnectionStatus.CONNECTED) {
            views = RemoteViews(packageName, R.layout.tile_disconnected)
            when (mConnectionStatus) {
                WearConnectionStatus.APPNOTINSTALLED -> {
                    views.setTextViewText(R.id.message, getString(R.string.error_notinstalled))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.common_full_open_on_phone
                    )
                }
                else -> {
                    views.setTextViewText(R.id.message, getString(R.string.status_disconnected))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.ic_phonelink_erase_white_24dp
                    )
                }
            }
            views.setOnClickPendingIntent(R.id.tile, getTapIntent(this))
            return views
        }

        views = RemoteViews(packageName, R.layout.tile_mediaplayer)
        views.setOnClickPendingIntent(R.id.tile, getTapIntent(this))

        val playerState = mPlayerStateData

        if (playerState == null || playerState.playbackState == PlaybackState.NONE) {
            views.setViewVisibility(R.id.player_controls, View.GONE)
            views.setViewVisibility(R.id.nomedia_view, View.VISIBLE)
            views.setViewVisibility(R.id.album_art_imageview, View.GONE)
            views.setOnClickPendingIntent(
                R.id.playrandom_button,
                getActionClickIntent(this, MediaHelper.MediaPlayPath)
            )
        } else {
            views.setViewVisibility(R.id.player_controls, View.VISIBLE)
            views.setViewVisibility(R.id.nomedia_view, View.GONE)
            views.setViewVisibility(R.id.album_art_imageview, View.VISIBLE)

            views.setTextViewText(R.id.title_view, playerState.title)
            views.setTextViewText(R.id.subtitle_view, playerState.artist)
            views.setViewVisibility(
                R.id.subtitle_view,
                if (playerState.artist.isNullOrBlank()) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.play_button,
                if (playerState.playbackState != PlaybackState.PLAYING) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.pause_button,
                if (playerState.playbackState != PlaybackState.PLAYING) View.GONE else View.VISIBLE
            )
            views.setImageViewBitmap(R.id.album_art_imageview, playerState.artwork)

            views.setProgressBar(
                R.id.volume_progressBar,
                mAudioStreamState?.maxVolume ?: 100,
                mAudioStreamState?.currentVolume ?: 0,
                false
            )

            views.setOnClickPendingIntent(
                R.id.prev_button,
                getActionClickIntent(this, MediaHelper.MediaPreviousPath)
            )
            views.setOnClickPendingIntent(
                R.id.play_button,
                getActionClickIntent(this, MediaHelper.MediaPlayPath)
            )
            views.setOnClickPendingIntent(
                R.id.pause_button,
                getActionClickIntent(this, MediaHelper.MediaPausePath)
            )
            views.setOnClickPendingIntent(
                R.id.next_button,
                getActionClickIntent(this, MediaHelper.MediaNextPath)
            )

            views.setOnClickPendingIntent(
                R.id.vol_down_button,
                getActionClickIntent(this, MediaHelper.MediaVolumeDownPath)
            )
            views.setOnClickPendingIntent(
                R.id.vol_up_button,
                getActionClickIntent(this, MediaHelper.MediaVolumeUpPath)
            )
        }

        return views
    }

    private fun getTapIntent(context: Context): PendingIntent {
        val onClickIntent = Intent(context.applicationContext, MediaPlayerListActivity::class.java)
        return PendingIntent.getActivity(context, 0, onClickIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getActionClickIntent(context: Context, action: String): PendingIntent {
        val onClickIntent =
            Intent(context.applicationContext, MediaPlayerTileProviderService::class.java)
                .setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            onClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MediaHelper.MediaPlayPath -> requestPlayAction()
            MediaHelper.MediaPausePath -> requestPauseAction()
            MediaHelper.MediaPreviousPath -> requestSkipToPreviousAction()
            MediaHelper.MediaNextPath -> requestSkipToNextAction()
            MediaHelper.MediaVolumeUpPath -> requestVolumeUp()
            MediaHelper.MediaVolumeDownPath -> requestVolumeDown()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun requestPlayerConnect() {
        scope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    MediaHelper.MediaPlayerConnectPath,
                    true.booleanToBytes() // isAutoLaunch
                )
            }
        }
    }

    private fun requestPlayerDisconnect() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
            }
        }
    }

    private fun requestVolumeStatus() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeStatusPath, null)
            }
        }
    }

    private fun requestPlayAction() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayPath, null)
            }
        }
    }

    private fun requestPauseAction() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPausePath, null)
            }
        }
    }

    private fun requestSkipToPreviousAction() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPreviousPath, null)
            }
        }
    }

    private fun requestSkipToNextAction() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaNextPath, null)
            }
        }
    }

    private fun requestVolumeUp() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeUpPath, null)
            }
        }
    }

    private fun requestVolumeDown() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeDownPath, null)
            }
        }
    }

    private suspend fun updatePlayerState(dataMap: DataMap) {
        val stateName = dataMap.getString(MediaHelper.KEY_MEDIA_PLAYBACKSTATE)
        val playbackState = stateName?.let { PlaybackState.valueOf(it) } ?: PlaybackState.NONE
        val title = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_TITLE)
        val artist = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_ARTIST)
        val artBitmap = dataMap.getAsset(MediaHelper.KEY_MEDIA_METADATA_ART)?.let {
            try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(this),
                    it
                )
            } catch (e: Exception) {
                null
            }
        }

        mPlayerStateData = PlayerStateData(title, artist, artBitmap, playbackState)

        sendRemoteViews()
    }

    private fun updatePlayerState() {
        scope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(this@MediaPlayerTileProviderService)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaPlayerStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updatePlayerState(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = messageEvent.data ?: return

        scope.launch {
            when (messageEvent.path) {
                WearableHelper.AudioStatusPath,
                MediaHelper.MediaVolumeStatusPath -> {
                    val status = data.let {
                        JSONParser.deserializer(
                            it.bytesToString(),
                            AudioStreamState::class.java
                        )
                    }
                    mAudioStreamState = status

                    sendRemoteViews()
                }
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                    deleteJob?.cancel()
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    scope.launch {
                        updatePlayerState(dataMap)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                val item = event.dataItem
                if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                    deleteJob?.cancel()
                    deleteJob = scope.launch delete@{
                        delay(1000)

                        if (!isActive) return@delete

                        updatePlayerState(DataMap())
                    }
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)

            if (mPhoneNodeWithApp == null) {
                /*
                 * If a device is disconnected from the wear network, capable nodes are empty
                 *
                 * No capable nodes can mean the app is not installed on the remote device or the
                 * device is disconnected.
                 *
                 * Verify if we're connected to any nodes; if not, we're truly disconnected
                 */
                mConnectionStatus = if (connectedNodes.isNullOrEmpty()) {
                    WearConnectionStatus.DISCONNECTED
                } else {
                    WearConnectionStatus.APPNOTINSTALLED
                }
            } else {
                if (mPhoneNodeWithApp!!.isNearby && connectedNodes.any { it.id == mPhoneNodeWithApp!!.id }) {
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } else {
                    try {
                        sendPing(mPhoneNodeWithApp!!.id)
                        mConnectionStatus = WearConnectionStatus.CONNECTED
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            mConnectionStatus = WearConnectionStatus.DISCONNECTED
                        } else {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }

            if (mInFocus && !isIdForDummyData(id)) {
                sendRemoteViews()
            }
        }
    }

    private suspend fun checkConnectionStatus() {
        val connectedNodes = getConnectedNodes()
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        if (mPhoneNodeWithApp == null) {
            /*
             * If a device is disconnected from the wear network, capable nodes are empty
             *
             * No capable nodes can mean the app is not installed on the remote device or the
             * device is disconnected.
             *
             * Verify if we're connected to any nodes; if not, we're truly disconnected
             */
            mConnectionStatus = if (connectedNodes.isNullOrEmpty()) {
                WearConnectionStatus.DISCONNECTED
            } else {
                WearConnectionStatus.APPNOTINSTALLED
            }
        } else {
            if (mPhoneNodeWithApp!!.isNearby && connectedNodes.any { it.id == mPhoneNodeWithApp!!.id }) {
                mConnectionStatus = WearConnectionStatus.CONNECTED
            } else {
                try {
                    sendPing(mPhoneNodeWithApp!!.id)
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        mConnectionStatus = WearConnectionStatus.DISCONNECTED
                    } else {
                        Logger.writeLine(Log.ERROR, e)
                    }
                }
            }
        }

        if (mInFocus && !isIdForDummyData(id)) {
            sendRemoteViews()
        }
    }

    private suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(this)
                .getCapability(
                    WearableHelper.CAPABILITY_PHONE_APP,
                    CapabilityClient.FILTER_ALL
                )
                .await()
            node = pickBestNodeId(capabilityInfo.nodes)
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }

        return node
    }

    private suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
    */
    private fun pickBestNodeId(nodes: Collection<Node>): Node? {
        var bestNode: Node? = null

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (node in nodes) {
            if (node.isNearby) {
                return node
            }
            bestNode = node
        }
        return bestNode
    }

    private suspend fun getConnectedNodes(): List<Node> {
        try {
            return Wearable.getNodeClient(this)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }

        return emptyList()
    }

    private suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeID, path, data)
                .await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED

                    if (mInFocus && !isIdForDummyData(id)) {
                        sendRemoteViews()
                    }
                    return
                }
            }

            Logger.writeLine(Log.ERROR, e)
        }
    }

    @Throws(ApiException::class)
    private suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as ApiException
                throw apiException
            }
            Logger.writeLine(Log.ERROR, e)
        }
    }
}