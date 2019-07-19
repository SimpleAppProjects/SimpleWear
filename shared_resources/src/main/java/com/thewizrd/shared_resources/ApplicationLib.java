package com.thewizrd.shared_resources;

import android.content.Context;

import com.thewizrd.shared_resources.helpers.AppState;

public interface ApplicationLib {
    Context getAppContext();

    AppState getAppState();

    boolean isPhone();
}
