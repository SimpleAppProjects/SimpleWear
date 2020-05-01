package com.thewizrd.simplewear;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.jakewharton.threetenabp.AndroidThreeTen;
import com.thewizrd.shared_resources.ApplicationLib;
import com.thewizrd.shared_resources.SimpleLibrary;
import com.thewizrd.shared_resources.helpers.AppState;
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
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        FirebaseCrashlytics.getInstance().sendUnsentReports();

        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                Logger.writeLine(Log.ERROR, e);

                if (oldHandler != null)
                    oldHandler.uncaughtException(t, e);
                else
                    System.exit(2);
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // Shutdown logger
        Logger.shutdown();
        SimpleLibrary.unRegister();
        sInstance = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        mActivitiesStarted++;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof WearableListenerActivity && applicationState != AppState.FOREGROUND) {
            applicationState = AppState.FOREGROUND;
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        mActivitiesStarted--;

        if (mActivitiesStarted == 0)
            applicationState = AppState.BACKGROUND;
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity.getLocalClassName().contains("DashboardActivity")) {
            applicationState = AppState.CLOSED;
        }
    }
}
