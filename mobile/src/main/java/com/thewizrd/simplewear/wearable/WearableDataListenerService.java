package com.thewizrd.simplewear.wearable;

import android.content.Intent;

import androidx.core.util.Pair;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.AppState;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.simplewear.MainActivity;

import static com.thewizrd.shared_resources.utils.SerializationUtils.booleanToBytes;
import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToInt;
import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;

public class WearableDataListenerService extends WearableListenerService {
    private static final String TAG = "WearableDataListenerService";

    public static final String ACTION_GETCONNECTEDNODE = "SimpleWear.Droid.action.GET_CONNECTED_NODE";
    public static final String EXTRA_NODEDEVICENAME = "SimpleWear.Droid.extra.NODE_DEVICE_NAME";

    private static final int JOB_ID = 1000;

    private WearableManager mWearMgr;

    @Override
    public void onCreate() {
        super.onCreate();
        mWearMgr = new WearableManager(this);
    }

    @Override
    public void onDestroy() {
        mWearMgr.unregister();
        mWearMgr = null;
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(WearableHelper.StartActivityPath)) {
            Intent startIntent = new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(startIntent);
        } else if (messageEvent.getPath().startsWith(WearableHelper.ActionsPath)) {
            final String jsonData = bytesToString(messageEvent.getData());

            Action action = JSONParser.deserializer(jsonData, Action.class);
            mWearMgr.performAction(messageEvent.getSourceNodeId(), action);
        } else if (messageEvent.getPath().startsWith(WearableHelper.UpdatePath)) {
            mWearMgr.sendStatusUpdate(messageEvent.getSourceNodeId(), null);
            mWearMgr.sendActionsUpdate(messageEvent.getSourceNodeId());
        } else if (messageEvent.getPath().equals(WearableHelper.AppStatePath)) {
            AppState wearAppState = AppState.valueOf(bytesToString(messageEvent.getData()));
            if (wearAppState == AppState.FOREGROUND) {
                mWearMgr.sendStatusUpdate(messageEvent.getSourceNodeId(), null);
                mWearMgr.sendActionsUpdate(messageEvent.getSourceNodeId());
            }
        } else if (messageEvent.getPath().startsWith(WearableHelper.MusicPlayersPath)) {
            mWearMgr.sendSupportedMusicPlayers();
        } else if (messageEvent.getPath().equals(WearableHelper.OpenMusicPlayerPath)) {
            final String jsonData = bytesToString(messageEvent.getData());

            Pair pair = JSONParser.deserializer(jsonData, Pair.class);
            String pkgName = pair.first != null ? pair.first.toString() : null;
            String activityName = pair.second != null ? pair.second.toString() : null;

            mWearMgr.startMusicPlayer(messageEvent.getSourceNodeId(), pkgName, activityName, false);
        } else if (messageEvent.getPath().equals(WearableHelper.PlayCommandPath)) {
            final String jsonData = bytesToString(messageEvent.getData());

            Pair pair = JSONParser.deserializer(jsonData, Pair.class);
            String pkgName = pair.first != null ? pair.first.toString() : null;
            String activityName = pair.second != null ? pair.second.toString() : null;

            mWearMgr.startMusicPlayer(messageEvent.getSourceNodeId(), pkgName, activityName, true);
        } else if (messageEvent.getPath().equals(WearableHelper.BtDiscoverPath)) {
            String deviceName = bytesToString(messageEvent.getData());
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(ACTION_GETCONNECTEDNODE)
                            .putExtra(EXTRA_NODEDEVICENAME, deviceName));
        } else if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerEnabledPath)) {
            mWearMgr.sendMessage(messageEvent.getSourceNodeId(), SleepTimerHelper.SleepTimerEnabledPath,
                    booleanToBytes(SleepTimerHelper.isSleepTimerInstalled()));
        } else if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStartPath)) {
            int timeInMins = bytesToInt(messageEvent.getData());
            startSleepTimer(timeInMins);
        } else if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStopPath)) {
            stopSleepTimer();
        } else if (messageEvent.getPath().startsWith(WearableHelper.StatusPath)) {
            mWearMgr.sendStatusUpdate(messageEvent.getSourceNodeId(), messageEvent.getPath());
        }
    }

    private void startSleepTimer(int timeInMins) {
        Intent startTimerIntent = new Intent(SleepTimerHelper.ACTION_START_TIMER)
                .setClassName(SleepTimerHelper.getPackageName(), SleepTimerHelper.PACKAGE_NAME + ".services.TimerService")
                .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, timeInMins);
        startService(startTimerIntent);
    }

    private void stopSleepTimer() {
        Intent stopTimerIntent = new Intent(SleepTimerHelper.ACTION_CANCEL_TIMER)
                .setClassName(SleepTimerHelper.getPackageName(), SleepTimerHelper.PACKAGE_NAME + ".services.TimerService");
        startService(stopTimerIntent);
    }
}
