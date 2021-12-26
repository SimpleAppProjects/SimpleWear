package com.thewizrd.shared_resources.actions

open class ValueActionState(
    var currentValue: Int,
    var minValue: Int,
    var maxValue: Int,
    var actionType: Actions
)