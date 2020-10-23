package com.thewizrd.simplewear.wearable;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.shared_resources.utils.Logger;

import java.lang.annotation.Retention;
import java.util.Locale;
import java.util.Map;

import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;
import static java.lang.annotation.RetentionPolicy.SOURCE;

public class WearableWorker extends Worker {
    private static final String TAG = "WearableWorker";

    // Actions
    private static final String KEY_ACTION = "action";
    public static final String ACTION_SENDSTATUSUPDATE = "SimpleWear.Droid.action.SEND_STATUS_UPDATE";
    public static final String ACTION_SENDBATTERYUPDATE = "SimpleWear.Droid.action.SEND_BATTERY_UPDATE";
    public static final String ACTION_SENDWIFIUPDATE = "SimpleWear.Droid.action.SEND_WIFI_UPDATE";
    public static final String ACTION_SENDBTUPDATE = "SimpleWear.Droid.action.SEND_BT_UPDATE";
    public static final String ACTION_SENDMOBILEDATAUPDATE = "SimpleWear.Droid.action.SEND_MOBILEDATA_UPDATE";
    public static final String ACTION_SENDACTIONUPDATE = "SimpleWear.Droid.action.SEND_ACTION_UPDATE";
    public static final String ACTION_REQUESTBTDISCOVERABLE = "SimpleWear.Droid.action.REQUEST_BT_DISCOVERABLE";
    public static final String ACTION_SLEEPTIMERINSTALLEDSTATUS = "SimpleWear.Droid.action.SLEEP_TIMER_INSTALLED_STATUS";

    // Extras
    public static final String EXTRA_STATUS = "SimpleWear.Droid.extra.STATUS";
    public static final String EXTRA_ACTION_CHANGED = "SimpleWear.Droid.extra.ACTION_CHANGED";

    @StringDef({
            ACTION_SENDBATTERYUPDATE,
            ACTION_SENDWIFIUPDATE,
            ACTION_SENDBTUPDATE
    })
    @Retention(SOURCE)
    public @interface StatusAction {
    }

    private final Context mContext;
    private WearableManager mWearMgr;

