package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class DNDChoice(val value: Int) {
    OFF(0),
    PRIORITY(1),
    ALARMS(2),
    SILENCE(3);

    companion object {
        private val map = SparseArray<DNDChoice>()

        fun valueOf(value: Int): DNDChoice {
            return map[value]
        }

        init {
            for (choice in values()) {
                map.put(choice.value, choice)
            }
        }
    }
}