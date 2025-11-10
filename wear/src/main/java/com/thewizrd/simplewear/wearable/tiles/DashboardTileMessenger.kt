package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.wearable.tiles.DashboardTileProviderService.Companion.requestTileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class DashboardTileMessenger(
    private val context: Context,
    private val isLegacyTile: Boolean = false
) : CapabilityClient.OnCapabilityChangedListener {
    companion object {
        private const val TAG = "DashboardTileMessenger"
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
    }

    fun unregister() {
        Wearable.getCapabilityClient(context)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)

        scope.cancel()
    }

    suspend fun requestUpdate() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.UpdatePath, null)
        }
    }

    private suspend fun requestAction(action: Action) {
        requestAction(JSONParser.serializer(action, Action::class.java))
    }

    private suspend fun requestAction(actionJSONString: String) {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.ActionsPath,
                actionJSONString.stringToBytes()
            )
        }
    }

    private suspend fun processAction(state: DashboardTileState, action: Actions) {
        when (action) {
            Actions.WIFI -> run {
                val wifiAction = state.getAction(Actions.WIFI) as? ToggleAction

                if (wifiAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.WIFI, !wifiAction.isEnabled))
            }

            Actions.BLUETOOTH -> run {
                val btAction = state.getAction(Actions.BLUETOOTH) as? ToggleAction

                if (btAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.BLUETOOTH, !btAction.isEnabled))
            }

            Actions.LOCKSCREEN -> requestAction(
                state.getAction(Actions.LOCKSCREEN) ?: NormalAction(Actions.LOCKSCREEN)
            )

            Actions.DONOTDISTURB -> run {
                val dndAction = state.getAction(Actions.DONOTDISTURB)

                if (dndAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(
                    if (dndAction is ToggleAction) {
                        ToggleAction(Actions.DONOTDISTURB, !dndAction.isEnabled)
                    } else {
                        MultiChoiceAction(
                            Actions.DONOTDISTURB,
                            (dndAction as MultiChoiceAction).choice + 1
                        )
                    }
                )
            }

            Actions.RINGER -> run {
                val ringerAction = state.getAction(Actions.RINGER) as? MultiChoiceAction

                if (ringerAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(MultiChoiceAction(Actions.RINGER, ringerAction.choice + 1))
            }

            Actions.TORCH -> run {
                val torchAction = state.getAction(Actions.TORCH) as? ToggleAction

                if (torchAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.TORCH, !torchAction.isEnabled))
            }

            Actions.MOBILEDATA -> run {
                val mobileDataAction = state.getAction(Actions.MOBILEDATA) as? ToggleAction

                if (mobileDataAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.MOBILEDATA, !mobileDataAction.isEnabled))
            }

            Actions.LOCATION -> run {
                val locationAction = state.getAction(Actions.LOCATION)

                if (locationAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(
                    if (locationAction is ToggleAction) {
                        ToggleAction(Actions.LOCATION, !locationAction.isEnabled)
                    } else {
                        MultiChoiceAction(
                            Actions.LOCATION,
                            (locationAction as MultiChoiceAction).choice + 1
                        )
                    }
                )
            }

            Actions.HOTSPOT -> run {
                val hotspotAction = state.getAction(Actions.HOTSPOT) as? ToggleAction

                if (hotspotAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.HOTSPOT, !hotspotAction.isEnabled))
            }

            Actions.NFC -> run {
                val nfcAction = state.getAction(Actions.NFC) as? ToggleAction

                if (nfcAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.NFC, !nfcAction.isEnabled))
            }

            else -> {
                // ignore unsupported actions
            }
        }
    }

    suspend fun processActionAsync(state: DashboardTileState, actionType: Actions): Boolean =
        suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                when (actionType) {
                    Actions.WIFI -> {
                        if (event.path == WearableHelper.WifiPath) {
                            if (continuation.isActive) {
                                continuation.resume(true)
                                return@OnMessageReceivedListener
                            }
                        }
                    }

                    Actions.BLUETOOTH -> {
                        if (event.path == WearableHelper.BluetoothPath) {
                            if (continuation.isActive) {
                                continuation.resume(true)
                                return@OnMessageReceivedListener
                            }
                        }
                    }

                    else -> {
                        if (event.path == WearableHelper.ActionsPath) {
                            val jsonData: String = event.data.bytesToString()
                            val action = JSONParser.deserializer(jsonData, Action::class.java)

                            if (action?.actionType == actionType) {
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                    return@OnMessageReceivedListener
                                }
                            }
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(context)
                    .removeListener(listener)
            }

            scope.launch {
                Wearable.getMessageClient(context)
                    .addListener(listener)
                    .await()

                processAction(state, actionType)
            }
        }

    suspend fun requestBatteryStatusAsync(): BatteryStatus? {
        return suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                when (event.path) {
                    WearableHelper.BatteryPath -> {
                        if (continuation.isActive) {
                            val jsonData: String = event.data.bytesToString()
                            val status = JSONParser.deserializer(
                                jsonData,
                                BatteryStatus::class.java
                            )

                            continuation.resume(status)
                            return@OnMessageReceivedListener
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(context)
                    .removeListener(listener)
            }

            scope.launch {
                Wearable.getMessageClient(context)
                    .addListener(
                        listener,
                        WearableHelper.getWearDataUri("*", WearableHelper.BatteryPath),
                        MessageClient.FILTER_LITERAL
                    )
                    .await()

                if (connect()) {
                    sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.BatteryPath, null)
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            val connectedNodes = getConnectedNodes()
            mPhoneNodeWithApp = WearableHelper.pickBestNodeId(capabilityInfo.nodes)
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
            Logger.writeLine(Log.ERROR, e)
        }

        return node
    }

    suspend fun connect(): Boolean {
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
                    _connectionState.update { WearConnectionStatus.DISCONNECTED }

                    if (!isLegacyTile) {
                        requestTileUpdate(context)
                    }
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