    public WearableWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context.getApplicationContext();
    }

    public static void enqueueAction(@NonNull Context context, @NonNull String intentAction) {
        enqueueAction(context, intentAction, null);
    }

    public static void enqueueAction(@NonNull Context context, @NonNull String intentAction, @Nullable Map<String, Object> inputDataMap) {
        context = context.getApplicationContext();

        switch (intentAction) {
            case ACTION_SENDSTATUSUPDATE:
            case ACTION_REQUESTBTDISCOVERABLE:
            case ACTION_SLEEPTIMERINSTALLEDSTATUS:
            case SleepTimerHelper.ACTION_START_TIMER:
            case SleepTimerHelper.ACTION_CANCEL_TIMER:
                startWork(context, intentAction);
                break;
            case SleepTimerHelper.ACTION_TIME_UPDATED:
                startWork(context, inputDataMap == null ? null :
                        new Data.Builder()
                                .putString(KEY_ACTION, intentAction)
                                .putAll(inputDataMap)
                                .build()
                );
                break;
        }
    }

    public static void sendStatusUpdate(@NonNull Context context) {
        context = context.getApplicationContext();
        startWork(context, ACTION_SENDSTATUSUPDATE);
    }

    public static void sendStatusUpdate(@NonNull Context context, @NonNull @StatusAction String statusAction, @NonNull String status) {
        context = context.getApplicationContext();
        startWork(context, new Data.Builder()
                .putString(KEY_ACTION, statusAction)
                .putString(EXTRA_STATUS, status)
                .build());
    }

    public static void sendStatusUpdate(@NonNull Context context, @NonNull @StatusAction String statusAction, int status) {
        context = context.getApplicationContext();
        startWork(context, new Data.Builder()
                .putString(KEY_ACTION, statusAction)
                .putInt(EXTRA_STATUS, status)
                .build());
    }

    public static void sendActionUpdate(@NonNull Context context, @NonNull Actions action) {
        context = context.getApplicationContext();
        startWork(context, new Data.Builder()
                .putString(KEY_ACTION, ACTION_SENDACTIONUPDATE)
                .putInt(EXTRA_ACTION_CHANGED, action.getValue())
                .build());
    }

    private static void startWork(@NonNull Context context, @NonNull String intentAction) {
        startWork(context, new Data.Builder().putString(KEY_ACTION, intentAction).build());
    }

    private static void startWork(@NonNull Context context, @Nullable Data inputData) {
        context = context.getApplicationContext();

        Logger.writeLine(Log.INFO, "%s: Requesting to start work", TAG);

        OneTimeWorkRequest.Builder updateRequest = new OneTimeWorkRequest.Builder(WearableWorker.class);
        String intentAction = null;
        if (inputData != null) {
            intentAction = inputData.getString(KEY_ACTION);
            updateRequest.setInputData(inputData);
        }

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        String.format(Locale.ROOT, "%s:%s_oneTime", TAG, intentAction),
                        ExistingWorkPolicy.REPLACE, updateRequest.build()
                );

        Logger.writeLine(Log.INFO, "%s: One-time work enqueued", TAG);
    }

    @NonNull
    @Override
    public Result doWork() {
        Logger.writeLine(Log.INFO, "%s: Work started", TAG);

        final String intentAction = getInputData().getString(KEY_ACTION);

        Logger.writeLine(Log.INFO, "%s: Action: %s", TAG, intentAction);

        mWearMgr = new WearableManager(mContext);

        if (mWearMgr.isWearNodesAvailable()) {
            if (ACTION_SENDSTATUSUPDATE.equals(intentAction)) {
                // Check if any devices are running and send an update
                mWearMgr.requestWearAppState();
            } else if (ACTION_SENDBATTERYUPDATE.equals(intentAction)) {
                final String jsonData = getInputData().getString(EXTRA_STATUS);
                mWearMgr.sendMessage(null, WearableHelper.BatteryPath, stringToBytes(jsonData));
            } else if (ACTION_SENDWIFIUPDATE.equals(intentAction)) {
                mWearMgr.sendMessage(null, WearableHelper.WifiPath, new byte[]{(byte) getInputData().getInt(EXTRA_STATUS, -1)});
            } else if (ACTION_SENDBTUPDATE.equals(intentAction)) {
                mWearMgr.sendMessage(null, WearableHelper.BluetoothPath, new byte[]{(byte) getInputData().getInt(EXTRA_STATUS, -1)});
            } else if (ACTION_SENDACTIONUPDATE.equals(intentAction)) {
                final Actions action = Actions.valueOf(getInputData().getInt(EXTRA_ACTION_CHANGED, 0));
                mWearMgr.sendActionsUpdate(null, action);
            } else if (ACTION_REQUESTBTDISCOVERABLE.equals(intentAction)) {
                mWearMgr.sendMessage(null, WearableHelper.PingPath, null);
                mWearMgr.sendMessage(null, WearableHelper.BtDiscoverPath, null);
            } else if (SleepTimerHelper.ACTION_START_TIMER.equals(intentAction)) {
                mWearMgr.sendMessage(null, SleepTimerHelper.SleepTimerStartPath, null);
            } else if (SleepTimerHelper.ACTION_CANCEL_TIMER.equals(intentAction)) {
                mWearMgr.sendMessage(null, SleepTimerHelper.SleepTimerStopPath, null);
            } else if (SleepTimerHelper.ACTION_TIME_UPDATED.equals(intentAction)) {
                long timeStartMs = getInputData().getLong(SleepTimerHelper.EXTRA_START_TIME_IN_MS, 0);
                long timeProgMs = getInputData().getLong(SleepTimerHelper.EXTRA_TIME_IN_MS, 0);
                mWearMgr.sendSleepTimerUpdate(null, timeStartMs, timeProgMs);
            }

        }

        return Result.success();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (mWearMgr != null) {
            mWearMgr.unregister();
            mWearMgr = null;
        }
    }
}
