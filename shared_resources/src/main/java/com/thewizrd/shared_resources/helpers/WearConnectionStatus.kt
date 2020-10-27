package com.thewizrd.shared_resources.helpers

import android.util.SparseArray

enum class WearConnectionStatus(val value: Int) {
    DISCONNECTED(0),
    CONNECTING(1),
    APPNOTINSTALLED(2),
    CONNECTED(3);

    companion object {
        private val map = SparseArray<WearConnectionStatus>()

        fun valueOf(value: Int): WearConnectionStatus {
            return map[value]
        }

        init {
            for (connectionStatus in values()) {
                map.put(connectionStatus.value, connectionStatus)
            }
        }
    }
}