package com.thewizrd.simplewear

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SharedModule
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.media.MediaPlayerActivity
import kotlinx.coroutines.cancel
import kotlin.system.exitProcess

class App : Application(), ActivityLifecycleCallbacks {
    private lateinit var applicationState: AppState
    private var mActivitiesStarted = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)
        applicationState = AppState.CLOSED
        mActivitiesStarted = 0

        // Initialize app dependencies (library module chain)
        // 1. ApplicationLib + SharedModule, 2. Firebase
        appLib = object : ApplicationLib() {
            override val context = applicationContext
            override val preferences: SharedPreferences
                get() = PreferenceManager.getDefaultSharedPreferences(context)
            override val appState: AppState
                get() = applicationState
            override val isPhone = false
        }

        sharedDeps = object : SharedModule() {
            override val context = appLib.context // keep same context as applib
        }

        FirebaseConfigurator.initialize(applicationContext)

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.writeLine(Log.ERROR, e)
            if (oldHandler != null) {
                oldHandler.uncaughtException(t, e)
            } else {
                exitProcess(2)
            }
        }

        startMigration()
    }

    override fun onTerminate() {
        super.onTerminate()
        // Shutdown logger
        Logger.shutdown()
        appLib.appScope.cancel()
    }

    private fun startMigration() {
        val versionCode = runCatching {
            val packageInfo = applicationContext.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionCode.toLong()
        }.getOrDefault(0)

        if (com.thewizrd.simplewear.preferences.Settings.getVersionCode() < versionCode) {
            // Add NFC Option
            if (com.thewizrd.simplewear.preferences.Settings.getVersionCode() < 361917000) {
                val dashConfig = com.thewizrd.simplewear.preferences.Settings.getDashboardConfig()
                    ?.toMutableList()
                if (dashConfig != null && !dashConfig.contains(Actions.NFC)) {
                    dashConfig.add(Actions.NFC)
                    com.thewizrd.simplewear.preferences.Settings.setDashboardConfig(dashConfig)
                }
            }
        }

        if (versionCode > 0) {
            com.thewizrd.simplewear.preferences.Settings.setVersionCode(versionCode)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
        if ((activity is DashboardActivity || activity is MediaPlayerActivity) && applicationState != AppState.FOREGROUND) {
            applicationState = AppState.FOREGROUND
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--
        if (mActivitiesStarted == 0) applicationState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains(DashboardActivity::class.java.simpleName)) {
            applicationState = AppState.CLOSED
        }
    }
}