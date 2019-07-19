package com.thewizrd.shared_resources.helpers;

public abstract class Action {
    protected Actions action;
    boolean actionSuccessful;
    private ActionStatus status;

    Action() {
        actionSuccessful = true;
        status = ActionStatus.UNKNOWN;
    }

    public boolean isActionSuccessful() {
        return actionSuccessful;
    }

    public void setActionSuccessful(boolean success) {
        actionSuccessful = success;
    }

    public void setActionSuccessful(ActionStatus status) {
        actionSuccessful = (status == ActionStatus.SUCCESS);
        this.status = status;
    }

    public ActionStatus getActionStatus() {
        return status;
    }

    public Actions getAction() {
        return action;
    }

    public Action(Actions action) {
        this();
        this.action = action;
    }
}