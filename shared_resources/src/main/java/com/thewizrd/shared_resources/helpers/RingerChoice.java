package com.thewizrd.shared_resources.helpers;

import android.util.SparseArray;

public enum RingerChoice {
    SILENT(0),
    VIBRATION(1),
    SOUND(2);

    private final int value;

    public int getValue() {
        return value;
    }

    private RingerChoice(int value) {
        this.value = value;
    }

    private static SparseArray<RingerChoice> map = new SparseArray<>();

    static {
        for (RingerChoice choice : values()) {
            map.put(choice.value, choice);
        }
    }

    public static RingerChoice valueOf(int value) {
        return map.get(value);
    }
}