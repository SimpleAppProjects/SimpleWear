package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.annotation.Size
import com.google.firebase.analytics.FirebaseAnalytics
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.sharedDeps
import java.lang.System.lineSeparator

@SuppressLint("MissingPermission")
object AnalyticsLogger {
    private val analytics by lazy { FirebaseAnalytics.getInstance(sharedDeps.context) }

    @JvmOverloads
    @JvmStatic
    fun logEvent(@Size(min = 1L, max = 40L) eventName: String, properties: Bundle? = null) {
        if (BuildConfig.DEBUG) {
            val append = if (properties == null) "" else lineSeparator() + properties.toString()
            Logger.writeLine(Log.INFO, "EVENT | $eventName$append")
        } else {
            // NOTE: Firebase Analytics only supports eventName with alphanumeric characters and underscores
            analytics.logEvent(eventName.replace("[^a-zA-Z0-9]".toRegex(), "_"), properties)
        }
    }

    @JvmStatic
    fun setUserProperty(
        @Size(min = 1L, max = 24L) property: String,
        @Size(max = 36L) value: String?
    ) {
        analytics.setUserProperty(property, value)
    }

    @JvmStatic
    fun setUserProperty(
        @Size(min = 1L, max = 24L) property: String,
        @Size(max = 36L) value: Boolean
    ) {
        analytics.setUserProperty(property, value.toString())
    }

    @JvmStatic
    fun setUserProperty(
        @Size(min = 1L, max = 24L) property: String,
        @Size(max = 36L) value: Number
    ) {
        analytics.setUserProperty(property, value.toString())
    }
}