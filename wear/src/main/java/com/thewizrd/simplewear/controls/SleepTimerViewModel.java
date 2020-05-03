package com.thewizrd.simplewear.controls;

import androidx.lifecycle.ViewModel;

public class SleepTimerViewModel extends ViewModel {
    public static final int DEFAULT_TIME_MIN = 5;
    public static final int MAX_TIME_IN_MINS = 120;

    private int progressTimeInMins = DEFAULT_TIME_MIN;

    public int getProgressTimeInMins() {
        return progressTimeInMins;
    }

    public void setProgressTimeInMins(int progressTimeInMins) {
        this.progressTimeInMins = progressTimeInMins;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        progressTimeInMins = DEFAULT_TIME_MIN;
    }
}
