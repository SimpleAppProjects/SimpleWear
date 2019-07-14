package com.thewizrd.shared_resources.helpers;

public final class ValueAction extends Action {
    private int value = -1;

    public ValueAction(Actions action, int value) {
        super(action);
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
