package com.thewizrd.shared_resources.actions

class ToggleAction(action: Actions, var isEnabled: Boolean) : Action(action) {
    init {
        isActionSuccessful = true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToggleAction) return false

        if (isEnabled != other.isEnabled) return false

        return true
    }

    override fun hashCode(): Int {
        return isEnabled.hashCode()
    }
}