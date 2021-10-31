package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.Settings
import com.thewizrd.wearsettings.root.RootHelper
import com.topjohnwu.superuser.Shell

object WifiAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            val status = setWifiEnabled(context, action.isEnabled)
            return if (status != ActionStatus.SUCCESS && Settings.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setWifiEnabledRoot(action.isEnabled)
            } else {
                status
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun setWifiEnabled(context: Context, enable: Boolean): ActionStatus {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return try {
                val wifiMan =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (wifiMan.setWifiEnabled(enable)) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.REMOTE_FAILURE
            }
        }
        return ActionStatus.REMOTE_PERMISSION_DENIED
    }

    private fun setWifiEnabledRoot(enable: Boolean): ActionStatus {
        val arg = if (enable) "enable" else "disable"

        val result = Shell.su("svc wifi $arg").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }
}