package com.thewizrd.simplewear

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.services.TorchService
import com.thewizrd.simplewear.wearable.WearableWorker

class PhoneBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PhoneBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when {
            Intent.ACTION_MY_PACKAGE_REPLACED == intent.action -> {
                WearableWorker.sendStatusUpdate(context)
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION == intent.action -> {
                WearableWorker.sendStatusUpdate(context, WearableWorker.ACTION_SENDWIFIUPDATE,
                        intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN))
            }
            BluetoothAdapter.ACTION_STATE_CHANGED == intent.action -> {
                WearableWorker.sendStatusUpdate(context, WearableWorker.ACTION_SENDBTUPDATE,
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON))
            }
            TorchService.ACTION_END_LIGHT == intent.action -> {
                TorchService.enqueueWork(context, Intent(context, TorchService::class.java)
                        .setAction(TorchService.ACTION_END_LIGHT))
            }
            TorchService.ACTION_START_LIGHT == intent.action -> {
                TorchService.enqueueWork(context, Intent(context, TorchService::class.java)
                        .setAction(TorchService.ACTION_START_LIGHT))
            }
            else -> {
                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.action)
            }
        }
    }
}