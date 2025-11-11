package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
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
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.datastore.media.mediaDataStore
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileProviderService.Companion.requestTileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class MediaPlayerTileMessenger(
    private val context: Context,
    private val isLegacyTile: Boolean = false
) :
    MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    companion object {
        private const val TAG = "MediaPlayerTileMessenger"
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

    private val _connectionState = MutableStateFlow(WearConnectionStatus.DISCONNECTED)
    val connectionState = _connectionState.stateIn(
        scope,
        SharingStarted.Eagerly,
        _connectionState.value
    )

    fun register() {
        Wearable.getCapabilityClient(context)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .addListener(this)
    }

    fun unregister() {
        Wearable.getCapabilityClient(context)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        Wearable.getMessageClient(context)
            .removeListener(this)

        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = messageEvent.data ?: return

        when (messageEvent.path) {
            WearableHelper.AudioStatusPath,
            MediaHelper.MediaVolumeStatusPath -> {
                Logger.debug(TAG, "message received - path: ${messageEvent.path}")

                val status = data.let {
                    JSONParser.deserializer(
                        it.bytesToString(),
                        AudioStreamState::class.java
                    )
                }

                scope.launch {
                    context.mediaDataStore.updateData {
                        it.copy(audioStreamState = status)
                    }
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)

            mPhoneNodeWithApp?.let { node ->
                if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                    _connectionState.update { WearConnectionStatus.CONNECTED }
                } else {
                    try {
                        sendPing(node.id)
                        _connectionState.update { WearConnectionStatus.CONNECTED }
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            _connectionState.update { WearConnectionStatus.DISCONNECTED }
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
                _connectionState.update {
                    if (connectedNodes.isEmpty()) {
                        WearConnectionStatus.DISCONNECTED
                    } else {
                        WearConnectionStatus.APPNOTINSTALLED
                    }
                }
            }

            if (!isLegacyTile) {
                requestTileUpdate(context)
            }
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
            sendMessage(
                mPhoneNodeWithApp!!.id,
                MediaHelper.MediaPlayerConnectPath,
                true.booleanToBytes()
            )

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

    suspend fun requestUpdatePlayerState() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerStatePath, null)
        }
    }

    suspend fun requestPlayerAppInfo() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerAppInfoPath, null)
        }
    }

    suspend fun updatePlayerStateFromRemote() {
        val stateListenerJob = scope.async {
            var complete = false

            val listener = MessageClient.OnMessageReceivedListener { event ->
                if (event.path == MediaHelper.MediaPlayerStatePath) {
                    this@MediaPlayerTileMessenger.onMessageReceived(event)
                    complete = true
                }
            }

            Wearable.getMessageClient(context)
                .addListener(
                    listener,
                    WearableHelper.getWearDataUri("*", MediaHelper.MediaPlayerStatePath),
                    MessageClient.FILTER_LITERAL
                )
                .await()

            while (isActive && !complete) {
                delay(250)
            }

            Wearable.getMessageClient(context).removeListener(listener)
        }

        val artListenerJob = scope.async {
            var complete = false

            val listener = MessageClient.OnMessageReceivedListener { event ->
                if (event.path == MediaHelper.MediaPlayerArtPath) {
                    this@MediaPlayerTileMessenger.onMessageReceived(event)
                    complete = true
                }
            }

            Wearable.getMessageClient(context)
                .addListener(
                    listener,
                    WearableHelper.getWearDataUri("*", MediaHelper.MediaPlayerArtPath),
                    MessageClient.FILTER_LITERAL
                )
                .await()

            while (isActive && !complete) {
                delay(250)
            }

            Wearable.getMessageClient(context).removeListener(listener)
        }

        val updateRequestJob by lazy {
            scope.async {
                requestUpdatePlayerState()
            }
        }

        awaitAll(stateListenerJob, artListenerJob, updateRequestJob)
    }

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
                                    Wearable.getMessageClient(context).removeListener(this)
                                }
                            }
                        }
                    }
                }

                else -> {
                    object : MessageClient.OnMessageReceivedListener {
                        override fun onMessageReceived(event: MessageEvent) {
                            if (event.path == MediaHelper.MediaPlayerStatePath) {
                                this@MediaPlayerTileMessenger.onMessageReceived(event)
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                    Wearable.getMessageClient(context).removeListener(this)
                                }
                            }
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(context).removeListener(listener)
            }

            scope.launch {
                Wearable.getMessageClient(context)
                    .run {
                        when (action) {
                            PlayerAction.VOL_UP, PlayerAction.VOL_DOWN -> {
                                addListener(
                                    listener,
                                    WearableHelper.getWearDataUri(
                                        "*",
                                        MediaHelper.MediaVolumeStatusPath
                                    ),
                                    MessageClient.FILTER_LITERAL
                                )
                            }

                            else -> {
                                addListener(
                                    listener,
                                    WearableHelper.getWearDataUri(
                                        "*",
                                        MediaHelper.MediaPlayerStatePath
                                    ),
                                    MessageClient.FILTER_LITERAL
                                )
                            }
                        }
                    }
                    .await()

                requestPlayerAction(action)
            }
        }

    suspend fun checkConnectionStatus(refreshTile: Boolean = false) {
        val connectedNodes = getConnectedNodes()
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        mPhoneNodeWithApp?.let { node ->
            if (node.isNearby && connectedNodes.any { it.id == node.id }) {
                _connectionState.update { WearConnectionStatus.CONNECTED }
            } else {
                try {
                    sendPing(node.id)
                    _connectionState.update { WearConnectionStatus.CONNECTED }
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        _connectionState.update { WearConnectionStatus.DISCONNECTED }
                    } else {
                        Logger.error(TAG, e)
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
            _connectionState.update {
                if (connectedNodes.isEmpty()) {
                    WearConnectionStatus.DISCONNECTED
                } else {
                    WearConnectionStatus.APPNOTINSTALLED
                }
            }
        }

        if (!isLegacyTile && refreshTile) {
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
            Logger.error(TAG, e)
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
            Logger.error(TAG, e)
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
                    _connectionState.update { WearConnectionStatus.DISCONNECTED }

                    if (!isLegacyTile) {
                        requestTileUpdate(context)
                    }
                    return
                }
            }

            Logger.error(TAG, e)
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
            Logger.error(TAG, e)
        }
    }
}