package com.thewizrd.simplewear

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.wearable.phone.PhoneDeviceType
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ExecutionException

abstract class WearableListenerActivity : AppCompatActivity(), OnMessageReceivedListener, OnCapabilityChangedListener {
    companion object {
        // Actions
        const val ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE"
        const val ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING"
        const val ACTION_UPDATECONNECTIONSTATUS = "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS"
        const val ACTION_CHANGED = "SimpleWear.Droid.Wear.action.ACTION_CHANGED"

        // Extras
        /**
         * Extra contains success flag for open on phone action.
         *
         * @see .ACTION_OPENONPHONE
         */
        const val EXTRA_SUCCESS = "SimpleWear.Droid.Wear.extra.SUCCESS"

        /**
         * Extra contains flag for whether or not to show the animation for the open on phone action.
         *
         * @see .ACTION_OPENONPHONE
         */
        const val EXTRA_SHOWANIMATION = "SimpleWear.Droid.Wear.extra.SHOW_ANIMATION"

        /**
         * Extra contains Action type to be changed for ValueActionActivity
         *
         * @see Actions
         *
         * @see ValueActionActivity
         */
        const val EXTRA_ACTION = "SimpleWear.Droid.Wear.extra.ACTION"

        /**
         * Extra contains Action data (serialized class in JSON) to be passed to BroadcastReceiver or Activity
         *
         * @see Action
         *
         * @see WearableListenerActivity
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
         *
         * @see WearableListenerActivity
         */
        const val EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS"

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
    }

    @Volatile
    protected var mPhoneNodeWithApp: Node? = null
    protected var mConnectionStatus = WearConnectionStatus.DISCONNECTED
    protected lateinit var mMainHandler: Handler

