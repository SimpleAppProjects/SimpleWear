package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.pickBestNodeId
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileProviderService.Companion.requestTileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

class MediaPlayerTileMessenger(private val context: Context) :
    MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {
    companion object {
        private const val TAG = "MediaPlayerTileMessenger"
        internal val tileModel by lazy { MediaPlayerTileModel() }
    }

    enum class PlayerAction {
        PLAY,
        PAUSE,
        PREVIOUS,
        NEXT,
        VOL_UP,
        VOL_DOWN
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var mPhoneNodeWithApp: Node? = null

    private var deleteJob: Job? = null
    private var updateJob: Job? = null

    fun register() {
        Wearable.getCapabilityClient(context)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .addListener(this)

        Wearable.getDataClient(context)
            .addListener(this)
    }

    fun unregister() {
        Wearable.getCapabilityClient(context)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .removeListener(this)

        Wearable.getDataClient(context)
            .removeListener(this)

        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = messageEvent.data ?: return

        Timber.tag(TAG).d("message received - path: ${messageEvent.path}")

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
                    tileModel.setAudioStreamState(status)

                    requestTileUpdate(context)
                }
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        val event =
            dataEventBuffer.findLast { it.dataItem.uri.path == MediaHelper.MediaPlayerStatePath }

        if (event != null) {
            processDataEvent(event)
        }
    }

    private fun processDataEvent(event: DataEvent) {
        val item = event.dataItem

        if (event.type == DataEvent.TYPE_CHANGED) {
            Timber.tag(TAG).d("processDataEvent: data changed")

            deleteJob?.cancel()
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            updateJob?.cancel()
            updateJob = scope.launch {
                updatePlayerState(dataMap)
            }
        } else if (event.type == DataEvent.TYPE_DELETED) {
            Timber.tag(TAG).d("processDataEvent: data deleted")

            deleteJob?.cancel()
            deleteJob = scope.launch delete@{
                delay(1000)

                if (!isActive) return@delete

                updatePlayerState(DataMap())
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)

            mPhoneNodeWithApp?.let { node ->
                if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                    tileModel.setConnectionStatus(WearConnectionStatus.CONNECTED)
                } else {
                    try {
                        sendPing(node.id)
                        tileModel.setConnectionStatus(WearConnectionStatus.CONNECTED)
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            tileModel.setConnectionStatus(WearConnectionStatus.DISCONNECTED)
                        } else {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            } ?: run {
                /*
                 * If a device is disconnected from the wear network, capable nodes are empty
                 *
                 * No capable nodes can mean the app is not installed on the remote device or the
                 * device is disconnected.
                 *
                 * Verify if we're connected to any nodes; if not, we're truly disconnected
                 */
                tileModel.setConnectionStatus(
                    if (connectedNodes.isEmpty()) {
                        WearConnectionStatus.DISCONNECTED
                    } else {
                        WearConnectionStatus.APPNOTINSTALLED
                    }
                )
            }

            requestTileUpdate(context)
        }
    }

    suspend fun requestPlayerConnect() {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                MediaHelper.MediaPlayerConnectPath,
                true.booleanToBytes() // isAutoLaunch
            )
        }
    }

    suspend fun requestPlayerDisconnect() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
        }
    }

    suspend fun requestVolumeStatus() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeStatusPath, null)
        }
    }

    suspend fun requestPlayerAction(action: PlayerAction) {
        if (connect()) {
            when (action) {
                PlayerAction.PLAY -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayPath, null)
                }

                PlayerAction.PAUSE -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPausePath, null)
                }

                PlayerAction.PREVIOUS -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPreviousPath, null)
                }

                PlayerAction.NEXT -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaNextPath, null)
                }

                PlayerAction.VOL_UP -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeUpPath, null)
                }

                PlayerAction.VOL_DOWN -> {
                    sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaVolumeDownPath, null)
                }
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
                withTimeoutOrNull(5000) {
                    ImageUtils.bitmapFromAssetStream(Wearable.getDataClient(context), it)
                }
            } catch (e: Exception) {
                null
            }
        }

        tileModel.setPlayerState(title, artist, artBitmap, playbackState)

        requestTileUpdate(context)
    }

    fun updatePlayerState() {
        scope.launch(Dispatchers.IO) {
            updatePlayerStateAsync()
        }
    }

    suspend fun updatePlayerStateAsync() {
        try {
            val buff = Wearable.getDataClient(context)
                .getDataItems(
                    WearableHelper.getWearDataUri(
                        "*",
                        MediaHelper.MediaPlayerStatePath
                    )
                )
                .await()

            val item = buff.findLast { it.uri.path == MediaHelper.MediaPlayerStatePath }

            if (item != null) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                updatePlayerState(dataMap)
            }

            buff.release()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    suspend fun requestPlayerActionAsync(action: PlayerAction): Boolean =
        suspendCancellableCoroutine { continuation ->
            val listener = when (action) {
                PlayerAction.VOL_UP, PlayerAction.VOL_DOWN -> {
                    object : MessageClient.OnMessageReceivedListener {
                        override fun onMessageReceived(event: MessageEvent) {
                            if (event.path == WearableHelper.AudioStatusPath || event.path == MediaHelper.MediaVolumeStatusPath) {
                                this@MediaPlayerTileMessenger.onMessageReceived(event)
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                    Wearable.getMessageClient(context)
                                        .removeListener(this)
                                }
                            }
                        }
                    }
                }

                else -> {
                    object : DataClient.OnDataChangedListener {
                        override fun onDataChanged(buffer: DataEventBuffer) {
                            val event =
                                buffer.findLast { it.dataItem.uri.path == MediaHelper.MediaPlayerStatePath }

                            if (event != null) {
                                processDataEvent(event)
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                    Wearable.getDataClient(context)
                                        .removeListener(this)
                                }
                            }
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                if (listener is MessageClient.OnMessageReceivedListener) {
                    Wearable.getMessageClient(context)
                        .removeListener(listener)
                } else if (listener is DataClient.OnDataChangedListener) {
                    Wearable.getDataClient(context)
                        .removeListener(listener)
                }
            }

            scope.launch {
                if (listener is MessageClient.OnMessageReceivedListener) {
                    Wearable.getMessageClient(context)
                        .addListener(
                            listener,
                            WearableHelper.getWearDataUri("*", MediaHelper.MediaVolumeStatusPath),
                            DataClient.FILTER_LITERAL
                        )
                        .await()
                } else if (listener is DataClient.OnDataChangedListener) {
                    Wearable.getDataClient(context)
                        .addListener(
                            listener,
                            WearableHelper.getWearDataUri("*", MediaHelper.MediaPlayerStatePath),
                            DataClient.FILTER_LITERAL
                        )
                        .await()
                }

                requestPlayerAction(action)
            }
        }

    suspend fun checkConnectionStatus(refreshTile: Boolean = false) {
        val connectedNodes = getConnectedNodes()
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        mPhoneNodeWithApp?.let { node ->
            if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                tileModel.setConnectionStatus(WearConnectionStatus.CONNECTED)
            } else {
                try {
                    sendPing(node.id)
                    tileModel.setConnectionStatus(
                        WearConnectionStatus.CONNECTED
                    )
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        tileModel.setConnectionStatus(
                            WearConnectionStatus.DISCONNECTED
                        )
                    } else {
                        Logger.writeLine(Log.ERROR, e)
                    }
                }
            }
        } ?: run {
            /*
             * If a device is disconnected from the wear network, capable nodes are empty
             *
             * No capable nodes can mean the app is not installed on the remote device or the
             * device is disconnected.
             *
             * Verify if we're connected to any nodes; if not, we're truly disconnected
             */
            tileModel.setConnectionStatus(
                if (connectedNodes.isEmpty()) {
                    WearConnectionStatus.DISCONNECTED
                } else {
                    WearConnectionStatus.APPNOTINSTALLED
                }
            )
        }

        if (refreshTile) {
            requestTileUpdate(context)
        }
    }

    private suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(context)
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

    suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    suspend fun getConnectedNodes(): List<Node> {
        try {
            return Wearable.getNodeClient(context)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }

        return emptyList()
    }

    private suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(context)
                .sendMessage(nodeID, path, data)
                .await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    tileModel.setConnectionStatus(
                        WearConnectionStatus.DISCONNECTED
                    )

                    requestTileUpdate(context)
                    return
                }
            }

            Logger.writeLine(Log.ERROR, e)
        }
    }

    @Throws(ApiException::class)
    suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(context)
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