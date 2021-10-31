package com.thewizrd.wearsettings.actions

import com.thewizrd.shared_resources.actions.ActionStatus
import com.topjohnwu.superuser.Shell

object SecureSettingsAction {
    fun putSetting(key: String, value: String): ActionStatus {
        if (!Shell.rootAccess()) {
            return ActionStatus.REMOTE_PERMISSION_DENIED
        }

        val result = Shell.su("settings put secure $key $value").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }
}

object GlobalSettingsAction {
    fun putSetting(key: String, value: String): ActionStatus {
        if (!Shell.rootAccess()) {
            return ActionStatus.REMOTE_PERMISSION_DENIED
        }

        val result = Shell.su("settings put global $key $value").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.REMOTE_FAILURE
        }
    }
}