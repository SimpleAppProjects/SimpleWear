package com.thewizrd.shared_resources.sleeptimer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.thewizrd.shared_resources.BuildConfig;
import com.thewizrd.shared_resources.SimpleLibrary;

public class SleepTimerHelper {
    // Link to Play Store listing
    public static final String PACKAGE_NAME = "com.thewizrd.simplesleeptimer";
    private static final String PLAY_STORE_APP_URI = "market://details?id=" + PACKAGE_NAME;

    public static Uri getPlayStoreURI() {
        return Uri.parse(PLAY_STORE_APP_URI);
    }

    // For WearableListenerService
    public static final String SleepTimerEnabledPath = "/status/sleeptimer/enabled";
    public static final String SleepTimerStartPath = "/status/sleeptimer/start";
    public static final String SleepTimerStopPath = "/status/sleeptimer/stop";
    public static final String SleepTimerStatusPath = "/status/sleeptimer/status";
    public static final String SleepTimerAudioPlayerPath = "/sleeptimer/audioplayer";

    // For Music Player DataMap
    public static final String KEY_SELECTEDPLAYER = "key_selected_player";

    public static final String ACTION_START_TIMER = "SimpleSleepTimer.action.START_TIMER";
    public static final String ACTION_CANCEL_TIMER = "SimpleSleepTimer.action.CANCEL_TIMER";
    public static final String EXTRA_TIME_IN_MINS = "SimpleSleepTimer.extra.TIME_IN_MINUTES";

    public static final String ACTION_TIME_UPDATED = "SimpleSleepTimer.action.TIME_UPDATED";
    public static final String EXTRA_START_TIME_IN_MS = "SimpleSleepTimer.extra.START_TIME_IN_MS";
    public static final String EXTRA_TIME_IN_MS = "SimpleSleepTimer.extra.TIME_IN_MS";

    public static String getPackageName() {
        String packageName = PACKAGE_NAME;
        if (BuildConfig.DEBUG) packageName += ".debug";
        return packageName;
    }

    public static boolean isSleepTimerInstalled() {
        try {
            Context context = SimpleLibrary.getInstance().getApp().getAppContext();
            return context.getPackageManager().getApplicationInfo(getPackageName(), 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
