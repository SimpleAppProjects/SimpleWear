package com.thewizrd.shared_resources.actions

class NormalAction(action: Actions) : Action(action) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalAction) return false
        if (!super.equals(other)) return false
        return true
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}