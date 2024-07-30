package com.thewizrd.simplewear.controls

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavController
import androidx.recyclerview.widget.DiffUtil
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.actions.ValueAction
import com.thewizrd.shared_resources.actions.ValueDirection
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.navigation.Screen
import java.util.Objects

class ActionButtonViewModel(val action: Action) {
    @get:DrawableRes
    @DrawableRes
    var drawableResId: Int
        private set

    val actionType: Actions
        get() = action.actionType

    @StringRes
    var actionLabelResId: Int = 0
        private set

    @StringRes
    var stateLabelResId: Int = 0
        private set

    var buttonState: Boolean? = null
        private set

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ActionButtonViewModel>() {
            override fun areItemsTheSame(
                oldItem: ActionButtonViewModel,
                newItem: ActionButtonViewModel
            ): Boolean {
                return oldItem.actionType == newItem.actionType
            }

            override fun areContentsTheSame(
                oldItem: ActionButtonViewModel,
                newItem: ActionButtonViewModel
            ): Boolean {
                return Objects.equals(oldItem.action, newItem.action)
            }
        }

        fun getViewModelFromAction(action: Actions): ActionButtonViewModel {
            return when (action) {
                Actions.WIFI,
                Actions.BLUETOOTH,
                Actions.MOBILEDATA,
                Actions.TORCH,
                Actions.HOTSPOT ->
                    ActionButtonViewModel(ToggleAction(action, true))
                Actions.LOCATION ->
                    ActionButtonViewModel(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                            MultiChoiceAction(action)
                        else
                            ToggleAction(action, true)
                    )
                Actions.LOCKSCREEN,
                Actions.MUSICPLAYBACK,
                Actions.SLEEPTIMER,
                Actions.APPS,
                Actions.PHONE,
                Actions.GESTURES ->
                    ActionButtonViewModel(NormalAction(action))
                Actions.VOLUME, Actions.BRIGHTNESS ->
                    ActionButtonViewModel(ValueAction(action, ValueDirection.UP))
                Actions.DONOTDISTURB ->
                    ActionButtonViewModel(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                            MultiChoiceAction(action)
                        else
                            ToggleAction(action, true)
                    )
                Actions.RINGER ->
                    ActionButtonViewModel(MultiChoiceAction(action))
            }
        }
    }

    init {
        drawableResId = R.drawable.ic_close_white_24dp
        buttonState = false
        initialize(action)
    }

    private fun initialize(action: Action?) {
        buttonState = true
        drawableResId = R.drawable.ic_close_white_24dp

        if (action is ToggleAction) {
            val tA = action

            if (!tA.isActionSuccessful) {
                // Revert state
                tA.isEnabled = !tA.isEnabled
            }

            buttonState = tA.isEnabled
            updateIconAndLabel()
        } else if (action is MultiChoiceAction) {
            val mA = action

            if (!mA.isActionSuccessful) {
                // Revert state
                mA.choice = mA.choice - 1
            }

            buttonState = mA.choice > 0
            updateIconAndLabel()
        } else if (action != null) {
            updateIconAndLabel()
        } else {
            throw IllegalArgumentException("Action class is invalid!!")
        }
    }

    fun onClick(
        navController: NavController,
        onActionChanged: (Action) -> Unit,
        onActionStatus: (Action) -> Unit
    ) {
        action.isActionSuccessful = true

        if (action is ValueAction) {
            navController.navigate("${Screen.ValueAction.route}/${actionType.value}")
        } else if (action is NormalAction && action.actionType == Actions.MUSICPLAYBACK) {
            navController.navigate(Screen.MediaPlayerList.route)
        } else if (action is NormalAction && action.actionType == Actions.SLEEPTIMER) {
            if (SleepTimerHelper.isSleepTimerInstalled()) {
                SleepTimerHelper.launchSleepTimer()
            } else {
                action.setActionSuccessful(ActionStatus.PERMISSION_DENIED)

                onActionStatus.invoke(action)
            }
        } else if (action is NormalAction && action.actionType == Actions.APPS) {
            navController.navigate(Screen.AppLauncher.route)
        } else if (action is NormalAction && action.actionType == Actions.PHONE) {
            navController.navigate(Screen.CallManager.route)
        } else if (action is NormalAction && action.actionType == Actions.GESTURES) {
            navController.navigate(Screen.GesturesAction.route)
        } else {
            if (action is ToggleAction) {
                val tA = action
                tA.isEnabled = !tA.isEnabled
                buttonState = null
            } else if (action is MultiChoiceAction) {
                val mA = action
                val currentChoice = mA.choice
                val newChoice = currentChoice + 1
                mA.choice = newChoice
                updateIconAndLabel()
            }

            onActionChanged.invoke(action)
        }
    }

    private fun updateIconAndLabel() {
        val tA: ToggleAction
        val mA: MultiChoiceAction

        when (actionType) {
            Actions.WIFI -> {
                tA = action as ToggleAction
                drawableResId =
                    if (tA.isEnabled) R.drawable.ic_network_wifi_white_24dp else R.drawable.ic_signal_wifi_off_white_24dp
                actionLabelResId = R.string.action_wifi
                stateLabelResId = if (tA.isEnabled) R.string.state_on else R.string.state_off
            }
            Actions.BLUETOOTH -> {
                tA = action as ToggleAction
                drawableResId =
                    if (tA.isEnabled) R.drawable.ic_bluetooth_white_24dp else R.drawable.ic_bluetooth_disabled_white_24dp
                actionLabelResId = R.string.action_bt
                stateLabelResId = if (tA.isEnabled) R.string.state_on else R.string.state_off
            }
            Actions.MOBILEDATA -> {
                tA = action as ToggleAction
                drawableResId =
                    if (tA.isEnabled) R.drawable.ic_network_cell_white_24dp else R.drawable.ic_signal_cellular_off_white_24dp
                actionLabelResId = R.string.action_mobiledata
                stateLabelResId = if (tA.isEnabled) R.string.state_on else R.string.state_off
            }
            Actions.LOCATION -> {
                actionLabelResId = R.string.action_location

                val locationState = if (action is ToggleAction) {
                    if (action.isEnabled) LocationState.HIGH_ACCURACY else LocationState.OFF
                } else {
                    mA = action as MultiChoiceAction
                    LocationState.valueOf(mA.choice)
                }
                when (locationState) {
                    LocationState.OFF -> {
                        drawableResId = R.drawable.ic_location_off_white_24dp
                        stateLabelResId = R.string.state_off
                    }
                    LocationState.SENSORS_ONLY -> {
                        drawableResId = R.drawable.ic_baseline_gps_fixed_24dp
                        stateLabelResId = R.string.locationstate_sensorsonly
                    }
                    LocationState.BATTERY_SAVING -> {
                        drawableResId = R.drawable.ic_outline_location_on_24dp
                        stateLabelResId = R.string.locationstate_batterysaving
                    }
                    LocationState.HIGH_ACCURACY -> {
                        drawableResId = R.drawable.ic_location_on_white_24dp
                        stateLabelResId = if (action is ToggleAction) {
                            R.string.state_on
                        } else {
                            R.string.locationstate_highaccuracy
                        }
                    }
                }
            }
            Actions.TORCH -> {
                tA = action as ToggleAction
                drawableResId = R.drawable.ic_lightbulb_outline_white_24dp
                actionLabelResId = R.string.action_torch
                stateLabelResId = if (tA.isEnabled) R.string.state_on else R.string.state_off
            }
            Actions.LOCKSCREEN -> {
                drawableResId = R.drawable.ic_lock_outline_white_24dp
                actionLabelResId = R.string.action_lockscreen
                stateLabelResId = 0
            }
            Actions.VOLUME -> {
                drawableResId = R.drawable.ic_volume_up_white_24dp
                actionLabelResId = R.string.action_volume
                stateLabelResId = 0
            }
            Actions.DONOTDISTURB -> {
                actionLabelResId = R.string.action_dnd

                val dndChoice = if (action is ToggleAction) {
                    if (action.isEnabled) DNDChoice.PRIORITY else DNDChoice.OFF
                } else {
                    mA = action as MultiChoiceAction
                    DNDChoice.valueOf(mA.choice)
                }
                when (dndChoice) {
                    DNDChoice.OFF -> {
                        drawableResId = R.drawable.ic_do_not_disturb_off_white_24dp
                        stateLabelResId = R.string.state_off
                    }
                    DNDChoice.PRIORITY -> {
                        drawableResId = R.drawable.ic_error_white_24dp
                        stateLabelResId = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            R.string.dndstate_priority
                        } else {
                            R.string.state_on
                        }
                    }
                    DNDChoice.ALARMS -> {
                        drawableResId = R.drawable.ic_alarm_white_24dp
                        stateLabelResId = R.string.dndstate_alarms
                    }
                    DNDChoice.SILENCE -> {
                        drawableResId = R.drawable.ic_notifications_off_white_24dp
                        stateLabelResId = R.string.dndstate_silence
                    }
                }
            }
            Actions.RINGER -> {
                mA = action as MultiChoiceAction

                actionLabelResId = R.string.action_ringer

                when (RingerChoice.valueOf(mA.choice)) {
                    RingerChoice.VIBRATION -> {
                        drawableResId = R.drawable.ic_vibration_white_24dp
                        stateLabelResId = R.string.ringerstate_vib
                    }
                    RingerChoice.SOUND -> {
                        drawableResId = R.drawable.ic_notifications_active_white_24dp
                        stateLabelResId = R.string.ringerstate_sound
                    }
                    RingerChoice.SILENT -> {
                        drawableResId = R.drawable.ic_volume_off_white_24dp
                        stateLabelResId = R.string.ringerstate_silent
                    }
                }
            }
            Actions.MUSICPLAYBACK -> {
                drawableResId = R.drawable.ic_play_circle_filled_white_24dp
                actionLabelResId = R.string.action_musicplayback
                stateLabelResId = 0
            }
            Actions.SLEEPTIMER -> {
                drawableResId = R.drawable.ic_sleep_timer
                actionLabelResId = R.string.action_sleeptimer
                stateLabelResId = 0
            }
            Actions.APPS -> {
                drawableResId = R.drawable.ic_apps_white_24dp
                actionLabelResId = R.string.action_apps
                stateLabelResId = 0
            }
            Actions.PHONE -> {
                drawableResId = R.drawable.ic_phone_24dp
                actionLabelResId = R.string.action_phone
                stateLabelResId = 0
            }
            Actions.BRIGHTNESS -> {
                drawableResId = R.drawable.ic_brightness_medium
                actionLabelResId = R.string.action_brightness
                stateLabelResId = 0
            }
            Actions.HOTSPOT -> {
                tA = action as ToggleAction
                drawableResId = R.drawable.ic_wifi_tethering
                actionLabelResId = R.string.action_hotspot
                stateLabelResId =
                    if (tA.isEnabled) R.string.state_on else R.string.state_off
            }
            Actions.GESTURES -> {
                drawableResId = R.drawable.ic_touch_app
                actionLabelResId = R.string.action_gestures
                stateLabelResId = 0
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ActionButtonViewModel

        if (action != other.action) return false
        if (actionType != other.actionType) return false
        if (actionLabelResId != other.actionLabelResId) return false
        if (stateLabelResId != other.stateLabelResId) return false
        if (buttonState != other.buttonState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + actionType.hashCode()
        result = 31 * result + actionLabelResId
        result = 31 * result + stateLabelResId
        result = 31 * result + (buttonState?.hashCode() ?: 0)
        return result
    }
}