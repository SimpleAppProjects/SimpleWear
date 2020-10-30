package com.thewizrd.simplewear

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.ApplicationLib
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.utils.Logger.init
import com.thewizrd.shared_resources.utils.Logger.shutdown
import com.thewizrd.shared_resources.utils.Logger.writeLine
import kotlin.system.exitProcess

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
    private var mActivitiesStarted = 0
    override val isPhone: Boolean = false

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this

        registerActivityLifecycleCallbacks(this)
        appState = AppState.CLOSED
        mActivitiesStarted = 0

        // Init shared library
        SimpleLibrary.init(this)

        // Start logger
        init(appContext)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().sendUnsentReports()

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            writeLine(Log.ERROR, e)
            if (oldHandler != null) oldHandler.uncaughtException(t, e) else exitProcess(2)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Shutdown logger
        shutdown()
        SimpleLibrary.unRegister()
        instance = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        mActivitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is WearableListenerActivity && appState !== AppState.FOREGROUND) {
            appState = AppState.FOREGROUND
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        mActivitiesStarted--
        if (mActivitiesStarted == 0) appState = AppState.BACKGROUND
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity.localClassName.contains("DashboardActivity")) {
            appState = AppState.CLOSED
        }
    }
}