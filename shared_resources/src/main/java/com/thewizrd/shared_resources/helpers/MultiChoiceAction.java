package com.thewizrd.shared_resources.helpers;

public final class MultiChoiceAction extends Action {
    private int value;

    public MultiChoiceAction(Actions action) {
        super(action);
    }

    public MultiChoiceAction(Actions action, int choice) {
        super(action);
        this.value = choice;
    }

    public int getChoice() {
        return value;
    }

    public void setChoice(int value) {
        this.value = value;
    }

    public int getNumberOfStates() {
        switch (getAction()) {
            case WIFI:
            case BLUETOOTH:
            case MOBILEDATA:
            case LOCATION:
            case TORCH:
            case LOCKSCREEN:
            case VOLUME:
            default:
                return 1;
            case DONOTDISTURB:
                return DNDChoice.values().length;
            case RINGER:
                return RingerChoice.values().length;
        }
    }
}
