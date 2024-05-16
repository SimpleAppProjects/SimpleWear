package com.thewizrd.simplewear.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.Logger

class WearBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WearBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AnalyticsLogger.logEvent("App_Upgrading", Bundle().apply {
                    putLong("VersionCode", WearableHelper.getAppVersionCode())
                })
            }

            else -> {
                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.action)
            }
        }
    }
}