package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.utils.CrashlyticsLoggingTree
import com.thewizrd.shared_resources.utils.Logger.DEBUG_MODE_ENABLED
import timber.log.Timber

@SuppressLint("LogNotTimber")
class CrashlyticsLoggingTree : Timber.Tree() {
    companion object {
        private const val KEY_PRIORITY = "priority"
        private const val KEY_TAG = "tag"
        private const val KEY_MESSAGE = "message"

        private val TAG = CrashlyticsLoggingTree::class.java.simpleName
    }

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority > Log.DEBUG || DEBUG_MODE_ENABLED
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val priorityTAG = when (priority) {
                Log.VERBOSE -> "VERBOSE"
                Log.DEBUG -> "DEBUG"
                Log.INFO -> "INFO"
                Log.WARN -> "WARN"
                Log.ERROR -> "ERROR"
                Log.ASSERT -> "ASSERT"
                else -> "DEBUG"
            }

            crashlytics.setCustomKey(KEY_PRIORITY, priorityTAG)
            tag?.let { crashlytics.setCustomKey(KEY_TAG, it) }
            crashlytics.setCustomKey(KEY_MESSAGE, message)

            if (tag != null) {
                crashlytics.log("$priorityTAG | $tag: $message")
            } else {
                crashlytics.log("$priorityTAG | $message")
            }

            if (t != null) {
                crashlytics.recordException(t)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging : $e")
        }
    }
}