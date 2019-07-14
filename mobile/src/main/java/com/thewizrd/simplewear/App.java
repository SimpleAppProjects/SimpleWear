package com.thewizrd.simplewear;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.thewizrd.shared_resources.AppState;
import com.thewizrd.shared_resources.ApplicationLib;
import com.thewizrd.shared_resources.BatteryStatus;
import com.thewizrd.shared_resources.SimpleLibrary;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

public class App extends Application implements ApplicationLib, Application.ActivityLifecycleCallbacks {
    private static ApplicationLib sInstance = null;

    private Context context;
    private AppState applicationState;
    private BroadcastReceiver mActionsReceiver;

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
                    WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDBATTERYUPDATE)
                            .putExtra(WearableDataListenerService.EXTRA_STATUS, jsonData));
                } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                    WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDMOBILEDATAUPDATE));
                } else if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                            .setAction(WearableDataListenerService.ACTION_SENDACTIONUPDATE)
                            .putExtra(WearableDataListenerService.EXTRA_ACTION_CHANGED, Actions.LOCATION));
                }
            }
        };
        IntentFilter actionsFilter = new IntentFilter();
        actionsFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        actionsFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        actionsFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);

        context.registerReceiver(mActionsReceiver, actionsFilter);

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

        WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                .setAction(WearableDataListenerService.ACTION_SENDSTATUSUPDATE));
    }

    @Override
    public void onTerminate() {
        context.unregisterReceiver(mActionsReceiver);
        super.onTerminate();
        // Shutdown logger
        Logger.shutdown();
        SimpleLibrary.unRegister();
        sInstance = null;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        applicationState = AppState.FOREGROUND;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        applicationState = AppState.FOREGROUND;
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        applicationState = AppState.BACKGROUND;
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        applicationState = AppState.CLOSED;
    }
}
