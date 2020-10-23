package com.thewizrd.shared_resources.utils;

import android.content.Context;

import com.thewizrd.shared_resources.BuildConfig;
import com.thewizrd.shared_resources.tasks.AsyncTask;

import timber.log.Timber;

public class Logger {
    public static void init(Context context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            Timber.plant(new FileLoggingTree(context));
        } else {
            cleanupLogs(context);
            Timber.plant(new CrashlyticsLoggingTree());
        }
    }

    public static void shutdown() {
        Timber.uprootAll();
    }

    public static void writeLine
            (int priority, Throwable t, String message, Object... args) {
        Timber.log(priority, t, message, args);
    }

    public static void writeLine(int priority, String message, Object... args) {
        Timber.log(priority, message, args);
    }

    public static void writeLine(int priority, Throwable t) {
        Timber.log(priority, t);
    }

    private static void cleanupLogs(final Context context) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                FileUtils.deleteDirectory(context.getExternalFilesDir(null) + "/logs");
            }
        });
    }
}