package com.thewizrd.shared_resources;

import android.content.Context;

public final class SimpleLibrary {
    private ApplicationLib mApp;
    private Context mContext;

    private static SimpleLibrary sSimpleLib;

    public SimpleLibrary() {

    }

    public SimpleLibrary(ApplicationLib app) {
        mApp = app;
        mContext = app.getAppContext();
    }

    public static SimpleLibrary getInstance() {
        if (sSimpleLib == null)
            sSimpleLib = new SimpleLibrary();

        return sSimpleLib;
    }

    public static void init(ApplicationLib app) {
        if (sSimpleLib == null || sSimpleLib.mApp == null || sSimpleLib.mContext == null)
            sSimpleLib = new SimpleLibrary(app);
    }

    public static void unRegister() {
        sSimpleLib = null;
    }

    public ApplicationLib getApp() {
        return mApp;
    }

    public Context getAppContext() {
        return mContext;
    }
}
