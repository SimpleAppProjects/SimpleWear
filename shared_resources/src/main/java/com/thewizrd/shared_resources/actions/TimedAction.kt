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
                    Actions.HOTSPOT,
                    Actions.SLEEPTIMER,
                    Actions.NFC -> true

                    else -> false
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimedAction) return false
        if (!super.equals(other)) return false

        if (timeInMillis != other.timeInMillis) return false
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + timeInMillis.hashCode()
        result = 31 * result + action.hashCode()
        return result
    }
}