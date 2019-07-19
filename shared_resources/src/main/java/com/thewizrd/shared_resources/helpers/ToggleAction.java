package com.thewizrd.shared_resources.helpers;

public final class ToggleAction extends Action {
    private boolean enabled;

    private ToggleAction() {
        super();
    }

    public ToggleAction(Actions action, boolean enabled) {
        super(action);
        this.enabled = enabled;
        actionSuccessful = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
