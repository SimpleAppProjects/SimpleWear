package com.thewizrd.simplewear.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.EXTRA_ACTION_DATA
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.helpers.AlarmStateManager
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.CallControllerService
import com.thewizrd.simplewear.services.TorchService
import com.thewizrd.simplewear.wearable.WearableManager
import com.thewizrd.simplewear.wearable.WearableWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PhoneBroadcastReceiver"
        const val ACTION_PERFORM_TIMED_ACTION = "SimpleWear.action.PERFORM_TIMED_ACTION"
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

                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    AnalyticsLogger.logEvent("App_Upgrading", Bundle().apply {
                        putLong("VersionCode", WearableHelper.getAppVersionCode())
                    })
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
            ACTION_PERFORM_TIMED_ACTION -> {
                if (intent.hasExtra(EXTRA_ACTION_DATA)) {
                    val action = JSONParser.deserializer(
                        intent.getStringExtra(EXTRA_ACTION_DATA),
                        Action::class.java
                    )
                    if (action != null) {
                        Logger.writeLine(
                            Log.INFO,
                            "%s: Performing timed action: %s",
                            TAG,
                            action.actionType.name
                        )

                        appLib.appScope.launch(Dispatchers.Default) {
                            WearableManager(context.applicationContext).run {
                                performAction(null, action)
                            }
                        }

                        // Clear alarm state
                        AlarmStateManager(context.applicationContext).run {
                            clearAlarm(action.actionType)
                        }

                        // Send status update
                        WearableWorker.enqueueAction(
                            context.applicationContext,
                            WearableWorker.ACTION_SENDTIMEDACTIONSUPDATE
                        )
                    }
                }
            }
            else -> {
                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.action)
            }
        }
    }
}