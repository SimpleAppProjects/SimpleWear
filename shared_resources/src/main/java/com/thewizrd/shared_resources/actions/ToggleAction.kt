package com.thewizrd.shared_resources.actions

class ToggleAction(action: Actions, var isEnabled: Boolean) : Action(action) {
    init {
        isActionSuccessful = true
    }
}