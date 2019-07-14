package com.thewizrd.shared_resources.helpers;

public final class ToggleAction extends Action {
    private boolean enabled = false;

    public ToggleAction(Actions action, boolean enabled) {
        super(action);
        this.enabled = enabled;
    }

    public ToggleAction(Actions action, boolean enabled, boolean isSuccess) {
        this(action, enabled);
        actionSuccessful = isSuccess;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
