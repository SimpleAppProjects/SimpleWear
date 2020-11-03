package com.thewizrd.shared_resources.actions

import android.os.Build

class MultiChoiceAction : Action {
    private var value = 0

    constructor(action: Actions) : super(action)
    constructor(action: Actions, choice: Int) : super(action) {
        this.choice = choice
    }

    var choice: Int
        get() = value
        set(value) {
            val maxStates = numberOfStates
            var choice = value % maxStates
            if (choice < 0) choice += maxStates

            this.value = choice
        }

    val numberOfStates: Int
        get() {
            return when (actionType) {
                Actions.WIFI,
                Actions.BLUETOOTH,
                Actions.MOBILEDATA,
                Actions.TORCH,
                Actions.LOCKSCREEN,
                Actions.VOLUME -> 1
                Actions.LOCATION -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) LocationState.values().size else 1
                Actions.DONOTDISTURB -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) DNDChoice.values().size else 1
                Actions.RINGER -> RingerChoice.values().size
                else -> 1
            }
        }
}