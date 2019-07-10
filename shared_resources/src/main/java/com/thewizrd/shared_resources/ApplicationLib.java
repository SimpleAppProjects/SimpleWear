package com.thewizrd.shared_resources;

import android.content.Context;

public interface ApplicationLib {
    Context getAppContext();

    AppState getAppState();

    boolean isPhone();
}
