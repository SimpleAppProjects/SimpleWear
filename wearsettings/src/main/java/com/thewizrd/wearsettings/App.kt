package com.thewizrd.wearsettings

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.material.color.DynamicColors
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.utils.FileLoggingTree
import com.thewizrd.shared_resources.utils.Logger

class App : Application(), ApplicationLib, Application.ActivityLifecycleCallbacks {
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
        if (!BuildConfig.DEBUG) {
            Logger.registerLogger(FileLoggingTree(appContext))
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onTerminate() {
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
}