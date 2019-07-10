package com.thewizrd.simplewear.wearable;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.phone.PhoneDeviceType;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.thewizrd.shared_resources.AppState;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.App;
import com.thewizrd.simplewear.R;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class WearableDataListenerService extends WearableListenerService {
    private static final String TAG = "WearableDataListenerService";

    // Actions
    public static final String ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE";
    public static final String ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING";
    public static final String ACTION_UPDATECONNECTIONSTATUS = "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS";

    // Extras
    public static final String EXTRA_SUCCESS = "SimpleWear.Droid.Wear.extra.SUCCESS";
    public static final String EXTRA_STATUS = "SimpleWear.Droid.Wear.extra.STATUS";
    public static final String EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS";

    private Node mPhoneNodeWithApp;
    private WearConnectionStatus mConnectionStatus = WearConnectionStatus.DISCONNECTED;
    private boolean mLoaded = false;
    private Handler mMainHandler;

    private static final int JOB_ID = 1000;
    private static final String NOT_CHANNEL_ID = "SimpleWear.generalnotif";

    public static void enqueueWork(Context context, Intent work) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(work);
        } else {
            context.startService(work);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static void initChannel() {
        // Gets an instance of the NotificationManager service
        Context context = App.getInstance().getAppContext();
        NotificationManager mNotifyMgr = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel mChannel = mNotifyMgr.getNotificationChannel(NOT_CHANNEL_ID);

        if (mChannel == null) {
            String notchannel_name = context.getResources().getString(R.string.not_channel_name_general);

            mChannel = new NotificationChannel(NOT_CHANNEL_ID, notchannel_name, NotificationManager.IMPORTANCE_LOW);
            // Configure the notification channel.
            mChannel.setShowBadge(false);
            mChannel.enableLights(false);
            mChannel.enableVibration(false);
            mNotifyMgr.createNotificationChannel(mChannel);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static Notification getForegroundNotification() {
        Context context = App.getInstance().getAppContext();
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, NOT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_icon)
                        .setContentTitle("Syncing...")
                        .setProgress(0, 0, true)
                        .setColor(context.getColor(R.color.colorPrimary))
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationManager.IMPORTANCE_LOW);

        return mBuilder.build();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel();

            startForeground(JOB_ID, getForegroundNotification());
        }

        mMainHandler = new Handler(Looper.getMainLooper());

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                checkConnectionStatus();

                LocalBroadcastManager.getInstance(WearableDataListenerService.this)
                        .sendBroadcast(new Intent(ACTION_UPDATECONNECTIONSTATUS)
                                .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.getValue()));

                mLoaded = true;
            }
        });

        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.writeLine(Log.ERROR, e, "SimpleWear: %s: UncaughtException", TAG);

                if (oldHandler != null) {
                    oldHandler.uncaughtException(t, e);
                } else {
                    System.exit(2);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        mLoaded = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().contains(WearableHelper.WifiPath)) {
            byte[] data = messageEvent.getData();
            int wifiStatus = data[0];
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(WearableHelper.WifiPath)
                            .putExtra(EXTRA_STATUS, wifiStatus));
        } else if (messageEvent.getPath().equals(WearableHelper.BatteryPath)) {
            byte[] data = messageEvent.getData();
            String jsonData = bytesToString(data);
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(WearableHelper.BatteryPath)
                            .putExtra(EXTRA_STATUS, jsonData));
        } else if (messageEvent.getPath().equals(WearableHelper.AppStatePath)) {
            AppState appState = App.getInstance().getAppState();
            sendMessage(messageEvent.getSourceNodeId(), messageEvent.getPath(), stringToBytes(appState.name()));
        }
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);

        mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.getNodes());

        if (mPhoneNodeWithApp == null) {
            mConnectionStatus = WearConnectionStatus.DISCONNECTED;

            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(WearableDataListenerService.this)
                        .getConnectedNodes());

                if (!nodes.isEmpty())
                    mConnectionStatus = WearConnectionStatus.APPNOTINSTALLED;
            } catch (ExecutionException | InterruptedException e) {
                Logger.writeLine(Log.ERROR, e);
            }
        } else {
            mConnectionStatus = WearConnectionStatus.CONNECTED;
        }

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_UPDATECONNECTIONSTATUS)
                        .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.getValue()));
    }

    private void checkConnectionStatus() {
        mPhoneNodeWithApp = checkIfPhoneHasApp();

        if (mPhoneNodeWithApp == null) {
            mConnectionStatus = WearConnectionStatus.DISCONNECTED;

            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(WearableDataListenerService.this)
                        .getConnectedNodes());

                if (!nodes.isEmpty())
                    mConnectionStatus = WearConnectionStatus.APPNOTINSTALLED;
            } catch (ExecutionException | InterruptedException e) {
                Logger.writeLine(Log.ERROR, e);
            }
        } else {
            mConnectionStatus = WearConnectionStatus.CONNECTED;
        }
    }

    @Override
    public int onStartCommand(@NonNull final Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForeground(JOB_ID, getForegroundNotification());

        Tasks.call(Executors.newSingleThreadExecutor(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (ACTION_OPENONPHONE.equals(intent.getAction())) {
                    openAppOnPhone();
                } else if (ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                    checkConnectionStatus();

                    LocalBroadcastManager.getInstance(WearableDataListenerService.this)
                            .sendBroadcast(new Intent(ACTION_UPDATECONNECTIONSTATUS)
                                    .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.getValue()));
                } else if (WearableHelper.UpdatePath.equals(intent.getAction())) {
                    requestUpdate();
                } else {
                    Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.getAction());
                }
                return null;
            }
        }).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    stopForeground(true);
            }
        });

        return START_NOT_STICKY;
    }

    private Node checkIfPhoneHasApp() {
        Node node = null;

        try {
            CapabilityInfo capabilityInfo = Tasks.await(Wearable.getCapabilityClient(this)
                    .getCapability(WearableHelper.CAPABILITY_PHONE_APP,
                            CapabilityClient.FILTER_REACHABLE));
            node = pickBestNodeId(capabilityInfo.getNodes());
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }

        return node;
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
     */
    private static Node pickBestNodeId(Collection<Node> nodes) {
        Node bestNode = null;

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node;
            }
            bestNode = node;
        }
        return bestNode;
    }

    private boolean connect() {
        return new AsyncTask<Boolean>().await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                if (!mLoaded && mPhoneNodeWithApp == null)
                    mPhoneNodeWithApp = checkIfPhoneHasApp();

                return mPhoneNodeWithApp != null;
            }
        });
    }

    private void openAppOnPhone() {
        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                connect();

                if (mPhoneNodeWithApp == null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WearableDataListenerService.this, "Device is not connected or app is not installed on device...", Toast.LENGTH_SHORT).show();
                        }
                    });

                    int deviceType = PhoneDeviceType.getPhoneDeviceType(WearableDataListenerService.this);
                    switch (deviceType) {
                        case PhoneDeviceType.DEVICE_TYPE_ANDROID:
                            LocalBroadcastManager.getInstance(WearableDataListenerService.this).sendBroadcast(
                                    new Intent(ACTION_SHOWSTORELISTING));
                            break;
                        case PhoneDeviceType.DEVICE_TYPE_IOS:
                        default:
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WearableDataListenerService.this, "Connected device is not supported", Toast.LENGTH_SHORT).show();
                                }
                            });
                            break;
                    }
                } else {
                    // Send message to device to start activity
                    int result = Tasks.await(Wearable.getMessageClient(WearableDataListenerService.this)
                            .sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.StartActivityPath, new byte[0]));

                    LocalBroadcastManager.getInstance(WearableDataListenerService.this)
                            .sendBroadcast(new Intent(ACTION_OPENONPHONE)
                                    .putExtra(EXTRA_SUCCESS, result != -1));
                }
                return null;
            }
        });
    }

    private void requestUpdate() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.UpdatePath, null);
        }
    }

    private void sendMessage(String nodeID, String path, byte[] data) {
        try {
            int ret = Tasks.await(Wearable.getMessageClient(this).sendMessage(nodeID, path, data));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
