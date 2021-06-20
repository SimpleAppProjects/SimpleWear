package com.thewizrd.shared_resources.utils

import android.annotation.SuppressLint
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

    private val crashlytics = FirebaseCrashlytics.getInstance()

    @SuppressLint("LogNotTimber")
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
            tag?.let { crashlytics.setCustomKey(KEY_TAG, it) }
            crashlytics.setCustomKey(KEY_MESSAGE, message)

            if (tag != null) {
                crashlytics.log(String.format("%s/%s: %s", priorityTAG, tag, message))
            } else {
                crashlytics.log(String.format("%s/%s", priorityTAG, message))

            }

            if (t != null) {
                crashlytics.recordException(t)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging : $e")
        }
    }
}