package com.thewizrd.shared_resources.actions

import android.os.Build

abstract class Action(_action: Actions) {
    var isActionSuccessful = true

    fun setActionSuccessful(status: ActionStatus) {
        isActionSuccessful = status == ActionStatus.SUCCESS
        actionStatus = status
    }

    var actionStatus: ActionStatus
        private set

    var actionType: Actions = _action
        protected set

    init {
        isActionSuccessful = true
        actionStatus = ActionStatus.UNKNOWN
    }

    companion object {
        fun getDefaultAction(action: Actions): Action {
            return when (action) {
                Actions.WIFI,
                Actions.BLUETOOTH,
                Actions.MOBILEDATA,
                Actions.TORCH,
                Actions.HOTSPOT ->
                    ToggleAction(action, true)

                Actions.LOCATION ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                        MultiChoiceAction(action)
                    else
                        ToggleAction(action, true)

                Actions.LOCKSCREEN,
                Actions.MUSICPLAYBACK,
                Actions.SLEEPTIMER,
                Actions.APPS,
                Actions.PHONE,
                Actions.GESTURES,
                Actions.TIMEDACTION ->
                    NormalAction(action)

                Actions.VOLUME, Actions.BRIGHTNESS ->
                    ValueAction(action, ValueDirection.UP)

                Actions.DONOTDISTURB ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                        MultiChoiceAction(action)
                    else
                        ToggleAction(action, true)

                Actions.RINGER ->
                    MultiChoiceAction(action)
            }
        }
    }
}