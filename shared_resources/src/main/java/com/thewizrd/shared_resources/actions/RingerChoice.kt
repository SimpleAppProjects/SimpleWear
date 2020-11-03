package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class RingerChoice(val value: Int) {
    SILENT(0),
    VIBRATION(1),
    SOUND(2);

    companion object {
        private val map = SparseArray<RingerChoice>()

        fun valueOf(value: Int): RingerChoice {
            return map[value]
        }

        init {
            for (choice in values()) {
                map.put(choice.value, choice)
            }
        }
    }
}