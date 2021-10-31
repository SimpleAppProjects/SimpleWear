package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object MobileDataAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (checkSecureSettingsPermission(context)) {
                setMobileDataEnabled(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                GlobalSettingsAction.putSetting(
                    "mobile_data",
                    if (action.isEnabled) {
                        1
                    } else {
                        0
                    }.toString()
                )
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun setMobileDataEnabled(context: Context, enabled: Boolean): ActionStatus {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val success = Settings.Global.putInt(
                context.contentResolver, "mobile_data",
                if (enabled) 1 else 0
            )
            if (success) {
                ActionStatus.SUCCESS
            } else {
                ActionStatus.REMOTE_FAILURE
            }
        } else {
            ActionStatus.REMOTE_PERMISSION_DENIED
        }
    }
}