    protected abstract val broadcastReceiver: BroadcastReceiver
    protected abstract val intentFilter: IntentFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mMainHandler = Handler(Looper.getMainLooper())
    }

    override fun onResume() {
        super.onResume()

        Wearable.getCapabilityClient(this).addListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(this).addListener(this)

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(broadcastReceiver)
        Wearable.getCapabilityClient(this).removeListener(this, WearableHelper.CAPABILITY_PHONE_APP)
        Wearable.getMessageClient(this).removeListener(this)
        super.onPause()
    }

    protected fun openAppOnPhone(showAnimation: Boolean = true) {
        lifecycleScope.launch {
            connect()

            if (mPhoneNodeWithApp == null) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@WearableListenerActivity, "Device is not connected or app is not installed on device...", Toast.LENGTH_SHORT).show()
                }

                val deviceType = PhoneDeviceType.getPhoneDeviceType(this@WearableListenerActivity)
                when (deviceType) {
                    PhoneDeviceType.DEVICE_TYPE_ANDROID -> {
                        LocalBroadcastManager.getInstance(this@WearableListenerActivity).sendBroadcast(
                                Intent(ACTION_SHOWSTORELISTING))
                    }
                    PhoneDeviceType.DEVICE_TYPE_IOS -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@WearableListenerActivity, "Connected device is not supported", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@WearableListenerActivity, "Connected device is not supported", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Send message to device to start activity
                val result = Wearable.getMessageClient(this@WearableListenerActivity)
                        .sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.StartActivityPath, ByteArray(0))
                        .await()

                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(Intent(ACTION_OPENONPHONE)
                                .putExtra(EXTRA_SUCCESS, result != -1)
                                .putExtra(EXTRA_SHOWANIMATION, showAnimation))
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        lifecycleScope.launch {
            if (messageEvent.path.contains(WearableHelper.WifiPath)) {
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

                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(Intent(WearableHelper.ActionsPath)
                                .putExtra(EXTRA_ACTIONDATA,
                                        serializer(ToggleAction(Actions.WIFI, enabled), Action::class.java)))
            } else if (messageEvent.path.contains(WearableHelper.BluetoothPath)) {
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

                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(Intent(WearableHelper.ActionsPath)
                                .putExtra(EXTRA_ACTIONDATA,
                                        serializer(ToggleAction(Actions.BLUETOOTH, enabled), Action::class.java)))
            } else if (messageEvent.path == WearableHelper.BatteryPath) {
                val data = messageEvent.data
                val jsonData: String = data.bytesToString()
                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(Intent(WearableHelper.BatteryPath)
                                .putExtra(EXTRA_STATUS, jsonData))
            } else if (messageEvent.path == WearableHelper.AppStatePath) {
                val appState: AppState = App.instance!!.appState
                sendMessage(messageEvent.sourceNodeId, messageEvent.path, appState.name.stringToBytes())
            } else if (messageEvent.path == WearableHelper.ActionsPath) {
                val data = messageEvent.data
                val jsonData: String = data.bytesToString()
                LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                        .sendBroadcast(Intent(WearableHelper.ActionsPath)
                                .putExtra(EXTRA_ACTIONDATA, jsonData))
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        lifecycleScope.launch {
            mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)

            if (mPhoneNodeWithApp == null) {
                mConnectionStatus = WearConnectionStatus.DISCONNECTED
            } else {
                if (mPhoneNodeWithApp!!.isNearby) {
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } else {
                    try {
                        sendPing(mPhoneNodeWithApp!!.id)
                        mConnectionStatus = WearConnectionStatus.CONNECTED
                    } catch (e: ApiException) {
                        if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                            mConnectionStatus = WearConnectionStatus.DISCONNECTED
                        }
                    }
                }
            }

            LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                    .sendBroadcast(Intent(ACTION_UPDATECONNECTIONSTATUS)
                            .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value))
        }
    }

    protected suspend fun updateConnectionStatus() {
        checkConnectionStatus()

        LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                .sendBroadcast(Intent(ACTION_UPDATECONNECTIONSTATUS)
                        .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value))
    }

    protected suspend fun checkConnectionStatus() {
        mPhoneNodeWithApp = checkIfPhoneHasApp()

        if (mPhoneNodeWithApp == null) {
            mConnectionStatus = WearConnectionStatus.DISCONNECTED
        } else {
            if (mPhoneNodeWithApp!!.isNearby) {
                mConnectionStatus = WearConnectionStatus.CONNECTED
            } else {
                try {
                    sendPing(mPhoneNodeWithApp!!.id)
                    mConnectionStatus = WearConnectionStatus.CONNECTED
                } catch (e: ApiException) {
                    if (e.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                        mConnectionStatus = WearConnectionStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    protected suspend fun checkIfPhoneHasApp(): Node? {
        var node: Node? = null

        try {
            val capabilityInfo = Wearable.getCapabilityClient(this@WearableListenerActivity)
                    .getCapability(WearableHelper.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                    .await()
            node = pickBestNodeId(capabilityInfo.nodes)
        } catch (e: ExecutionException) {
            writeLine(Log.ERROR, e)
        } catch (e: InterruptedException) {
            writeLine(Log.ERROR, e)
        }

        return node
    }

    protected suspend fun connect(): Boolean {
        if (mPhoneNodeWithApp == null)
            mPhoneNodeWithApp = checkIfPhoneHasApp()

        return mPhoneNodeWithApp != null
    }

    protected fun requestUpdate() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.UpdatePath, null)
            }
        }
    }

    protected fun requestAction(action: Action?) {
        requestAction(serializer(action, Action::class.java))
    }

    protected fun requestAction(actionJSONString: String?) {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.ActionsPath, actionJSONString?.stringToBytes())
            }
        }
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(this@WearableListenerActivity)
                    .sendMessage(nodeID, path, data).await()
        } catch (ex: ExecutionException) {
            if (ex.cause is ApiException) {
                val apiEx = ex.cause as ApiException?
                if (apiEx!!.statusCode == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED

                    LocalBroadcastManager.getInstance(this@WearableListenerActivity)
                            .sendBroadcast(Intent(ACTION_UPDATECONNECTIONSTATUS)
                                    .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.value))
                }
            }

            writeLine(Log.ERROR, ex)
        } catch (e: Exception) {
            writeLine(Log.ERROR, e)
        }
    }

    @Throws(ApiException::class)
    private suspend fun sendPing(nodeID: String) {
        try {
            Wearable.getMessageClient(this@WearableListenerActivity)
                    .sendMessage(nodeID, WearableHelper.PingPath, null).await()
        } catch (ex: ExecutionException) {
            if (ex.cause is ApiException) {
                throw ex.cause as ApiException
            }
            writeLine(Log.ERROR, ex)
        } catch (e: Exception) {
            writeLine(Log.ERROR, e)
        }
    }
}