package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.ILocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import com.thewizrd.wearsettings.shizuku.grantSecureSettingsPermission
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object LocationAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is MultiChoiceAction) {
            val state = LocationState.valueOf(action.choice)

            return if (checkSecureSettingsPermission(context)) {
                setLocationState(context, state)
            } else if (Shizuku.pingBinder()) {
                context.grantSecureSettingsPermission()
                setLocationState(context, state)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setLocationStateRoot(context, state)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        } else if (action is ToggleAction) {
            return if (Shizuku.pingBinder()) {
                setLocationEnabledShizuku(context, action.isEnabled)
            } else if (checkSecureSettingsPermission(context)) {
                setLocationEnabled(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                setLocationEnabledRoot(context, action.isEnabled)
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        }

        return ActionStatus.UNKNOWN
    }

    private fun LocationState.toSecureSettingsInt(): Int {
        return when (this) {
            LocationState.OFF -> Settings.Secure.LOCATION_MODE_OFF
            LocationState.SENSORS_ONLY -> Settings.Secure.LOCATION_MODE_SENSORS_ONLY
            LocationState.BATTERY_SAVING -> Settings.Secure.LOCATION_MODE_BATTERY_SAVING
            LocationState.HIGH_ACCURACY -> Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
        }
    }

    private fun setLocationState(context: Context, state: LocationState): ActionStatus {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val success = Settings.Secure.putInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                when (state) {
                    LocationState.OFF -> Settings.Secure.LOCATION_MODE_OFF
                    LocationState.SENSORS_ONLY -> Settings.Secure.LOCATION_MODE_SENSORS_ONLY
                    LocationState.BATTERY_SAVING -> Settings.Secure.LOCATION_MODE_BATTERY_SAVING
                    LocationState.HIGH_ACCURACY -> Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                }
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

    private fun setLocationStateRoot(context: Context, state: LocationState): ActionStatus {
        return SecureSettingsAction.putSettingRoot(
            Settings.Secure.LOCATION_MODE,
            state.toSecureSettingsInt().toString()
        )
    }

    private fun setLocationEnabled(context: Context, enable: Boolean): ActionStatus {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val success = Settings.Secure.putInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                if (enable) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF
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

    private fun setLocationEnabledRoot(context: Context, enable: Boolean): ActionStatus {
        return setLocationStateRoot(
            context,
            if (enable) {
                LocationState.HIGH_ACCURACY
            } else {
                LocationState.OFF
            }
        )
    }

    private fun setLocationEnabledShizuku(context: Context, enable: Boolean): ActionStatus {
        return runCatching {
            val locationMgr = SystemServiceHelper.getSystemService(Context.LOCATION_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(ILocationManager.Stub::asInterface)

            val userId = ShizukuUtils.getUserId()

            val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationMgr.setLocationEnabledForUser(enable, userId)
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