package com.thewizrd.wearsettings.actions

import android.app.INotificationManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import com.topjohnwu.superuser.Shell
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object DoNotDisturbAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is MultiChoiceAction) {
            val state = DNDChoice.valueOf(action.choice)

            return if (ShizukuUtils.isRunning(context)) {
                setDNDStateShizuku(context, state)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setDNDStateRoot(context, state)
            } else if (isNotificationAccessAllowed(context)) {
                setDNDState(context, state)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        } else if (action is ToggleAction) {
            return if (ShizukuUtils.isRunning(context)) {
                setDNDStateShizuku(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setDNDStateRoot(context, action.isEnabled)
            } else if (isNotificationAccessAllowed(context)) {
                setDNDState(context, action.isEnabled)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun isNotificationAccessAllowed(context: Context): Boolean {
        val notMan =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notMan.isNotificationPolicyAccessGranted
    }

    private fun setDNDState(context: Context, enable: Boolean): ActionStatus {
        return setDNDState(context, if (enable) DNDChoice.PRIORITY else DNDChoice.OFF)
    }

    private fun setDNDState(context: Context, state: DNDChoice): ActionStatus {
        return try {
            val notMan =
                context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            when (state) {
                DNDChoice.OFF -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                DNDChoice.PRIORITY -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                DNDChoice.ALARMS -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                DNDChoice.SILENCE -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun setDNDStateRoot(context: Context, enable: Boolean): ActionStatus {
        return setDNDStateRoot(context, if (enable) DNDChoice.PRIORITY else DNDChoice.OFF)
    }

    private fun setDNDStateRoot(context: Context, state: DNDChoice): ActionStatus {
        val dndValue = when (state) {
            DNDChoice.OFF -> "off"
            DNDChoice.PRIORITY -> "priority"
            DNDChoice.ALARMS -> "alarms"
            DNDChoice.SILENCE -> "on"
        }

        val result = Shell.su("cmd notification set_dnd $dndValue").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }

    private fun setDNDStateShizuku(context: Context, enable: Boolean): ActionStatus {
        return setDNDStateShizuku(context, if (enable) DNDChoice.PRIORITY else DNDChoice.OFF)
    }

    private fun setDNDStateShizuku(context: Context, state: DNDChoice): ActionStatus {
        val interruptionFilter = when (state) {
            DNDChoice.OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
            DNDChoice.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            DNDChoice.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            DNDChoice.SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
        }

        return runCatching {
            val notificationMgr = SystemServiceHelper.getSystemService(Context.NOTIFICATION_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(INotificationManager.Stub::asInterface)

            val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                notificationMgr.setInterruptionFilter("com.android.shell", interruptionFilter, true)
                true
            } else if (Build.VERSION.SDK_INT_FULL >= (Build.VERSION_CODES_FULL.UPSIDE_DOWN_CAKE + 3)) {
                runCatching {
                    notificationMgr.setInterruptionFilter("com.android.system", interruptionFilter)
                }.onFailure {
                    if (it is NoSuchMethodError) {
                        // Android 14 - QPR3
                        notificationMgr.setInterruptionFilter(
                            "com.android.shell",
                            interruptionFilter,
                            true
                        )
                    } else {
                        throw it
                    }
                }
                true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationMgr.setInterruptionFilter("com.android.system", interruptionFilter)
                true
            } else {
                false
            }

            if (ret) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            ActionStatus.REMOTE_FAILURE
        }
    }
}