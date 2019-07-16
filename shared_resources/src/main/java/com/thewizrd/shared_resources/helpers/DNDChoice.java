package com.thewizrd.shared_resources.helpers;

import android.util.SparseArray;

public enum DNDChoice {
    OFF(0),
    PRIORITY(1),
    ALARMS(2),
    SILENCE(3);

    private final int value;

    public int getValue() {
        return value;
    }

    private DNDChoice(int value) {
        this.value = value;
    }

    private static SparseArray<DNDChoice> map = new SparseArray<>();

    static {
        for (DNDChoice choice : values()) {
            map.put(choice.value, choice);
        }
    }

    public static DNDChoice valueOf(int value) {
        return map.get(value);
    }
}