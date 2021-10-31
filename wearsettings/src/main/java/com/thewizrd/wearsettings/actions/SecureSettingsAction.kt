package com.thewizrd.wearsettings.actions

import com.thewizrd.shared_resources.actions.ActionStatus
import com.topjohnwu.superuser.Shell

object SecureSettingsAction {
    fun putSetting(key: String, value: String): ActionStatus {
        if (!Shell.rootAccess()) {
            return ActionStatus.PERMISSION_DENIED
        }

        val result = Shell.su("settings put secure $key $value").exec()

        return if (result.isSuccess) {
            ActionStatus.SUCCESS
        } else {
            ActionStatus.FAILURE
        }
    }
}