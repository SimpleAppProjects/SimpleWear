package com.thewizrd.shared_resources.actions

open class ValueAction(action: Actions, var direction: ValueDirection) : Action(action) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueAction) return false

        if (direction != other.direction) return false

        return true
    }

    override fun hashCode(): Int {
        return direction.hashCode()
    }
}