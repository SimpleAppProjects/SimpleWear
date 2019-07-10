package com.thewizrd.simplewear;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.thewizrd.shared_resources.AppState;
import com.thewizrd.shared_resources.ApplicationLib;
import com.thewizrd.shared_resources.SimpleLibrary;
import com.thewizrd.shared_resources.utils.Logger;

public class App extends Application implements ApplicationLib, Application.ActivityLifecycleCallbacks {
    private static ApplicationLib sInstance = null;

    private Context context;
    private AppState applicationState;
    private int mActivitiesStarted;

    public static synchronized ApplicationLib getInstance() {
        return sInstance;
    }

    @Override
    public Context getAppContext() {
        return context;
    }

    @Override
    public AppState getAppState() {
        return applicationState;
    }

    @Override
    public boolean isPhone() {
        return false;
    }

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        sInstance = this;

        registerActivityLifecycleCallbacks(this);
        applicationState = AppState.CLOSED;
        mActivitiesStarted = 0;

        // Init shared library
        SimpleLibrary.init(this);
        AndroidThreeTen.init(this);

        // Start logger
        Logger.init(context);

        // Init common action broadcast receiver
        //registerCommonReceiver();

        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.writeLine(Log.ERROR, e);

                if (oldHandler != null)
                    oldHandler.uncaughtException(t, e);
                else
                    System.exit(2);
            }
        });
    }

    /*
    private void registerCommonReceiver() {
        mCommonReceiver = new CommonActionsBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CommonActions.ACTION_SETTINGS_UPDATEAPI);
        filter.addAction(CommonActions.ACTION_SETTINGS_UPDATEGPS);
        filter.addAction(CommonActions.ACTION_SETTINGS_UPDATEUNIT);
        filter.addAction(CommonActions.ACTION_SETTINGS_UPDATEREFRESH);
        filter.addAction(CommonActions.ACTION_WEATHER_SENDLOCATIONUPDATE);
        filter.addAction(CommonActions.ACTION_WEATHER_SENDWEATHERUPDATE);
        filter.addAction(CommonActions.ACTION_WEATHER_UPDATEWIDGETLOCATION);
        filter.addAction(CommonActions.ACTION_WEATHER_UPDATEWIDGETWEATHER);
        filter.addAction(CommonActions.ACTION_WIDGET_REFRESHWIDGETS);
        filter.addAction(CommonActions.ACTION_WIDGET_RESETWIDGETS);

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mCommonReceiver, filter);
    }
    */

    /*
    private void unregisterCommonReceiver() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(mCommonReceiver);
    }
    */

    @Override
    public void onTerminate() {
        //unregisterCommonReceiver();
        super.onTerminate();
        // Shutdown logger
        Logger.shutdown();
        SimpleLibrary.unRegister();
        sInstance = null;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity.getLocalClassName().contains("LaunchActivity") ||
                activity.getLocalClassName().contains("DashboardActivity")) {
            applicationState = AppState.FOREGROUND;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (mActivitiesStarted == 0)
            applicationState = AppState.FOREGROUND;

        mActivitiesStarted++;
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        mActivitiesStarted--;

        if (mActivitiesStarted == 0)
            applicationState = AppState.BACKGROUND;
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activity.getLocalClassName().contains("DashboardActivity")) {
            applicationState = AppState.CLOSED;
        }
    }
}
