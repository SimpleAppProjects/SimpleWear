package com.thewizrd.shared_resources.actions

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
}