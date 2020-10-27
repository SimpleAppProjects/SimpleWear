package com.thewizrd.shared_resources.helpers

import android.util.SparseArray

enum class ActionStatus(val value: Int) {
    UNKNOWN(0),
    SUCCESS(1),
    FAILURE(2),
    PERMISSION_DENIED(3),
    TIMEOUT(4);

    companion object {
        private val map = SparseArray<ActionStatus>()

        fun valueOf(value: Int): ActionStatus {
            return map[value]
        }

        init {
            for (status in values()) {
                map.put(status.value, status)
            }
        }
    }
}