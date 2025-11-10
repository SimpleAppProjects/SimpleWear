package com.thewizrd.simplewear.wearable.tiles

import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus

data class DashboardTileState(
    val connectionStatus: WearConnectionStatus,
    val batteryStatus: BatteryStatus? = null,
    val actions: Map<Actions, Action?> = emptyMap(),
    val showBatteryStatus: Boolean = true
) {
    fun getAction(actionType: Actions): Action? = actions[actionType]

    val isEmpty = batteryStatus == null || actions.isEmpty()

    fun isActionEnabled(action: Actions): Boolean {
        return when (action) {
            Actions.WIFI, Actions.BLUETOOTH, Actions.MOBILEDATA, Actions.TORCH, Actions.HOTSPOT, Actions.NFC, Actions.BATTERYSAVER -> {
                (getAction(action) as? ToggleAction)?.isEnabled == true
            }

            Actions.LOCATION -> {
                val locationAction = getAction(action)

                val locChoice = if (locationAction is ToggleAction) {
                    if (locationAction.isEnabled) LocationState.HIGH_ACCURACY else LocationState.OFF
                } else if (locationAction is MultiChoiceAction) {
                    LocationState.valueOf(locationAction.choice)
                } else {
                    LocationState.OFF
                }

                locChoice != LocationState.OFF
            }

            Actions.LOCKSCREEN -> true
            Actions.DONOTDISTURB -> {
                val dndAction = getAction(action)

                val dndChoice = if (dndAction is ToggleAction) {
                    if (dndAction.isEnabled) DNDChoice.PRIORITY else DNDChoice.OFF
                } else if (dndAction is MultiChoiceAction) {
                    DNDChoice.valueOf(dndAction.choice)
                } else {
                    DNDChoice.OFF
                }

                dndChoice != DNDChoice.OFF
            }

            Actions.RINGER -> {
                val ringerAction = getAction(action) as? MultiChoiceAction
                val ringerChoice = ringerAction?.choice?.let {
                    RingerChoice.valueOf(it)
                } ?: RingerChoice.VIBRATION

                ringerChoice != RingerChoice.SILENT
            }

            else -> false
        }
    }

    fun isNextActionEnabled(action: Actions): Boolean {
        val actionState = getAction(action)

        if (actionState == null) {
            return when (action) {
                // Normal actions
                Actions.LOCKSCREEN -> true
                // others
                else -> false
            }
        } else {
            return when (actionState) {
                is ToggleAction -> {
                    !actionState.isEnabled
                }

                is MultiChoiceAction -> {
                    val newChoice = actionState.choice + 1
                    val ma = MultiChoiceAction(action, newChoice)
                    ma.choice > 0
                }

                is NormalAction -> {
                    true
                }

                else -> {
                    false
                }
            }
        }
    }
}