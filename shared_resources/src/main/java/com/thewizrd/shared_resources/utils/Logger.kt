package com.thewizrd.shared_resources.utils

import android.content.Context
import com.thewizrd.shared_resources.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree

object Logger {
    @JvmStatic
    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
            Timber.plant(FileLoggingTree(context.applicationContext))
        } else {
            cleanupLogs(context.applicationContext)
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

    private fun cleanupLogs(context: Context) {
        GlobalScope.launch(Dispatchers.IO) {
            FileUtils.deleteDirectory(context.getExternalFilesDir(null).toString() + "/logs")
        }
    }
}