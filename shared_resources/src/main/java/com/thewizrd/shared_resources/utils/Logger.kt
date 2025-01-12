package com.thewizrd.shared_resources.utils

import android.content.Context
import android.util.Log
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.appLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree

object Logger {
    @JvmStatic
    internal var DEBUG_MODE_ENABLED = false

    @JvmStatic
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            Timber.plant(FileLoggingTree(context.applicationContext))
        } else {
            cleanupLogs(context.applicationContext)
        }
    }

    fun isDebugLoggerEnabled(): Boolean {
        return Timber.forest().any { it is FileLoggingTree }
    }

    fun enableDebugLogger(context: Context, enable: Boolean) {
        DEBUG_MODE_ENABLED = enable

        if (enable) {
            if (!Timber.forest().any { it is FileLoggingTree }) {
                Timber.plant(FileLoggingTree(context.applicationContext))
            }
        } else {
            Timber.forest().forEach {
                if (it is FileLoggingTree) {
                    Timber.uproot(it)
                }
            }
        }
    }

    @JvmStatic
    fun registerLogger(tree: Timber.Tree) {
        Timber.plant(tree)
    }

    @JvmStatic
    fun shutdown() {
        Timber.uprootAll()
    }

    @JvmStatic
    fun writeLine(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
        Timber.log(priority, t, message, *args)
    }

    @JvmStatic
    fun writeLine(priority: Int, message: String?, vararg args: Any?) {
        Timber.log(priority, message, *args)
    }

    @JvmStatic
    fun writeLine(priority: Int, t: Throwable?) {
        Timber.log(priority, t)
    }

    @JvmStatic
    fun verbose(tag: String, message: String, vararg args: Any?) {
        log(Log.VERBOSE, tag, message = message, args = args)
    }

    @JvmStatic
    fun verbose(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.VERBOSE, tag, t, message, args)
    }

    @JvmStatic
    fun debug(tag: String, message: String, vararg args: Any?) {
        log(Log.DEBUG, tag, message = message, args = args)
    }

    @JvmStatic
    fun debug(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.DEBUG, tag, t, message, args)
    }

    @JvmStatic
    fun info(tag: String, message: String, vararg args: Any?) {
        log(Log.INFO, tag, message = message, args = args)
    }

    @JvmStatic
    fun info(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.INFO, tag, t, message, args)
    }

    @JvmStatic
    fun warn(tag: String, message: String, vararg args: Any?) {
        log(Log.WARN, tag, message = message, args = args)
    }

    @JvmStatic
    fun warn(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.WARN, tag, t, message, args)
    }

    @JvmStatic
    fun error(tag: String, message: String, vararg args: Any?) {
        log(Log.ERROR, tag, message = message, args = args)
    }

    @JvmStatic
    fun error(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.ERROR, tag, t, message, args)
    }

    @JvmStatic
    fun assert(tag: String, message: String, vararg args: Any?) {
        log(Log.ASSERT, tag, message = message, args = args)
    }

    @JvmStatic
    fun assert(tag: String, t: Throwable? = null, message: String? = null, vararg args: Any?) {
        log(Log.ASSERT, tag, t, message, args)
    }

    private fun log(
        priority: Int,
        tag: String,
        t: Throwable? = null,
        message: String? = null,
        vararg args: Any?
    ) {
        Timber.tag(tag).log(priority, t, message, *args)
    }

    private fun cleanupLogs(context: Context) {
        appLib.appScope.launch(Dispatchers.IO) {
            FileUtils.deleteDirectory(context.getExternalFilesDir(null).toString() + "/logs")
        }
    }
}