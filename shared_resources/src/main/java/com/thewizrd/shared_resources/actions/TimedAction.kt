package com.thewizrd.shared_resources.actions

class TimedAction(var timeInMillis: Long, val action: Action) : Action(Actions.TIMEDACTION) {
    companion object {
        fun getSupportedActions(): List<Actions> {
            return Actions.entries.filter {
                when (it) {
                    Actions.WIFI,
                    Actions.BLUETOOTH,
                    Actions.MOBILEDATA,
                    Actions.LOCATION,
                    Actions.TORCH,
                    Actions.DONOTDISTURB,
                    Actions.RINGER,
                    Actions.HOTSPOT -> true

                    else -> false
                }
            }
        }
    }
}