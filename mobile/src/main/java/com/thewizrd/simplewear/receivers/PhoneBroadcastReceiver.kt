package com.thewizrd.simplewear.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.CallControllerService
import com.thewizrd.simplewear.services.TorchService
import com.thewizrd.simplewear.wearable.WearableWorker

class PhoneBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PhoneBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
                WearableWorker.sendStatusUpdate(context)
                if (Settings.isBridgeMediaEnabled()) {
                    MediaControllerService.enqueueWork(
                        context,
                        Intent(context, MediaControllerService::class.java)
                            .setAction(MediaControllerService.ACTION_CONNECTCONTROLLER)
                            .putExtra(MediaControllerService.EXTRA_SOFTLAUNCH, true)
                    )
                }
                if (Settings.isBridgeCallsEnabled()) {
                    CallControllerService.enqueueWork(
                        context,
                        Intent(context, CallControllerService::class.java)
                            .setAction(CallControllerService.ACTION_CONNECTCONTROLLER)
                    )
                }
            }
            TorchService.ACTION_END_LIGHT -> {
                TorchService.enqueueWork(
                    context, Intent(context, TorchService::class.java)
                        .setAction(TorchService.ACTION_END_LIGHT)
                )
            }
            TorchService.ACTION_START_LIGHT -> {
                TorchService.enqueueWork(
                    context, Intent(context, TorchService::class.java)
                        .setAction(TorchService.ACTION_START_LIGHT)
                )
            }
            else -> {
                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.action)
            }
        }
    }
}