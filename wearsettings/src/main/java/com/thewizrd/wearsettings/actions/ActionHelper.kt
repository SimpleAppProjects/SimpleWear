package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions

fun checkSecureSettingsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED
}

object ActionHelper {
    fun performAction(context: Context, action: Action): ActionStatus {
        return when (action.actionType) {
            Actions.WIFI -> {
                WifiAction.executeAction(context, action)
            }
            Actions.LOCATION -> {
                LocationAction.executeAction(context, action)
            }
            Actions.MOBILEDATA -> {
                MobileDataAction.executeAction(context, action)
            }
            else -> ActionStatus.FAILURE
        }
    }
}