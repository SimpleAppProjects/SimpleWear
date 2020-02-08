package com.thewizrd.shared_resources.utils;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

import timber.log.Timber;

public class CrashlyticsLoggingTree extends Timber.Tree {
    private static final String KEY_PRIORITY = "priority";
    private static final String KEY_TAG = "tag";
    private static final String KEY_MESSAGE = "message";

    private static final String TAG = CrashlyticsLoggingTree.class.getSimpleName();

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        try {
            String priorityTAG;
            switch (priority) {
                default:
                case Log.DEBUG:
                    priorityTAG = "DEBUG";
                    break;
                case Log.INFO:
                    priorityTAG = "INFO";
                    break;
                case Log.VERBOSE:
                    priorityTAG = "VERBOSE";
                    break;
                case Log.WARN:
                    priorityTAG = "WARN";
                    break;
                case Log.ERROR:
                    priorityTAG = "ERROR";
                    break;
            }

            Crashlytics.setString(KEY_PRIORITY, priorityTAG);
            Crashlytics.setString(KEY_TAG, tag);
            Crashlytics.setString(KEY_MESSAGE, message);

            if (t != null) {
                Crashlytics.logException(t);
            } else {
                Crashlytics.log(priority, tag, message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while logging into file : " + e);
        }
    }
}
