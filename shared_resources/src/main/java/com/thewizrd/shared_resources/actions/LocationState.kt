package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class LocationState(val value: Int) {
    OFF(0),
    SENSORS_ONLY(1),
    BATTERY_SAVING(2),
    HIGH_ACCURACY(3);

    companion object {
        private val map = SparseArray<LocationState>()

        fun valueOf(value: Int): LocationState {
            return map[value]
        }

        init {
            for (choice in values()) {
                map.put(choice.value, choice)
            }
        }
    }
}