package com.thewizrd.simplewear.wearable.tiles

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import kotlin.coroutines.resume

class DashboardTileMessenger(private val context: Context) :
    CapabilityClient.OnCapabilityChangedListener, MessageClient.OnMessageReceivedListener {
    companion object {
        private const val TAG = "DashboardTileMessenger"
        internal val tileModel by lazy { DashboardTileModel() }
    }

    @Volatile
    private var mPhoneNodeWithApp: Node? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        Timber.tag(TAG).d("message received - path: ${messageEvent.path}")

        when {
            messageEvent.path.contains(WearableHelper.WifiPath) -> {
                val wifiStatus = data[0].toInt()
                var enabled = false

                when (wifiStatus) {
                    WifiManager.WIFI_STATE_DISABLING,
                    WifiManager.WIFI_STATE_DISABLED,
                    WifiManager.WIFI_STATE_UNKNOWN -> enabled = false

                    WifiManager.WIFI_STATE_ENABLING,
                    WifiManager.WIFI_STATE_ENABLED -> enabled = true
                }

                tileModel.setAction(Actions.WIFI, ToggleAction(Actions.WIFI, enabled))
                requestTileUpdate(context)
            }

            messageEvent.path.contains(WearableHelper.BluetoothPath) -> {
                val btStatus = data[0].toInt()
                var enabled = false

                when (btStatus) {
                    BluetoothAdapter.STATE_OFF,
                    BluetoothAdapter.STATE_TURNING_OFF -> enabled = false

                    BluetoothAdapter.STATE_ON,
                    BluetoothAdapter.STATE_TURNING_ON -> enabled = true
                }

                tileModel.setAction(Actions.BLUETOOTH, ToggleAction(Actions.BLUETOOTH, enabled))
                requestTileUpdate(context)
            }

            messageEvent.path == WearableHelper.BatteryPath -> {
                val jsonData: String = data.bytesToString()
                tileModel.updateBatteryStatus(
                    JSONParser.deserializer(
                        jsonData,
                        BatteryStatus::class.java
                    )
                )
                requestTileUpdate(context)
            }

            messageEvent.path == WearableHelper.ActionsPath -> {
                val jsonData: String = data.bytesToString()
                val action = JSONParser.deserializer(jsonData, Action::class.java)

                when (action?.actionType) {
                    Actions.WIFI,
                    Actions.BLUETOOTH,
                    Actions.TORCH,
                    Actions.DONOTDISTURB,
                    Actions.RINGER,
                    Actions.MOBILEDATA,
                    Actions.LOCATION,
                    Actions.LOCKSCREEN,
                    Actions.PHONE,
                    Actions.HOTSPOT -> {
                        tileModel.setAction(action.actionType, action)
                        requestTileUpdate(context)
                    }

                    else -> {
                        // ignore unsupported action
                    }
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

    suspend fun processAction(action: Actions) {
        when (action) {
            Actions.WIFI -> run {
                val wifiAction = tileModel.getAction(Actions.WIFI) as? ToggleAction

                if (wifiAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.WIFI, !wifiAction.isEnabled))
            }

            Actions.BLUETOOTH -> run {
                val btAction = tileModel.getAction(Actions.BLUETOOTH) as? ToggleAction

                if (btAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.BLUETOOTH, !btAction.isEnabled))
            }

            Actions.LOCKSCREEN -> requestAction(
                tileModel.getAction(Actions.LOCKSCREEN) ?: NormalAction(
                    Actions.LOCKSCREEN
                )
            )

            Actions.DONOTDISTURB -> run {
                val dndAction = tileModel.getAction(Actions.DONOTDISTURB)

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
                val ringerAction = tileModel.getAction(Actions.RINGER) as? MultiChoiceAction

                if (ringerAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(MultiChoiceAction(Actions.RINGER, ringerAction.choice + 1))
            }

            Actions.TORCH -> run {
                val torchAction = tileModel.getAction(Actions.TORCH) as? ToggleAction

                if (torchAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.TORCH, !torchAction.isEnabled))
            }

            Actions.MOBILEDATA -> run {
                val mobileDataAction = tileModel.getAction(Actions.MOBILEDATA) as? ToggleAction

                if (mobileDataAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.MOBILEDATA, !mobileDataAction.isEnabled))
            }

            Actions.LOCATION -> run {
                val locationAction = tileModel.getAction(Actions.LOCATION)

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
                val hotspotAction = tileModel.getAction(Actions.HOTSPOT) as? ToggleAction

                if (hotspotAction == null) {
                    requestUpdate()
                    return@run
                }

                requestAction(ToggleAction(Actions.HOTSPOT, !hotspotAction.isEnabled))
            }

            else -> {
                // ignore unsupported actions
            }
        }
    }

    suspend fun processActionAsync(actionType: Actions) {
        suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                when (actionType) {
                    Actions.WIFI -> {
                        if (event.path == WearableHelper.WifiPath) {
                            onMessageReceived(event)
                            if (continuation.isActive) {
                                continuation.resume(true)
                                return@OnMessageReceivedListener
                            }
                        }
                    }

                    Actions.BLUETOOTH -> {
                        if (event.path == WearableHelper.BluetoothPath) {
                            onMessageReceived(event)
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
                                onMessageReceived(event)
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

                processAction(actionType)
            }
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