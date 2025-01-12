package com.thewizrd.simplewear.viewmodels

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.utils.ErrorMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

abstract class WearableListenerViewModel(private val app: Application) : AndroidViewModel(app),
    OnMessageReceivedListener, OnCapabilityChangedListener {
    protected val appContext: Context
        get() = app.applicationContext

    @SuppressLint("StaticFieldLeak")
    protected var activityContext: Activity? = null

    @Volatile
    protected var mPhoneNodeWithApp: Node? = null
    private var mConnectionStatus = WearConnectionStatus.CONNECTING

    protected val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(appContext)

    protected val _eventsFlow = MutableSharedFlow<WearableEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventFlow: SharedFlow<WearableEvent> = _eventsFlow

    protected val _errorMessagesFlow = MutableSharedFlow<ErrorMessage>(replay = 0)
    val errorMessagesFlow: SharedFlow<ErrorMessage> = _errorMessagesFlow

    init {
        Wearable.getCapabilityClient(appContext)
            .addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(appContext).addListener(this)
    }

    fun initActivityContext(activity: Activity) {
        activityContext = activity
    }

    override fun onCleared() {
        super.onCleared()
        Wearable.getCapabilityClient(appContext)
            .removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(appContext).removeListener(this)
        activityContext = null
    }

    fun openAppOnPhone(activity: Activity, showAnimation: Boolean = true) {
        viewModelScope.launch {
            connect()

            if (mPhoneNodeWithApp == null) {
                _errorMessagesFlow.tryEmit(ErrorMessage.String("Device is not connected or app is not installed on device..."))

                when (PhoneTypeHelper.getPhoneDeviceType(appContext)) {
                    PhoneTypeHelper.DEVICE_TYPE_ANDROID -> {
                        openPlayStore(activity, showAnimation)
                    }

                    PhoneTypeHelper.DEVICE_TYPE_IOS -> {
                        _errorMessagesFlow.tryEmit(ErrorMessage.String("Connected device is not supported"))
                    }

                    else -> {
                        _errorMessagesFlow.tryEmit(ErrorMessage.String("Connected device is not supported"))
                    }
                }
            } else {
                // Send message to device to start activity
                val result = sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.StartActivityPath,
                    ByteArray(0)
                )

                if (showAnimation) {
                    activity.showConfirmationOverlay(result != -1)
                }

                _eventsFlow.tryEmit(WearableEvent(ACTION_OPENONPHONE, Bundle().apply {
                    putBoolean(EXTRA_SUCCESS, result != -1)
                    putBoolean(EXTRA_SHOWANIMATION, showAnimation)
                }))
            }
        }
    }

    suspend fun openPlayStore(activity: Activity, showAnimation: Boolean = true) {
        // Open store on remote device
        val intentAndroid = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(WearableHelper.getPlayStoreURI())

        runCatching {
            remoteActivityHelper.startRemoteActivity(intentAndroid)
                .await()

            if (showAnimation) {
                activity.showConfirmationOverlay(true)
            }
        }.onFailure {
            if (it !is CancellationException && showAnimation) {
                activity.showConfirmationOverlay(false)
            }
        }
    }

    suspend fun startRemoteActivity(intent: Intent): Boolean {
        return runCatching {
            remoteActivityHelper.startRemoteActivity(intent).await()
            true
        }.onFailure {
            Logger.writeLine(Log.ERROR, it, "Error starting remote activity")
        }.getOrDefault(false)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        viewModelScope.launch {
            when {
                messageEvent.path.contains(WearableHelper.WifiPath) -> {
                    val data = messageEvent.data
                    val wifiStatus = data[0].toInt()
                    var enabled = false
                    when (wifiStatus) {
                        WifiManager.WIFI_STATE_DISABLING,
                        WifiManager.WIFI_STATE_DISABLED,
                        WifiManager.WIFI_STATE_UNKNOWN -> {
                            enabled = false
                        }

                        WifiManager.WIFI_STATE_ENABLING,
                        WifiManager.WIFI_STATE_ENABLED -> {
                            enabled = true
                        }
                    }

                    _eventsFlow.tryEmit(WearableEvent(WearableHelper.ActionsPath, Bundle().apply {
                        putString(
                            EXTRA_ACTIONDATA,
                            JSONParser.serializer(
                                ToggleAction(Actions.WIFI, enabled),
                                Action::class.java
                            )
                        )
                    }))
                }

                messageEvent.path.contains(WearableHelper.BluetoothPath) -> {
                    val data = messageEvent.data
                    val bt_status = data[0].toInt()
                    var enabled = false

                    when (bt_status) {
                        BluetoothAdapter.STATE_OFF,
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            enabled = false
                        }

                        BluetoothAdapter.STATE_ON,
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            enabled = true
                        }
                    }

                    _eventsFlow.tryEmit(WearableEvent(WearableHelper.ActionsPath, Bundle().apply {
                        putString(
                            EXTRA_ACTIONDATA,
                            JSONParser.serializer(
                                ToggleAction(Actions.BLUETOOTH, enabled),
                                Action::class.java
                            )
                        )
                    }))
                }

                messageEvent.path == WearableHelper.BatteryPath -> {
                    val data = messageEvent.data
                    val jsonData: String = data.bytesToString()

                    _eventsFlow.tryEmit(WearableEvent(WearableHelper.BatteryPath, Bundle().apply {
                        putString(EXTRA_STATUS, jsonData)
                    }))
                }

                messageEvent.path == WearableHelper.AppStatePath -> {
                    val appState = appLib.appState
                    sendMessage(
                        messageEvent.sourceNodeId,
                        messageEvent.path,
                        appState.name.stringToBytes()
                    )
                }

                messageEvent.path == WearableHelper.ActionsPath -> {
                    val data = messageEvent.data
                    val jsonData: String = data.bytesToString()

                    _eventsFlow.tryEmit(WearableEvent(WearableHelper.ActionsPath, Bundle().apply {
                        putString(EXTRA_ACTIONDATA, jsonData)
                    }))
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        viewModelScope.launch {
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
                mConnectionStatus = if (connectedNodes.isEmpty()) {
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

            _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
                putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
            }))
        }
    }

    protected suspend fun updateConnectionStatus() {
        checkConnectionStatus()

        _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
            putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
        }))
    }

    protected suspend fun checkConnectionStatus() {
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
            mConnectionStatus = if (connectedNodes.isEmpty()) {
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
    }

    suspend fun getConnectionStatus(): WearConnectionStatus {
        checkConnectionStatus()
        return mConnectionStatus
    }

    protected suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(appContext)
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

    protected suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    fun requestUpdate() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.UpdatePath, null)
            }
        }
    }

    protected fun requestAction(action: Action?) {
        requestAction(JSONParser.serializer(action, Action::class.java))
    }

    protected fun requestAction(actionJSONString: String?) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.ActionsPath,
                    actionJSONString?.stringToBytes()
                )
            }
        }
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
     */
    protected fun pickBestNodeId(nodes: Collection<Node>): Node? {
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
            return Wearable.getNodeClient(appContext)
                .connectedNodes
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }

        return emptyList()
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?): Int? {
        try {
            return Wearable.getMessageClient(appContext)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as? ApiException
                if (apiException?.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED

                    _eventsFlow.tryEmit(
                        WearableEvent(
                            ACTION_UPDATECONNECTIONSTATUS,
                            Bundle().apply {
                                putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
                            })
                    )
                }
            }

            Logger.writeLine(Log.ERROR, e)
        }

        return -1
    }

    @Throws(ApiException::class)
    protected suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(appContext)
                .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (e: Exception) {
            if (e is ApiException || e.cause is ApiException) {
                val apiException = e.cause as? ApiException ?: e as ApiException
                throw apiException
            }
            Logger.writeLine(Log.ERROR, e)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected fun setConnectionStatus(status: WearConnectionStatus) {
        mConnectionStatus = status

        _eventsFlow.tryEmit(WearableEvent(ACTION_UPDATECONNECTIONSTATUS, Bundle().apply {
            putInt(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value)
        }))
    }

    companion object {
        // Actions
        const val ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE"
        const val ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING"
        const val ACTION_UPDATECONNECTIONSTATUS =
            "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS"
        const val ACTION_CHANGED = "SimpleWear.Droid.Wear.action.ACTION_CHANGED"

        // Extras
        /**
         * Extra contains success flag for open on phone action.
         *
         * @see ACTION_OPENONPHONE
         */
        const val EXTRA_SUCCESS = "SimpleWear.Droid.Wear.extra.SUCCESS"

        /**
         * Extra contains flag for whether or not to show the animation for the open on phone action.
         *
         * @see ACTION_OPENONPHONE
         */
        const val EXTRA_SHOWANIMATION = "SimpleWear.Droid.Wear.extra.SHOW_ANIMATION"

        /**
         * Extra contains Action type to be changed for ValueActionActivity
         *
         * @see Actions
         */
        const val EXTRA_ACTION = "SimpleWear.Droid.Wear.extra.ACTION"

        /**
         * Extra contains Action data (serialized class in JSON) to be passed to BroadcastReceiver or Activity
         *
         * @see Action
         */
        const val EXTRA_ACTIONDATA = "SimpleWear.Droid.Wear.extra.ACTION_DATA"

        /**
         * Extra contains Status data (serialized class in JSON) for complex Status types
         *
         * @see BatteryStatus
         */
        const val EXTRA_STATUS = "SimpleWear.Droid.Wear.extra.STATUS"

        /**
         * Extra contains connection status for WearOS device and connected phone
         *
         * @see WearConnectionStatus
         */
        const val EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS"
    }
}