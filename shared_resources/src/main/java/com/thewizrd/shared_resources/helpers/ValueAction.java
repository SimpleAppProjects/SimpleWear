package com.thewizrd.shared_resources.helpers;

public final class ValueAction extends Action {
    private ValueDirection direction;

    public ValueAction(Actions action, ValueDirection direction) {
        super(action);
        this.direction = direction;
    }

    public ValueDirection getDirection() {
        return direction;
    }

    public void setDirection(ValueDirection direction) {
        this.direction = direction;
    }
}
