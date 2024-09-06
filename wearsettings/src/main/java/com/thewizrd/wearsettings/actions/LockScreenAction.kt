package com.thewizrd.wearsettings.actions

import android.content.Context
import android.os.IPowerManager
import android.os.SystemClock
import android.util.Log
import android.view.IWindowManager
import android.view.KeyEvent
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.root.RootHelper
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object LockScreenAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is NormalAction) {
            return if (Shizuku.pingBinder()) {
                lockScreenShizuku(context)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                lockScreenRoot()
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun lockScreenShizuku(context: Context): ActionStatus {
        return runCatching {
            val powerMgr = SystemServiceHelper.getSystemService(Context.POWER_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IPowerManager.Stub::asInterface)

            val windowMgr = SystemServiceHelper.getSystemService(Context.WINDOW_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IWindowManager.Stub::asInterface)

            powerMgr.goToSleep(SystemClock.uptimeMillis(), 0, 0)
            windowMgr.lockNow(null)
            val ret = true

            if (ret) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun lockScreenRoot(): ActionStatus {
        val result = Shell.su("input keyevent ${KeyEvent.KEYCODE_POWER}").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }
}