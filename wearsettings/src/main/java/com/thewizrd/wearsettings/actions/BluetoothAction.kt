package com.thewizrd.wearsettings.actions

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.PermissionChecker
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.Settings
import com.thewizrd.wearsettings.root.RootHelper
import com.topjohnwu.superuser.Shell

object BluetoothAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            val status = setBTEnabled(context, action.isEnabled)
            return if (status != ActionStatus.SUCCESS && Settings.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setBTEnabledRoot(action.isEnabled)
            } else {
                status
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun isBluetoothPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun setBTEnabled(context: Context, enable: Boolean): ActionStatus {
        if (isBluetoothPermissionGranted(context)) {
            return try {
                val btMan =
                    context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = btMan.adapter
                val success = if (enable) adapter.enable() else adapter.disable()
                if (success) ActionStatus.SUCCESS else ActionStatus.REMOTE_FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.REMOTE_FAILURE
            }
        }
        return ActionStatus.REMOTE_PERMISSION_DENIED
    }

    private fun setBTEnabledRoot(enable: Boolean): ActionStatus {
        val arg = if (enable) "enable" else "disable"

        val result = Shell.su("svc bluetooth $arg").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }
}