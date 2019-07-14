package com.thewizrd.shared_resources.helpers;

public abstract class Action {
    protected Actions action;
    protected boolean actionSuccessful = true;

    public boolean isActionSuccessful() {
        return actionSuccessful;
    }

    public void setActionSuccessful(boolean success) {
        actionSuccessful = success;
    }

    public Actions getAction() {
        return action;
    }

    public Action(Actions action) {
        this.action = action;
    }
}