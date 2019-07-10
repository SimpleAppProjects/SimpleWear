package com.thewizrd.simplewear.wearable;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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
import com.thewizrd.shared_resources.BatteryStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.App;
import com.thewizrd.simplewear.MainActivity;
import com.thewizrd.simplewear.PhoneStatusHelper;
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
    public static final String ACTION_SENDSTATUSUPDATE = "SimpleWear.Droid.action.SEND_STATUS_UPDATE";
    public static final String ACTION_SENDBATTERYUPDATE = "SimpleWear.Droid.action.SEND_BATTERY_UPDATE";
    public static final String ACTION_SENDWIFIUPDATE = "SimpleWear.Droid.action.SEND_WIFI_UPDATE";

    // Extras
    public static final String EXTRA_STATUS = "SimpleWear.Droid.extra.STATUS";

    private Collection<Node> mWearNodesWithApp;
    private Collection<Node> mAllConnectedNodes;
    private boolean mLoaded = false;

    private static final int JOB_ID = 1000;
    private static final String NOT_CHANNEL_ID = "SimpleWear.generalnotif";

    public static void enqueueWork(Context context, Intent work) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(work);
        } else {
            context.startService(work);
        }
    }

    private static void initChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                mWearNodesWithApp = findWearDevicesWithApp();
                mAllConnectedNodes = findAllWearDevices();

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
    public void onMessageReceived(final MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(WearableHelper.StartActivityPath)) {
            Intent startIntent = new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(startIntent);
        } else if (messageEvent.getPath().startsWith(WearableHelper.StatusPath)) {
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    sendStatusUpdate(messageEvent.getSourceNodeId(), messageEvent.getPath());
                }
            });
        } else if (messageEvent.getPath().startsWith(WearableHelper.ActionsPath)) {
            //
        } else if (messageEvent.getPath().startsWith(WearableHelper.UpdatePath)) {
            sendStatusUpdate(messageEvent.getSourceNodeId(), null);
        } else if (messageEvent.getPath().equals(WearableHelper.AppStatePath)) {
            AppState wearAppState = AppState.valueOf(bytesToString(messageEvent.getData()));
            if (wearAppState == AppState.FOREGROUND) {
                sendStatusUpdate(messageEvent.getSourceNodeId(), null);
            }
        }
    }

    @Override
    public void onCapabilityChanged(final CapabilityInfo capabilityInfo) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                mWearNodesWithApp = capabilityInfo.getNodes();
                mAllConnectedNodes = findAllWearDevices();
            }
        });
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForeground(JOB_ID, getForegroundNotification());

        Tasks.call(Executors.newSingleThreadExecutor(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (intent != null && ACTION_SENDSTATUSUPDATE.equals(intent.getAction())) {
                    // Check if any devices are running and send an update
                    for (Node node : mWearNodesWithApp) {
                        sendMessage(node.getId(), WearableHelper.AppStatePath, null);
                    }
                    Logger.writeLine(Log.INFO, "%s: Action: %s", TAG, intent.getAction());
                } else if (intent != null && ACTION_SENDBATTERYUPDATE.equals(intent.getAction())) {
                    String jsonData = intent.getStringExtra(EXTRA_STATUS);
                    sendMessage(null, WearableHelper.BatteryPath, stringToBytes(jsonData));
                } else if (intent != null && ACTION_SENDWIFIUPDATE.equals(intent.getAction())) {
                    sendMessage(null, WearableHelper.WifiPath, new byte[]{(byte) intent.getIntExtra(EXTRA_STATUS, -1)});
                } else if (intent != null) {
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

    private Collection<Node> findWearDevicesWithApp() {
        CapabilityInfo capabilityInfo = null;

        try {
            capabilityInfo = Tasks.await(Wearable.getCapabilityClient(this)
                    .getCapability(WearableHelper.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }

        if (capabilityInfo != null) {
            return capabilityInfo.getNodes();
        }

        return null;
    }

    private Collection<Node> findAllWearDevices() {
        List<Node> nodes = null;

        try {
            nodes = Tasks.await(Wearable.getNodeClient(this)
                    .getConnectedNodes());
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }

        return nodes;
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

    private void sendStatusUpdate(@Nullable String nodeID, @Nullable String path) {
        if (nodeID == null) {
            if (mWearNodesWithApp == null) {
                // Create requests if nodes exist with app support
                mWearNodesWithApp = findWearDevicesWithApp();

                if (mWearNodesWithApp == null || mWearNodesWithApp.size() == 0)
                    return;
            }
        }

        if (nodeID == null) {
            Node node = pickBestNodeId(mWearNodesWithApp);
            if (node != null) {
                nodeID = node.getId();
            } else {
                return;
            }
        }

        if (path != null && path.contains(WearableHelper.WifiPath)) {
            sendMessage(nodeID, path, new byte[]{(byte) PhoneStatusHelper.getWifiState(this.getApplicationContext())});
        } else if (path != null && path.contains(WearableHelper.BatteryPath)) {
            sendMessage(nodeID, path, stringToBytes(JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(this), BatteryStatus.class)));
        } else if (path == null || WearableHelper.StatusPath.equals(path)) {
            // Status dump
            sendMessage(nodeID, WearableHelper.WifiPath, new byte[]{(byte) PhoneStatusHelper.getWifiState(this.getApplicationContext())});
            sendMessage(nodeID, WearableHelper.BatteryPath, stringToBytes(JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(this), BatteryStatus.class)));
        }
    }

    private void sendMessage(@Nullable String nodeID, @NonNull String path, byte[] data) {
        if (nodeID == null) {
            if (mWearNodesWithApp == null) {
                // Create requests if nodes exist with app support
                mWearNodesWithApp = findWearDevicesWithApp();

                if (mWearNodesWithApp == null || mWearNodesWithApp.size() == 0)
                    return;
            }
        }

        if (nodeID == null) {
            Node node = pickBestNodeId(mWearNodesWithApp);
            if (node != null) {
                nodeID = node.getId();
            } else {
                return;
            }
        }

        try {
            Tasks.await(Wearable.getMessageClient(this).sendMessage(nodeID, path, data));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
