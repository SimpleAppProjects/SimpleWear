package com.thewizrd.simplewear.wearable

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.LaunchActivity

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableDataListenerService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearableHelper.StartActivityPath) {
            val startIntent = Intent(this, LaunchActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            this.startActivity(startIntent)
        } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
            this.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30))

            sendMessage(messageEvent.sourceNodeId, messageEvent.path, Build.MODEL.stringToBytes())
        }
    }

    protected fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Tasks.await(Wearable.getMessageClient(this@WearableDataListenerService).sendMessage(nodeID, path, data))
        } catch (e: Exception) {
            writeLine(Log.ERROR, e)
        }
    }
}