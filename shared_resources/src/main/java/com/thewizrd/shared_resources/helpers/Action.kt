package com.thewizrd.shared_resources.helpers

abstract class Action {
    var isActionSuccessful = true

    fun setActionSuccessful(status: ActionStatus) {
        isActionSuccessful = status == ActionStatus.SUCCESS
        actionStatus = status
    }

    var actionStatus: ActionStatus
        private set

    var actionType: Actions
        protected set

    constructor(_action: Actions) {
        this.actionType = _action
        isActionSuccessful = true
        actionStatus = ActionStatus.UNKNOWN
        this.actionType = _action
    }
}