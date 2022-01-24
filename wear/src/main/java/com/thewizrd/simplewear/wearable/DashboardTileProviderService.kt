package com.thewizrd.simplewear.wearable

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.google.android.clockwork.tiles.TileData
import com.google.android.clockwork.tiles.TileProviderService
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.preferences.DashboardTileConfigActivity
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.*

class DashboardTileProviderService : TileProviderService(), OnMessageReceivedListener, OnCapabilityChangedListener {
    companion object {
        private const val TAG = "DashTileProviderService"
        private const val MAX_BUTTONS = DashboardTileConfigActivity.MAX_BUTTONS
        private fun getDefaultActions(): List<Actions> {
            return DashboardTileConfigActivity.DEFAULT_TILES
        }
    }

    private var mInFocus = false
    private var id = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        Timber.tag(TAG).d("$TAG: onTileFocus called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            id = tileId
            mInFocus = true

            // Update tile actions
            tileActions.clear()
            tileActions.addAll(Settings.getDashboardTileConfig() ?: getDefaultActions())

            sendRemoteViews()

            Wearable.getCapabilityClient(applicationContext)
                .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
            Wearable.getMessageClient(applicationContext).addListener(this)

            scope.launch {
                checkConnectionStatus()
                requestUpdate()
            }
        }
    }

    override fun onTileBlur(tileId: Int) {
        super.onTileBlur(tileId)

        Timber.tag(TAG).d("$TAG: onTileBlur called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            mInFocus = false

            Wearable.getCapabilityClient(applicationContext).removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
            Wearable.getMessageClient(applicationContext).removeListener(this)
        }
    }

    private fun sendRemoteViews() {
        Timber.tag(TAG).d("$TAG: sendRemoteViews")
        scope.launch {
            val updateViews = buildUpdate()

            val tileData = TileData.Builder()
                .setRemoteViews(updateViews)
                .build()

            sendUpdate(id, tileData)
        }
    }

    @Volatile
    private var mPhoneNodeWithApp: Node? = null
    private var mConnectionStatus = WearConnectionStatus.DISCONNECTED

    private var battStatus: BatteryStatus? = null
    private val tileActions = mutableListOf<Actions>()
    private val actionMap = mutableMapOf<Actions, Action>().apply {
        // Add NormalActions
        putIfAbsent(Actions.LOCKSCREEN, NormalAction(Actions.LOCKSCREEN))
    }

    private fun buildUpdate(): RemoteViews {
        val views: RemoteViews

        if (mConnectionStatus != WearConnectionStatus.CONNECTED) {
            views = RemoteViews(applicationContext.packageName, R.layout.tile_disconnected)
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
            views.setOnClickPendingIntent(R.id.tile, getTapIntent(applicationContext))
            return views
        }

        views = RemoteViews(applicationContext!!.packageName, R.layout.tile_layout_dashboard)
        views.setOnClickPendingIntent(R.id.tile, getTapIntent(applicationContext))

        if (battStatus != null) {
            val battValue = String.format(
                Locale.ROOT, "%d%%, %s", battStatus!!.batteryLevel,
                if (battStatus!!.isCharging) applicationContext.getString(R.string.batt_state_charging) else applicationContext.getString(
                    R.string.batt_state_discharging
                )
            )
            views.setTextViewText(R.id.batt_stat_text, battValue)
        }

        for (i in 0 until MAX_BUTTONS) {
            val action = tileActions.getOrNull(i)
            updateButton(views, i + 1, action)
        }

        return views
    }

    private fun updateButton(views: RemoteViews, buttonIndex: Int, action: Actions?) {
        val layoutId = when (buttonIndex) {
            1 -> R.id.button_1_layout
            2 -> R.id.button_2_layout
            3 -> R.id.button_3_layout
            4 -> R.id.button_4_layout
            5 -> R.id.button_5_layout
            6 -> R.id.button_6_layout
            else -> return
        }

        val buttonId = when (buttonIndex) {
            1 -> R.id.button_1
            2 -> R.id.button_2
            3 -> R.id.button_3
            4 -> R.id.button_4
            5 -> R.id.button_5
            6 -> R.id.button_6
            else -> return
        }

        if (action != null) {
            actionMap[action]?.let {
                val model = ActionButtonViewModel(it)
                views.setImageViewResource(buttonId, model.drawableID)
                views.setInt(
                    buttonId,
                    "setBackgroundResource",
                    if (model.buttonState != false) R.drawable.round_button_enabled else R.drawable.round_button_disabled
                )
                views.setOnClickPendingIntent(
                    buttonId,
                    getActionClickIntent(applicationContext, it.actionType)
                )
                views.setContentDescription(buttonId, model.actionLabel)
            }
            views.setViewVisibility(layoutId, View.VISIBLE)
        } else {
            views.setViewVisibility(layoutId, View.GONE)
        }
    }

    private fun getTapIntent(context: Context): PendingIntent {
        val onClickIntent = Intent(context.applicationContext, PhoneSyncActivity::class.java)
        return PendingIntent.getActivity(context, 0, onClickIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getActionClickIntent(context: Context, action: Actions): PendingIntent {
        val onClickIntent =
            Intent(context.applicationContext, DashboardTileProviderService::class.java)
                .setAction(action.name)
        return PendingIntent.getService(
            context,
            action.value,
            onClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            when (Actions.valueOf(intent.action!!)) {
                Actions.WIFI -> run {
                    val wifiAction = actionMap[Actions.WIFI] as? ToggleAction

                    if (wifiAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(ToggleAction(Actions.WIFI, !wifiAction.isEnabled))
                }
                Actions.BLUETOOTH -> run {
                    val btAction = actionMap[Actions.BLUETOOTH] as? ToggleAction

                    if (btAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(ToggleAction(Actions.BLUETOOTH, !btAction.isEnabled))
                }
                Actions.LOCKSCREEN -> requestAction(
                    actionMap[Actions.LOCKSCREEN] ?: NormalAction(Actions.LOCKSCREEN)
                )
                Actions.DONOTDISTURB -> run {
                    val dndAction = actionMap[Actions.DONOTDISTURB]

                    if (dndAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(
                        if (dndAction is ToggleAction) {
                            ToggleAction(Actions.DONOTDISTURB, !dndAction.isEnabled)
                        } else {
                            MultiChoiceAction(Actions.DONOTDISTURB, (dndAction as MultiChoiceAction).choice + 1)
                        }
                    )
                }
                Actions.RINGER -> run {
                    val ringerAction = actionMap[Actions.RINGER] as? MultiChoiceAction

                    if (ringerAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(MultiChoiceAction(Actions.RINGER, ringerAction.choice + 1))
                }
                Actions.TORCH -> run {
                    val torchAction = actionMap[Actions.TORCH] as? ToggleAction

                    if (torchAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(ToggleAction(Actions.TORCH, !torchAction.isEnabled))
                }
                Actions.MOBILEDATA -> run {
                    val mobileDataAction = actionMap[Actions.MOBILEDATA] as? ToggleAction

                    if (mobileDataAction == null) {
                        requestUpdate()
                        return@run
                    }

                    requestAction(ToggleAction(Actions.MOBILEDATA, !mobileDataAction.isEnabled))
                }
                Actions.LOCATION -> run {
                    val locationAction = actionMap[Actions.LOCATION]

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
                    val hotspotAction = actionMap[Actions.HOTSPOT] as? ToggleAction

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
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val data = messageEvent.data ?: return

        scope.launch {
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

                    actionMap[Actions.WIFI] = ToggleAction(Actions.WIFI, enabled)
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

                    actionMap[Actions.BLUETOOTH] = ToggleAction(Actions.BLUETOOTH, enabled)
                }
                messageEvent.path == WearableHelper.BatteryPath -> {
                    val jsonData: String = data.bytesToString()
                    battStatus = JSONParser.deserializer(jsonData, BatteryStatus::class.java)
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
                            actionMap[action.actionType] = action
                        }
                        else -> {
                            // ignore unsupported action
                        }
                    }
                }
            }

            // Send update if tile is in focus
            if (mInFocus && !isIdForDummyData(id)) {
                sendRemoteViews()
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
            val capabilityInfo = Wearable.getCapabilityClient(this@DashboardTileProviderService)
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

    private fun requestUpdate() {
        scope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.UpdatePath, null)
            }
        }
    }

    private fun requestAction(action: Action) {
        requestAction(JSONParser.serializer(action, Action::class.java))
    }

    private fun requestAction(actionJSONString: String) {
        scope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.ActionsPath,
                    actionJSONString.stringToBytes()
                )
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
            Wearable.getMessageClient(this@DashboardTileProviderService)
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
            Wearable.getMessageClient(this@DashboardTileProviderService)
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