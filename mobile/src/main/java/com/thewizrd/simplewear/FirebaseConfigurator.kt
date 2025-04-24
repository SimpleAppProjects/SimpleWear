package com.thewizrd.simplewear

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.utils.AnalyticsProps
import com.thewizrd.shared_resources.utils.ContextUtils.isLargeTablet
import com.thewizrd.shared_resources.utils.ContextUtils.isSmallestWidth
import com.thewizrd.shared_resources.utils.ContextUtils.isTv
import com.thewizrd.shared_resources.utils.CrashlyticsLoggingTree
import com.thewizrd.shared_resources.utils.Logger

object FirebaseConfigurator {
    @SuppressLint("MissingPermission")
    fun initialize(context: Context) {
        FirebaseAnalytics.getInstance(context).setUserProperty(
            AnalyticsProps.DEVICE_TYPE, if (context.isTv()) {
                "tv"
            } else if (context.isLargeTablet() || context.isSmallestWidth(600)) {
                "tablet"
            } else {
                "mobile"
            }
        )

        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = true
            sendUnsentReports()
        }

        if (!BuildConfig.DEBUG) {
            Logger.registerLogger(CrashlyticsLoggingTree())
        }
    }
}