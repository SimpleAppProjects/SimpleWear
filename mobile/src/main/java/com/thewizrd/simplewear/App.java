package com.thewizrd.simplewear;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.jakewharton.threetenabp.AndroidThreeTen;
import com.thewizrd.shared_resources.ApplicationLib;
import com.thewizrd.shared_resources.SimpleLibrary;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.AppState;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.wearable.WearableWorker;

public class App extends Application implements ApplicationLib, Application.ActivityLifecycleCallbacks {
    private static ApplicationLib sInstance = null;

    private Context context;
    private AppState applicationState;
    private BroadcastReceiver mActionsReceiver;
    private ContentObserver mContentObserver;

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
        return true;
    }

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        sInstance = this;

        registerActivityLifecycleCallbacks(this);
        applicationState = AppState.CLOSED;

        // Init shared library
        SimpleLibrary.init(this);
        AndroidThreeTen.init(this);

        // Start logger
        Logger.init(context);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        FirebaseCrashlytics.getInstance().sendUnsentReports();

        // Init common action broadcast receiver
        mActionsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int battPct = (int) ((level / (float) scale) * 100);

                    int batStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = (batStatus == BatteryManager.BATTERY_STATUS_CHARGING) || (batStatus == BatteryManager.BATTERY_STATUS_FULL);

                    String jsonData = JSONParser.serializer(new BatteryStatus(battPct, isCharging), BatteryStatus.class);
                    WearableWorker.sendStatusUpdate(context, WearableWorker.ACTION_SENDBATTERYUPDATE, jsonData);
                } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                    WearableWorker.sendActionUpdate(context, Actions.MOBILEDATA);
                } else if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    WearableWorker.sendActionUpdate(context, Actions.LOCATION);
                } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())) {
                    WearableWorker.sendActionUpdate(context, Actions.RINGER);
                } else if (NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED.equals(intent.getAction())) {
                    WearableWorker.sendActionUpdate(context, Actions.DONOTDISTURB);
                }
            }
        };
        IntentFilter actionsFilter = new IntentFilter();
        actionsFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        actionsFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        actionsFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        actionsFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        actionsFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);

        context.registerReceiver(mActionsReceiver, actionsFilter);

        // Register listener system settings
        ContentResolver contentResolver = getContentResolver();
        Uri setting = Settings.Global.getUriFor("mobile_data");
        mContentObserver = new ContentObserver(new Handler()) {
            @Override
            public boolean deliverSelfNotifications() {
                return super.deliverSelfNotifications();
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);

                if (uri.toString().contains("mobile_data")) {
                    WearableWorker.sendActionUpdate(context, Actions.MOBILEDATA);
                }
            }
        };
        contentResolver.registerContentObserver(setting, false, mContentObserver);

        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                Logger.writeLine(Log.ERROR, e, "Uncaught exception!");

                if (oldHandler != null)
                    oldHandler.uncaughtException(t, e);
                else
                    System.exit(2);
            }
        });

        WearableWorker.sendStatusUpdate(context);
    }

    @Override
    public void onTerminate() {
        getContentResolver().unregisterContentObserver(mContentObserver);
        context.unregisterReceiver(mActionsReceiver);
        super.onTerminate();
        // Shutdown logger
        Logger.shutdown();
        SimpleLibrary.unRegister();
        sInstance = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        applicationState = AppState.FOREGROUND;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        applicationState = AppState.FOREGROUND;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        applicationState = AppState.BACKGROUND;
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        applicationState = AppState.CLOSED;
    }
}
