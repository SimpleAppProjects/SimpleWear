package com.thewizrd.shared_resources.helpers

class ToggleAction(action: Actions, var isEnabled: Boolean) : Action(action) {
    init {
        isActionSuccessful = true
    }
}