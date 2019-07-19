package com.thewizrd.shared_resources.helpers;

import android.util.SparseArray;

public enum ActionStatus {
    UNKNOWN(0),
    SUCCESS(1),
    FAILURE(2),
    PERMISSION_DENIED(3),
    TIMEOUT(4);

    private final int value;

    public int getValue() {
        return value;
    }

    private ActionStatus(int value) {
        this.value = value;
    }

    private static SparseArray<ActionStatus> map = new SparseArray<>();

    static {
        for (ActionStatus status : values()) {
            map.put(status.value, status);
        }
    }

    public static ActionStatus valueOf(int value) {
        return map.get(value);
    }
}