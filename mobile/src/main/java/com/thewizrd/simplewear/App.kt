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
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.wearable.WearableWorker
import kotlin.system.exitProcess

class App : Application(), ApplicationLib, ActivityLifecycleCallbacks, Configuration.Provider {
    companion object {
        @JvmStatic
        lateinit var instance: ApplicationLib
            private set
    }

    override lateinit var appContext: Context
        private set
    override lateinit var applicationState: AppState
        private set
    override val isPhone: Boolean = true

    private var mActivitiesStarted = 0

    private lateinit var mActionsReceiver: BroadcastReceiver
    private lateinit var mContentObserver: ContentObserver

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
        registerActivityLifecycleCallbacks(this)
        applicationState = AppState.CLOSED
        mActivitiesStarted = 0

        // Init shared library
        SimpleLibrary.initialize(this)

        // Start logger
        Logger.init(appContext)
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            sendUnsentReports()
        }

        // Init common action broadcast receiver
        mActionsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when {
                    Intent.ACTION_BATTERY_CHANGED == intent.action -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val battPct = (level / scale.toFloat() * 100).toInt()
                        val batStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging =
                            batStatus == BatteryManager.BATTERY_STATUS_CHARGING || batStatus == BatteryManager.BATTERY_STATUS_FULL
                        val jsonData = JSONParser.serializer(
                            BatteryStatus(battPct, isCharging),
                            BatteryStatus::class.java
                        )
                        WearableWorker.sendStatusUpdate(
                            context,
                            WearableWorker.ACTION_SENDBATTERYUPDATE,
                            jsonData
                        )
                    }
                    ConnectivityManager.CONNECTIVITY_ACTION == intent.action -> {
                        WearableWorker.sendActionUpdate(context, Actions.MOBILEDATA)
                    }
                    LocationManager.PROVIDERS_CHANGED_ACTION == intent.action || LocationManager.MODE_CHANGED_ACTION == intent.action -> {
                        WearableWorker.sendActionUpdate(context, Actions.LOCATION)
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION == intent.action -> {
                        WearableWorker.sendActionUpdate(context, Actions.RINGER)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED == intent.action -> {
                        WearableWorker.sendActionUpdate(context, Actions.DONOTDISTURB)
                    }
                }
            }
        }

        val actionsFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        appContext.registerReceiver(mActionsReceiver, actionsFilter)

        // Register listener system settings
        val contentResolver = contentResolver
        val setting = Settings.Global.getUriFor("mobile_data")
        mContentObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean, uri: Uri) {
                super.onChange(selfChange, uri)
                if (uri.toString().contains("mobile_data")) {
                    WearableWorker.sendActionUpdate(appContext, Actions.MOBILEDATA)
                }
            }
        }
        contentResolver.registerContentObserver(setting, false, mContentObserver)

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.writeLine(Log.ERROR, e, "Uncaught exception!")
            if (oldHandler != null) {
                oldHandler.uncaughtException(t, e)
            } else {
                exitProcess(2)
            }
        }

        WearableWorker.sendStatusUpdate(appContext)
    }

    override fun onTerminate() {
        contentResolver.unregisterContentObserver(mContentObserver)
        appContext.unregisterReceiver(mActionsReceiver)
        // Shutdown logger
        Logger.shutdown()
        SimpleLibrary.unregister()
        super.onTerminate()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applicationState = AppState.FOREGROUND
    }

    override fun onActivityStarted(activity: Activity) {
        if (mActivitiesStarted == 0) applicationState = AppState.FOREGROUND
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--
        if (mActivitiesStarted == 0) applicationState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains("MainActivity")) {
            applicationState = AppState.CLOSED
        }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
    }
}