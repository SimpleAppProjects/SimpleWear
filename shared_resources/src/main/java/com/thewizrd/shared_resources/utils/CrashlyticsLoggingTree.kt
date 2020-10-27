package com.thewizrd.shared_resources.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thewizrd.shared_resources.utils.CrashlyticsLoggingTree
import timber.log.Timber

class CrashlyticsLoggingTree : Timber.Tree() {
    companion object {
        private const val KEY_PRIORITY = "priority"
        private const val KEY_TAG = "tag"
        private const val KEY_MESSAGE = "message"
        private val TAG = CrashlyticsLoggingTree::class.java.simpleName
    }

    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val priorityTAG: String = when (priority) {
                Log.DEBUG -> "DEBUG"
                Log.INFO -> "INFO"
                Log.VERBOSE -> "VERBOSE"
                Log.WARN -> "WARN"
                Log.ERROR -> "ERROR"
                else -> "DEBUG"
            }
            crashlytics.setCustomKey(KEY_PRIORITY, priorityTAG)
            crashlytics.setCustomKey(KEY_TAG, tag!!)
            crashlytics.setCustomKey(KEY_MESSAGE, message)
            if (t != null) {
                crashlytics.recordException(t)
            } else {
                crashlytics.log(String.format("%s/%s: %s", priority, tag, message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging into file : $e")
        }
    }
}