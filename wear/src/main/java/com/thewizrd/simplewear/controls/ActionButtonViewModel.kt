package com.thewizrd.simplewear.controls

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.simplewear.*
import com.thewizrd.simplewear.WearableListenerActivity.Companion.EXTRA_ACTION
import java.util.*

class ActionButtonViewModel(val action: Action) {
    @get:DrawableRes
    @DrawableRes
    var drawableID: Int
        private set

    val actionType: Actions
        get() = action.actionType

    var actionLabel: String? = null
        private set
    var stateLabel: String? = null
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
                Actions.PHONE ->
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
        drawableID = R.drawable.ic_close_white_24dp
        buttonState = false
        initialize(action)
    }

    private fun initialize(action: Action?) {
        buttonState = true
        drawableID = R.drawable.ic_close_white_24dp

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

    fun onClick(activityContext: Activity) {
        action.isActionSuccessful = true

        if (action is ValueAction) {
            val intent: Intent = Intent(activityContext, ValueActionActivity::class.java)
                    .putExtra(EXTRA_ACTION, actionType)
            activityContext.startActivityForResult(intent, -1)
        } else if (action is NormalAction && action.actionType == Actions.MUSICPLAYBACK) {
            val intent = Intent(activityContext, MediaPlayerListActivity::class.java)
            activityContext.startActivityForResult(intent, -1)
        } else if (action is NormalAction && action.actionType == Actions.SLEEPTIMER) {
            if (SleepTimerHelper.isSleepTimerInstalled()) {
                SleepTimerHelper.launchSleepTimer()
            } else {
                action.setActionSuccessful(ActionStatus.PERMISSION_DENIED)
                LocalBroadcastManager.getInstance(activityContext)
                    .sendBroadcast(
                        Intent(WearableHelper.ActionsPath)
                            .putExtra(
                                WearableListenerActivity.EXTRA_ACTIONDATA,
                                serializer(action, Action::class.java)
                            )
                    )
            }
        } else if (action is NormalAction && action.actionType == Actions.APPS) {
            val intent = Intent(activityContext, AppLauncherActivity::class.java)
            activityContext.startActivityForResult(intent, -1)
        } else if (action is NormalAction && action.actionType == Actions.PHONE) {
            val intent = Intent(activityContext, CallManagerActivity::class.java)
            activityContext.startActivityForResult(intent, -1)
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

            LocalBroadcastManager.getInstance(activityContext)
                .sendBroadcast(Intent(WearableListenerActivity.ACTION_CHANGED)
                    .putExtra(WearableListenerActivity.EXTRA_ACTIONDATA,
                        serializer(action, Action::class.java)))
        }
    }

    private fun updateIconAndLabel() {
        val tA: ToggleAction
        val mA: MultiChoiceAction
        val context = App.instance.appContext

        when (actionType) {
            Actions.WIFI -> {
                tA = action as ToggleAction
                drawableID = if (tA.isEnabled) R.drawable.ic_network_wifi_white_24dp else R.drawable.ic_signal_wifi_off_white_24dp
                actionLabel = context.getString(R.string.action_wifi)
                stateLabel = if (tA.isEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
            Actions.BLUETOOTH -> {
                tA = action as ToggleAction
                drawableID = if (tA.isEnabled) R.drawable.ic_bluetooth_white_24dp else R.drawable.ic_bluetooth_disabled_white_24dp
                actionLabel = context.getString(R.string.action_bt)
                stateLabel = if (tA.isEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
            Actions.MOBILEDATA -> {
                tA = action as ToggleAction
                drawableID = if (tA.isEnabled) R.drawable.ic_network_cell_white_24dp else R.drawable.ic_signal_cellular_off_white_24dp
                actionLabel = context.getString(R.string.action_mobiledata)
                stateLabel = if (tA.isEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
            Actions.LOCATION -> {
                actionLabel = context.getString(R.string.action_location)

                val locationState = if (action is ToggleAction) {
                    if (action.isEnabled) LocationState.HIGH_ACCURACY else LocationState.OFF
                } else {
                    mA = action as MultiChoiceAction
                    LocationState.valueOf(mA.choice)
                }
                when (locationState) {
                    LocationState.OFF -> {
                        drawableID = R.drawable.ic_location_off_white_24dp
                        stateLabel = context.getString(R.string.state_off)
                    }
                    LocationState.SENSORS_ONLY -> {
                        drawableID = R.drawable.ic_baseline_gps_fixed_24dp
                        stateLabel = context.getString(R.string.locationstate_sensorsonly)
                    }
                    LocationState.BATTERY_SAVING -> {
                        drawableID = R.drawable.ic_outline_location_on_24dp
                        stateLabel = context.getString(R.string.locationstate_batterysaving)
                    }
                    LocationState.HIGH_ACCURACY -> {
                        drawableID = R.drawable.ic_location_on_white_24dp
                        stateLabel = context.getString(
                                if (action is ToggleAction) {
                                    R.string.state_on
                                } else {
                                    R.string.locationstate_highaccuracy
                                }
                        )
                    }
                }
            }
            Actions.TORCH -> {
                tA = action as ToggleAction
                drawableID = R.drawable.ic_lightbulb_outline_white_24dp
                actionLabel = context.getString(R.string.action_torch)
                stateLabel = if (tA.isEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
            Actions.LOCKSCREEN -> {
                drawableID = R.drawable.ic_lock_outline_white_24dp
                actionLabel = context.getString(R.string.action_lockscreen)
                stateLabel = null
            }
            Actions.VOLUME -> {
                drawableID = R.drawable.ic_volume_up_white_24dp
                actionLabel = context.getString(R.string.action_volume)
                stateLabel = null
            }
            Actions.DONOTDISTURB -> {
                actionLabel = context.getString(R.string.action_dnd)

                val dndChoice = if (action is ToggleAction) {
                    if (action.isEnabled) DNDChoice.PRIORITY else DNDChoice.OFF
                } else {
                    mA = action as MultiChoiceAction
                    DNDChoice.valueOf(mA.choice)
                }
                when (dndChoice) {
                    DNDChoice.OFF -> {
                        drawableID = R.drawable.ic_do_not_disturb_off_white_24dp
                        stateLabel = context.getString(R.string.state_off)
                    }
                    DNDChoice.PRIORITY -> {
                        drawableID = R.drawable.ic_error_white_24dp
                        stateLabel = context.getString(
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                                    R.string.dndstate_priority
                                } else {
                                    R.string.state_on
                                }
                        )
                    }
                    DNDChoice.ALARMS -> {
                        drawableID = R.drawable.ic_alarm_white_24dp
                        stateLabel = context.getString(R.string.dndstate_alarms)
                    }
                    DNDChoice.SILENCE -> {
                        drawableID = R.drawable.ic_notifications_off_white_24dp
                        stateLabel = context.getString(R.string.dndstate_silence)
                    }
                }
            }
            Actions.RINGER -> {
                mA = action as MultiChoiceAction

                actionLabel = context.getString(R.string.action_ringer)

                when (RingerChoice.valueOf(mA.choice)) {
                    RingerChoice.VIBRATION -> {
                        drawableID = R.drawable.ic_vibration_white_24dp
                        stateLabel = context.getString(R.string.ringerstate_vib)
                    }
                    RingerChoice.SOUND -> {
                        drawableID = R.drawable.ic_notifications_active_white_24dp
                        stateLabel = context.getString(R.string.ringerstate_sound)
                    }
                    RingerChoice.SILENT -> {
                        drawableID = R.drawable.ic_volume_off_white_24dp
                        stateLabel = context.getString(R.string.ringerstate_silent)
                    }
                }
            }
            Actions.MUSICPLAYBACK -> {
                drawableID = R.drawable.ic_play_circle_filled_white_24dp
                actionLabel = context.getString(R.string.action_musicplayback)
                stateLabel = null
            }
            Actions.SLEEPTIMER -> {
                drawableID = R.drawable.ic_sleep_timer
                actionLabel = context.getString(R.string.action_sleeptimer)
                stateLabel = null
            }
            Actions.APPS -> {
                drawableID = R.drawable.ic_apps_white_24dp
                actionLabel = context.getString(R.string.action_apps)
                stateLabel = null
            }
            Actions.PHONE -> {
                drawableID = R.drawable.ic_phone_24dp
                actionLabel = context.getString(R.string.action_phone)
                stateLabel = null
            }
            Actions.BRIGHTNESS -> {
                drawableID = R.drawable.ic_brightness_medium
                actionLabel = context.getString(R.string.action_brightness)
                stateLabel = null
            }
            Actions.HOTSPOT -> {
                tA = action as ToggleAction
                drawableID = R.drawable.ic_wifi_tethering
                actionLabel = context.getString(R.string.action_hotspot)
                stateLabel =
                    if (tA.isEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ActionButtonViewModel

        if (action != other.action) return false
        if (actionType != other.actionType) return false
        if (actionLabel != other.actionLabel) return false
        if (stateLabel != other.stateLabel) return false
        if (buttonState != other.buttonState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + actionType.hashCode()
        result = 31 * result + (actionLabel?.hashCode() ?: 0)
        result = 31 * result + (stateLabel?.hashCode() ?: 0)
        result = 31 * result + (buttonState?.hashCode() ?: 0)
        return result
    }
}