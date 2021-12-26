package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class Actions(val value: Int) {
    WIFI(0),
    BLUETOOTH(1),
    MOBILEDATA(2),
    LOCATION(3),
    TORCH(4),
    LOCKSCREEN(5),
    VOLUME(6),
    DONOTDISTURB(7),
    RINGER(8),
    MUSICPLAYBACK(9),
    SLEEPTIMER(10),
    APPS(11),
    PHONE(12),
    BRIGHTNESS(13),
    HOTSPOT(14);

    companion object {
        private val map = SparseArray<Actions>()

        fun valueOf(value: Int): Actions {
            return map[value]
        }

        init {
            for (action in values()) {
                map.put(action.value, action)
            }
        }
    }
}