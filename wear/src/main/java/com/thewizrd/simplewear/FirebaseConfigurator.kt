package com.thewizrd.simplewear

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.thewizrd.shared_resources.utils.AnalyticsProps
import com.thewizrd.shared_resources.utils.CrashlyticsLoggingTree
import com.thewizrd.shared_resources.utils.Logger

object FirebaseConfigurator {
    @SuppressLint("MissingPermission")
    fun initialize(context: Context) {
        FirebaseAnalytics.getInstance(context).run {
            setUserProperty(AnalyticsProps.DEVICE_TYPE, "watch")
            setUserProperty(AnalyticsProps.PLATFORM, "Android")
        }

        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = true
            sendUnsentReports()
        }

        if (!BuildConfig.DEBUG) {
            Logger.registerLogger(CrashlyticsLoggingTree())
        }

        // Add Firebase RemoteConfig real-time listener
        FirebaseRemoteConfig.getInstance().run {
            addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Logger.verbose("FirebaseConfigurator", "Remote update received")
                    this@run.activate()
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Logger.error(
                        "FirebaseConfigurator",
                        message = "Error on real-time update",
                        t = error
                    )
                }
            })
        }
    }
}