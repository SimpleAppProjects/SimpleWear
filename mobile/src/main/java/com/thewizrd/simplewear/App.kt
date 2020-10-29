package com.thewizrd.simplewear

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jakewharton.threetenabp.AndroidThreeTen
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.helpers.Actions
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.helpers.BatteryStatus
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.wearable.WearableWorker
import com.thewizrd.simplewear.wearable.WearableWorker.Companion.sendActionUpdate
import com.thewizrd.simplewear.wearable.WearableWorker.Companion.sendStatusUpdate

class App : Application(), ApplicationLib, ActivityLifecycleCallbacks {
    companion object {
        @get:Synchronized
        var instance: ApplicationLib? = null
            private set
    }

    override lateinit var appContext: Context
        private set
    override lateinit var appState: AppState
        private set
    private lateinit var mActionsReceiver: BroadcastReceiver
    private lateinit var mContentObserver: ContentObserver
    override val isPhone: Boolean = true

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
        registerActivityLifecycleCallbacks(this)
        appState = AppState.CLOSED

        // Init shared library
        SimpleLibrary.init(this)
        AndroidThreeTen.init(this)

        // Start logger
        Logger.init(appContext)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().sendUnsentReports()

        // Init common action broadcast receiver
        mActionsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when {
                    Intent.ACTION_BATTERY_CHANGED == intent.action -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val battPct = (level / scale.toFloat() * 100).toInt()
                        val batStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = batStatus == BatteryManager.BATTERY_STATUS_CHARGING || batStatus == BatteryManager.BATTERY_STATUS_FULL
                        val jsonData = serializer(BatteryStatus(battPct, isCharging), BatteryStatus::class.java)
                        sendStatusUpdate(context, WearableWorker.ACTION_SENDBATTERYUPDATE, jsonData)
                    }
                    ConnectivityManager.CONNECTIVITY_ACTION == intent.action -> {
                        sendActionUpdate(context, Actions.MOBILEDATA)
                    }
                    LocationManager.PROVIDERS_CHANGED_ACTION == intent.action || LocationManager.MODE_CHANGED_ACTION == intent.action -> {
                        sendActionUpdate(context, Actions.LOCATION)
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION == intent.action -> {
                        sendActionUpdate(context, Actions.RINGER)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED == intent.action -> {
                        sendActionUpdate(context, Actions.DONOTDISTURB)
                    }
                }
            }
        }
        val actionsFilter = IntentFilter()
        actionsFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        actionsFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        actionsFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        actionsFilter.addAction(LocationManager.MODE_CHANGED_ACTION)
        actionsFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        actionsFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        appContext.registerReceiver(mActionsReceiver, actionsFilter)

        // Register listener system settings
        val contentResolver = contentResolver
        val setting = Settings.Global.getUriFor("mobile_data")
        mContentObserver = object : ContentObserver(Handler()) {
            override fun deliverSelfNotifications(): Boolean {
                return super.deliverSelfNotifications()
            }

            override fun onChange(selfChange: Boolean, uri: Uri) {
                super.onChange(selfChange, uri)
                if (uri.toString().contains("mobile_data")) {
                    sendActionUpdate(appContext, Actions.MOBILEDATA)
                }
            }
        }
        contentResolver.registerContentObserver(setting, false, mContentObserver)

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.writeLine(Log.ERROR, e, "Uncaught exception!")
            if (oldHandler != null) oldHandler.uncaughtException(t, e) else System.exit(2)
        }

        sendStatusUpdate(appContext)
    }

    override fun onTerminate() {
        contentResolver.unregisterContentObserver(mContentObserver)
        appContext.unregisterReceiver(mActionsReceiver)
        super.onTerminate()
        // Shutdown logger
        Logger.shutdown()
        SimpleLibrary.unRegister()
        instance = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        appState = AppState.FOREGROUND
    }

    override fun onActivityStarted(activity: Activity) {
        appState = AppState.FOREGROUND
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        appState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        appState = AppState.CLOSED
    }
}