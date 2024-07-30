package com.thewizrd.simplewear.preferences

import com.thewizrd.shared_resources.actions.Actions

object DashboardTileUtils {
    const val MAX_BUTTONS = 6

    val DEFAULT_TILES by lazy {
        listOf(
            Actions.WIFI, Actions.BLUETOOTH, Actions.LOCKSCREEN,
            Actions.DONOTDISTURB, Actions.RINGER, Actions.TORCH
        )
    }

    fun isActionAllowed(actionType: Actions): Boolean {
        return when (actionType) {
            Actions.VOLUME,
            Actions.MUSICPLAYBACK,
            Actions.SLEEPTIMER,
            Actions.APPS,
            Actions.PHONE,
            Actions.BRIGHTNESS,
            Actions.GESTURES -> false
            else -> true
        }
    }
}