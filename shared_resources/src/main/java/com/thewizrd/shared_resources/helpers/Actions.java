package com.thewizrd.shared_resources.helpers;

import android.util.SparseArray;

public enum Actions {
    WIFI(0),
    BLUETOOTH(1),
    MOBILEDATA(2);

    private final int value;

    public int getValue() {
        return value;
    }

    private Actions(int value) {
        this.value = value;
    }

    private static SparseArray<Actions> map = new SparseArray<>();

    static {
        for (Actions action : values()) {
            map.put(action.value, action);
        }
    }

    public static Actions valueOf(int value) {
        return map.get(value);
    }
}