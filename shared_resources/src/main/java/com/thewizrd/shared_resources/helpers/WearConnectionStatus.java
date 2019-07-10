package com.thewizrd.shared_resources.helpers;

import android.util.SparseArray;

public enum WearConnectionStatus {
    DISCONNECTED(0),
    CONNECTING(1),
    APPNOTINSTALLED(2),
    CONNECTED(3);

    private final int value;

    public int getValue() {
        return value;
    }

    private WearConnectionStatus(int value) {
        this.value = value;
    }

    private static SparseArray<WearConnectionStatus> map = new SparseArray<>();

    static {
        for (WearConnectionStatus connectionStatus : values()) {
            map.put(connectionStatus.value, connectionStatus);
        }
    }

    public static WearConnectionStatus valueOf(int value) {
        return map.get(value);
    }
}
