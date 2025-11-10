package com.thewizrd.wearsettings.actions

import android.content.Context
import android.os.Build
import android.os.IPowerManager
import android.provider.Settings
import android.util.Log
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import com.thewizrd.wearsettings.shizuku.grantSecureSettingsPermission
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object BatterySaverAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (ShizukuUtils.isRunning(context)) {
                context.grantSecureSettingsPermission()
                setBatterySaverEnabledShizuku(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setBatterySaverEnabledRoot(context, action.isEnabled)
            } else if (checkSecureSettingsPermission(context)) {
                setBatterySaverEnabled(context, action.isEnabled)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun setBatterySaverEnabled(context: Context, enable: Boolean): ActionStatus {
        val success = Settings.Global.putInt(
            context.contentResolver, "low_power", if (enable) 1 else 0
        )

        return if (success) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun setBatterySaverEnabledRoot(context: Context, enable: Boolean): ActionStatus {
        return GlobalSettingsAction.putSettingRoot("low_power", if (enable) "1" else "0")
    }

    private fun setBatterySaverEnabledShizuku(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            val powerMgr = SystemServiceHelper.getSystemService(Context.POWER_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IPowerManager.Stub::asInterface)

            val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerMgr.setPowerSaveModeEnabled(enable)
            } else {
                powerMgr.setPowerSaveMode(enable)
            }

            if (ret) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}