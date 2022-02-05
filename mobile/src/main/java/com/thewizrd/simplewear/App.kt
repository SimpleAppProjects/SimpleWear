package com.thewizrd.simplewear

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.location.LocationManager
import android.media.AudioManager
import android.net.*
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.work.Configuration
import com.google.android.material.color.DynamicColors
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.utils.CrashlyticsLoggingTree
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.services.CallControllerService
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
        Logger.registerLogger(CrashlyticsLoggingTree())
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(true)
            sendUnsentReports()
        }

        // Init common action broadcast receiver
        mActionsReceiver = object : BroadcastReceiver() {
            private var mBatteryPct: Int? = null
            private var mIsBatteryCharging: Boolean? = null

            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val batStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                        val battPct = (level / scale.toFloat() * 100f).toInt()
                        val isCharging =
                            batStatus == BatteryManager.BATTERY_STATUS_CHARGING || batStatus == BatteryManager.BATTERY_STATUS_FULL

                        val sendUpdate = mBatteryPct != battPct || mIsBatteryCharging != isCharging

                        mBatteryPct = battPct
                        mIsBatteryCharging = isCharging

                        if (sendUpdate) {
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
                    }
                    LocationManager.PROVIDERS_CHANGED_ACTION,
                    LocationManager.MODE_CHANGED_ACTION -> {
                        WearableWorker.sendActionUpdate(context, Actions.LOCATION)
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        WearableWorker.sendActionUpdate(context, Actions.RINGER)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        WearableWorker.sendActionUpdate(context, Actions.DONOTDISTURB)
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        WearableWorker.sendStatusUpdate(
                            context, WearableWorker.ACTION_SENDWIFIUPDATE,
                            intent.getIntExtra(
                                WifiManager.EXTRA_WIFI_STATE,
                                WifiManager.WIFI_STATE_UNKNOWN
                            )
                        )
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        WearableWorker.sendStatusUpdate(
                            context, WearableWorker.ACTION_SENDBTUPDATE,
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.STATE_ON
                            )
                        )
                    }
                }
            }
        }

        val actionsFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        appContext.registerReceiver(mActionsReceiver, actionsFilter)

        // Register listener system settings
        val setting = Settings.Global.getUriFor("mobile_data")
        mContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri.toString().contains("mobile_data")) {
                    WearableWorker.sendActionUpdate(appContext, Actions.MOBILEDATA)
                }
            }
        }
        contentResolver.registerContentObserver(setting, false, mContentObserver)

        // Register connectivity listener
        runCatching {
            val connMgr = appContext.getSystemService(ConnectivityManager::class.java)
            connMgr.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        WearableWorker.sendActionUpdate(appContext, Actions.MOBILEDATA)
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        WearableWorker.sendActionUpdate(appContext, Actions.MOBILEDATA)
                    }
                }
            )
        }.onFailure {
            // SecurityException: Package android does not belong to xxxxx
            // https://issuetracker.google.com/issues/175055271
            Logger.writeLine(Log.ERROR, it)
        }

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
        if (com.thewizrd.simplewear.preferences.Settings.isBridgeMediaEnabled()) {
            MediaControllerService.enqueueWork(
                this,
                Intent(this, MediaControllerService::class.java)
                    .setAction(MediaControllerService.ACTION_CONNECTCONTROLLER)
                    .putExtra(MediaControllerService.EXTRA_SOFTLAUNCH, true)
            )
        }
        if (com.thewizrd.simplewear.preferences.Settings.isBridgeCallsEnabled()) {
            CallControllerService.enqueueWork(
                this,
                Intent(this, CallControllerService::class.java)
                    .setAction(CallControllerService.ACTION_CONNECTCONTROLLER)
            )
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
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