package com.thewizrd.wearsettings.actions

import android.content.Context
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.ToggleAction

object MobileDataAction {
    fun executeAction(context: Context, action: Action): ActionStatus {
        if (action is ToggleAction) {
            return SecureSettingsAction.putSetting(
                "mobile_data",
                if (action.isEnabled) "1" else "0"
            )
        }

        return ActionStatus.UNKNOWN
    }
}