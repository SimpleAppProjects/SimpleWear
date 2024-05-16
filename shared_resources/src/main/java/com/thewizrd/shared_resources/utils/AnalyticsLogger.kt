package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.annotation.Size
import com.google.firebase.analytics.FirebaseAnalytics
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.SimpleLibrary
import java.lang.System.lineSeparator

@SuppressLint("MissingPermission")
object AnalyticsLogger {
    private val analytics by lazy { FirebaseAnalytics.getInstance(SimpleLibrary.instance.appContext) }

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
}