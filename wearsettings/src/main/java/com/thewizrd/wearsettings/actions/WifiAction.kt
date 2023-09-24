package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.IWifiManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.Settings
import com.thewizrd.wearsettings.root.RootHelper
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object WifiAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            val status = setWifiEnabled(context, action.isEnabled)
            return if (status != ActionStatus.SUCCESS) {
                // Note: could have failed due to Airplane mode restriction
                // Try with root
                if (Shizuku.pingBinder()) {
                    setWifiEnabledShizuku(context, action.isEnabled)
                } else if (Settings.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                    setWifiEnabledRoot(action.isEnabled)
                } else {
                    status
                }
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
                if (wifiMan.setWifiEnabled(enable))
                    ActionStatus.SUCCESS
                else
                    ActionStatus.REMOTE_FAILURE
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

    private fun setWifiEnabledShizuku(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            val wifiMgr = SystemServiceHelper.getSystemService(Context.WIFI_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IWifiManager.Stub::asInterface)

            val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                wifiMgr.setWifiEnabled("com.android.shell", enable)
            } else {
                wifiMgr.setWifiEnabled(enable)
            }

            if (ret) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}