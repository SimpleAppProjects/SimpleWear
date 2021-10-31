package com.thewizrd.wearsettings.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.Settings as SettingsHelper

object LocationAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is MultiChoiceAction) {
            val state = LocationState.valueOf(action.choice)

            return if (checkSecureSettingsPermission(context)) {
                setLocationState(context, state)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                SecureSettingsAction.putSetting(
                    Settings.Secure.LOCATION_MODE,
                    state.toSecureSettingsInt().toString()
                )
            } else {
                ActionStatus.REMOTE_PERMISSION_DENIED
            }
        } else if (action is ToggleAction) {
            return if (checkSecureSettingsPermission(context)) {
                setLocationEnabled(context, action.isEnabled)
            } else if (SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()) {
                SecureSettingsAction.putSetting(
                    Settings.Secure.LOCATION_MODE,
                    if (action.isEnabled) {
                        Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                    } else {
                        Settings.Secure.LOCATION_MODE_OFF
                    }.toString()
                )
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
}