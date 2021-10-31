package com.thewizrd.shared_resources.actions

import android.util.SparseArray

enum class ActionStatus(val value: Int) {
    UNKNOWN(0),
    SUCCESS(1),
    FAILURE(2),
    PERMISSION_DENIED(3),
    TIMEOUT(4),
    REMOTE_FAILURE(5),
    REMOTE_PERMISSION_DENIED(6);

    companion object {
        private val map = SparseArray<ActionStatus>()

        fun valueOf(value: Int): ActionStatus {
            return map[value] ?: UNKNOWN
        }

        init {
            for (status in values()) {
                map.put(status.value, status)
            }
        }
    }
}