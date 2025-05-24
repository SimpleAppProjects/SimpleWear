package com.thewizrd.wearsettings.actions

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.android.internal.telephony.ITelephony
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import com.topjohnwu.superuser.Shell
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object MobileDataAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return if (ShizukuUtils.isRunning(context)) {
                setMobileDataEnabledShizuku(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setMobileDataEnabledRoot(action.isEnabled)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun setMobileDataEnabledRoot(enable: Boolean): ActionStatus {
        val arg = if (enable) "enable" else "disable"

        val result = Shell.su("svc data $arg").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun setMobileDataEnabledShizuku(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            val telephony = SystemServiceHelper.getSystemService(Context.TELEPHONY_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(ITelephony.Stub::asInterface)

            val activeSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SubscriptionManager.getActiveDataSubscriptionId().takeUnless {
                    it == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                } ?: SubscriptionManager.getDefaultSubscriptionId()
            } else {
                SubscriptionManager.getDefaultSubscriptionId()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                telephony.setDataEnabledForReason(
                    activeSubId,
                    TelephonyManager.DATA_ENABLED_REASON_USER,
                    enable,
                    ""
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephony.setDataEnabledForReason(
                    activeSubId,
                    TelephonyManager.DATA_ENABLED_REASON_USER,
                    enable
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telephony.setUserDataEnabled(activeSubId, enable)
            } else {
                telephony.setDataEnabled(activeSubId, enable)
            }

            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (enable) {
                    telephony.enableDataConnectivity("")
                } else {
                    telephony.disableDataConnectivity("")
                }
            } else {
                if (enable) {
                    telephony.enableDataConnectivity()
                } else {
                    telephony.disableDataConnectivity()
                }
            }
            */

            ActionStatus.SUCCESS
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}