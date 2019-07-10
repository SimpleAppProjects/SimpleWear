package com.thewizrd.shared_resources.helpers;

public abstract class Action {
    protected boolean actionSuccessful;

    public void isActionSuccessful(boolean success) {
        actionSuccessful = success;
    }
}