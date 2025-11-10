package com.thewizrd.wearsettings.actions

import android.content.Context
import android.nfc.INfcAdapter
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.Settings
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import com.thewizrd.wearsettings.shizuku.grantSecureSettingsPermission
import com.topjohnwu.superuser.Shell
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object NfcAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (ShizukuUtils.isRunning(context)) {
                context.grantSecureSettingsPermission()
                setNfcEnabledShizuku(context, action.isEnabled)
            } else if (Settings.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setNfcEnabledRoot(action.isEnabled)
            } else if (checkSecureSettingsPermission(context)) {
                setNfcEnabled(context, action.isEnabled)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun setNfcEnabled(context: Context, enable: Boolean): ActionStatus {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter?.let {
            try {
                val success = if (enable) it.enable() else it.disable()
                if (success) {
                    ActionStatus.SUCCESS
                } else {
                    ActionStatus.REMOTE_FAILURE
                }
            } catch (e: SecurityException) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        } ?: ActionStatus.REMOTE_FAILURE
    }

    private fun setNfcEnabledRoot(enable: Boolean): ActionStatus {
        val arg = if (enable) "enable" else "disable"

        val result = Shell.su("svc nfc $arg").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun setNfcEnabledShizuku(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            val nfcManager = SystemServiceHelper.getSystemService(Context.NFC_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(INfcAdapter.Stub::asInterface)

            val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                if (enable) nfcManager.enable("com.android.shell") else nfcManager.disable(
                    false,
                    "com.android.shell"
                )
            } else {
                if (enable) nfcManager.enable() else nfcManager.disable(false)
            }

            if (ret) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}