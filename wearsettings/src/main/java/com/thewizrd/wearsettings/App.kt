package com.thewizrd.wearsettings

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SharedModule
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.FileLoggingTree
import com.thewizrd.shared_resources.utils.Logger
import kotlinx.coroutines.cancel
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application(), Application.ActivityLifecycleCallbacks {
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
            override val isPhone = true
        }

        sharedDeps = object : SharedModule() {
            override val context = appLib.context // keep same context as applib
        }

        if (!BuildConfig.DEBUG) {
            Logger.registerLogger(FileLoggingTree(applicationContext))
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }

    override fun onTerminate() {
        // Shutdown logger
        Logger.shutdown()
        appLib.appScope.cancel()
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
        if (activity.localClassName.contains(MainActivity::class.java.simpleName)) {
            applicationState = AppState.CLOSED
        }
    }
}