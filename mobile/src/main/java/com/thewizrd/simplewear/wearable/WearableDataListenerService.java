package com.thewizrd.simplewear.wearable;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.companion.CompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.util.Pair;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.AppState;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.MultiChoiceAction;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.ValueAction;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.ImageUtils;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.App;
import com.thewizrd.simplewear.MainActivity;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.helpers.PhoneStatusHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class WearableDataListenerService extends WearableListenerService {
    private static final String TAG = "WearableDataListenerService";

    // Actions
    public static final String ACTION_SENDSTATUSUPDATE = "SimpleWear.Droid.action.SEND_STATUS_UPDATE";
    public static final String ACTION_SENDBATTERYUPDATE = "SimpleWear.Droid.action.SEND_BATTERY_UPDATE";
    public static final String ACTION_SENDWIFIUPDATE = "SimpleWear.Droid.action.SEND_WIFI_UPDATE";
    public static final String ACTION_SENDBTUPDATE = "SimpleWear.Droid.action.SEND_BT_UPDATE";
    public static final String ACTION_SENDMOBILEDATAUPDATE = "SimpleWear.Droid.action.SEND_MOBILEDATA_UPDATE";
    public static final String ACTION_SENDACTIONUPDATE = "SimpleWear.Droid.action.SEND_ACTION_UPDATE";
    public static final String ACTION_REQUESTBTDISCOVERABLE = "SimpleWear.Droid.action.REQUEST_BT_DISCOVERABLE";
    public static final String ACTION_GETCONNECTEDNODE = "SimpleWear.Droid.action.GET_CONNECTED_NODE";

    // Extras
    public static final String EXTRA_STATUS = "SimpleWear.Droid.extra.STATUS";
    public static final String EXTRA_ACTION_CHANGED = "SimpleWear.Droid.extra.ACTION_CHANGED";
    public static final String EXTRA_NODEDEVICENAME = "SimpleWear.Droid.extra.NODE_DEVICE_NAME";

    private Context mContext;

    private Collection<Node> mWearNodesWithApp;

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
                        .setContentTitle(context.getString(R.string.not_title_wearservice))
                        .setProgress(0, 0, true)
                        .setColor(context.getColor(R.color.colorPrimary))
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationManager.IMPORTANCE_LOW);

        return mBuilder.build();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this.getApplicationContext();

        Logger.writeLine(Log.INFO, "%s: onCreate", TAG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel();
            startForeground(JOB_ID, getForegroundNotification());
        }

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                mWearNodesWithApp = findWearDevicesWithApp();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        Logger.writeLine(Log.INFO, "%s: onDestroy", TAG);

        super.onDestroy();
    }

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(WearableHelper.StartActivityPath)) {
            Intent startIntent = new Intent(mContext, MainActivity.class)
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
            final String jsonData = bytesToString(messageEvent.getData());

            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    Action action = JSONParser.deserializer(jsonData, Action.class);
                    performAction(messageEvent.getSourceNodeId(), action);
                }
            });
        } else if (messageEvent.getPath().startsWith(WearableHelper.UpdatePath)) {
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    sendStatusUpdate(messageEvent.getSourceNodeId(), null);
                    sendActionsUpdate(messageEvent.getSourceNodeId());
                }
            });
        } else if (messageEvent.getPath().equals(WearableHelper.AppStatePath)) {
            AppState wearAppState = AppState.valueOf(bytesToString(messageEvent.getData()));
            if (wearAppState == AppState.FOREGROUND) {
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        sendStatusUpdate(messageEvent.getSourceNodeId(), null);
                        sendActionsUpdate(messageEvent.getSourceNodeId());
                    }
                });
            }
        } else if (messageEvent.getPath().startsWith(WearableHelper.MusicPlayersPath)) {
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    sendSupportedMusicPlayers();
                }
            });
        } else if (messageEvent.getPath().equals(WearableHelper.PlayCommandPath)) {
            final String jsonData = bytesToString(messageEvent.getData());
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    Pair pair = JSONParser.deserializer(jsonData, Pair.class);
                    String pkgName = pair.first != null ? pair.first.toString() : null;
                    String activityName = pair.second != null ? pair.second.toString() : null;

                    startMusicPlayer(messageEvent.getSourceNodeId(), pkgName, activityName);
                }
            });
        } else if (messageEvent.getPath().equals(WearableHelper.BtDiscoverPath)) {
            String deviceName = bytesToString(messageEvent.getData());
            LocalBroadcastManager.getInstance(mContext)
                    .sendBroadcast(new Intent(ACTION_GETCONNECTEDNODE)
                            .putExtra(EXTRA_NODEDEVICENAME, deviceName));
        }
    }

    private void startMusicPlayer(String nodeID, String pkgName, String activityName) {
        if (!StringUtils.isNullOrWhitespace(pkgName) && !StringUtils.isNullOrWhitespace(activityName)) {
            Intent appIntent = new Intent();
            appIntent.setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_MUSIC)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(new ComponentName(pkgName, activityName));

            // Check if the app has a registered MediaButton BroadcastReceiver
            List<ResolveInfo> infos = mContext.getPackageManager().queryBroadcastReceivers(
                    new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkgName), PackageManager.GET_RESOLVED_FILTER);
            Intent playKeyIntent = null;

            for (ResolveInfo info : infos) {
                if (pkgName.equals(info.activityInfo.packageName)) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);

                    playKeyIntent = new Intent();
                    playKeyIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);
                    playKeyIntent.setAction(Intent.ACTION_MEDIA_BUTTON)
                            .setComponent(new ComponentName(pkgName, info.activityInfo.name));
                    break;
                }
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                mContext.startActivity(appIntent);

                // Give the system enough time to start the app
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }

                // If the app has a registered MediaButton Broadcast receiver,
                // Send the media keyevent directly to the app; Otherwise, send
                // a broadcast to the most recent music session, which should be the
                // app activity we just started
                if (playKeyIntent != null) {
                    sendMessage(nodeID, WearableHelper.PlayCommandPath, stringToBytes(PhoneStatusHelper.sendPlayMusicCommand(mContext, playKeyIntent).name()));
                } else {
                    sendMessage(nodeID, WearableHelper.PlayCommandPath, stringToBytes(PhoneStatusHelper.sendPlayMusicCommand(mContext).name()));
                }
            } else { // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                CompanionDeviceManager deviceManager = (CompanionDeviceManager) mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE);
                List<String> associated_devices = deviceManager.getAssociations();

                if (associated_devices.isEmpty()) {
                    // No devices associated; send message to user
                    sendMessage(nodeID, WearableHelper.PlayCommandPath, stringToBytes(ActionStatus.PERMISSION_DENIED.name()));
                } else {
                    mContext.startActivity(appIntent);

                    // Give the system enough time to start the app
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }

                    // If the app has a registered MediaButton Broadcast receiver,
                    // Send the media keyevent directly to the app; Otherwise, send
                    // a broadcast to the most recent music session, which should be the
                    // app activity we just started
                    if (playKeyIntent != null) {
                        sendMessage(nodeID, WearableHelper.PlayCommandPath, stringToBytes(PhoneStatusHelper.sendPlayMusicCommand(mContext, playKeyIntent).name()));
                    } else {
                        sendMessage(nodeID, WearableHelper.PlayCommandPath, stringToBytes(PhoneStatusHelper.sendPlayMusicCommand(mContext).name()));
                    }
                }
            }
        }
    }

    @Override
    public void onCapabilityChanged(final CapabilityInfo capabilityInfo) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                mWearNodesWithApp = capabilityInfo.getNodes();
                requestWearAppState();
            }
        });
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForeground(JOB_ID, getForegroundNotification());

        try {
            AsyncTask.awaitTask(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    if (intent != null) {
                        if (ACTION_SENDSTATUSUPDATE.equals(intent.getAction())) {
                            // Check if any devices are running and send an update
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    requestWearAppState();
                                }
                            });
                        } else if (ACTION_SENDBATTERYUPDATE.equals(intent.getAction())) {
                            final String jsonData = intent.getStringExtra(EXTRA_STATUS);
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendMessage(null, WearableHelper.BatteryPath, stringToBytes(jsonData));
                                }
                            });
                        } else if (ACTION_SENDWIFIUPDATE.equals(intent.getAction())) {
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendMessage(null, WearableHelper.WifiPath, new byte[]{(byte) intent.getIntExtra(EXTRA_STATUS, -1)});
                                }
                            });
                        } else if (ACTION_SENDBTUPDATE.equals(intent.getAction())) {
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendMessage(null, WearableHelper.BluetoothPath, new byte[]{(byte) intent.getIntExtra(EXTRA_STATUS, -1)});
                                }
                            });
                        } else if (ACTION_SENDMOBILEDATAUPDATE.equals(intent.getAction())) {
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendActionsUpdate(null, Actions.MOBILEDATA);
                                }
                            });
                        } else if (ACTION_SENDACTIONUPDATE.equals(intent.getAction())) {
                            final Actions action = (Actions) intent.getSerializableExtra(EXTRA_ACTION_CHANGED);
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendActionsUpdate(null, action);
                                }
                            });
                        } else if (ACTION_REQUESTBTDISCOVERABLE.equals(intent.getAction())) {
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    sendMessage(null, WearableHelper.PingPath, null);
                                    sendMessage(null, WearableHelper.BtDiscoverPath, null);
                                }
                            });
                        }

                        Logger.writeLine(Log.INFO, "%s: Intent Action: %s", TAG, intent.getAction());
                    }
                    return null;
                }
            });
        } catch (ExecutionException ex) {
            Logger.writeLine(Log.ERROR, ex.getCause());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            stopForeground(true);

        return super.onStartCommand(intent, flags, startId);
    }

    private Collection<Node> findWearDevicesWithApp() {
        CapabilityInfo capabilityInfo = null;

        try {
            capabilityInfo = Tasks.await(Wearable.getCapabilityClient(mContext)
                    .getCapability(WearableHelper.CAPABILITY_WEAR_APP, CapabilityClient.FILTER_REACHABLE));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }

        if (capabilityInfo != null) {
            return capabilityInfo.getNodes();
        }

        return null;
    }

    private void requestWearAppState() {
        if (mWearNodesWithApp == null) return;

        for (Node node : mWearNodesWithApp) {
            sendMessage(node.getId(), WearableHelper.AppStatePath, null);
        }
    }

    private void sendStatusUpdate(String nodeID, String path) {
        if (path != null && path.contains(WearableHelper.WifiPath)) {
            sendMessage(nodeID, path, new byte[]{(byte) PhoneStatusHelper.getWifiState(mContext)});
        } else if (path != null && path.contains(WearableHelper.BatteryPath)) {
            sendMessage(nodeID, path, stringToBytes(JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(mContext), BatteryStatus.class)));
        } else if (path == null || WearableHelper.StatusPath.equals(path)) {
            // Status dump
            sendMessage(nodeID, WearableHelper.WifiPath, new byte[]{(byte) PhoneStatusHelper.getWifiState(mContext)});
            sendMessage(nodeID, WearableHelper.BatteryPath, stringToBytes(JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(mContext), BatteryStatus.class)));
        }
    }

    private void sendActionsUpdate(final String nodeID) {
        for (final Actions act : Actions.values()) {
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    sendActionsUpdate(nodeID, act);
                }
            });
        }
    }

    private void sendActionsUpdate(final String nodeID, final Actions act) {
        Action action;
        switch (act) {
            case WIFI:
                action = new ToggleAction(act, PhoneStatusHelper.isWifiEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case BLUETOOTH:
                action = new ToggleAction(act, PhoneStatusHelper.isBluetoothEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case MOBILEDATA:
                action = new ToggleAction(act, PhoneStatusHelper.isMobileDataEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case LOCATION:
                action = new ToggleAction(act, PhoneStatusHelper.isLocationEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case TORCH:
                action = new ToggleAction(act, PhoneStatusHelper.isTorchEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case LOCKSCREEN:
            case VOLUME:
                // No-op since status is not needed
                break;
            case DONOTDISTURB:
                action = new MultiChoiceAction(act, PhoneStatusHelper.getDNDState(mContext).getValue());
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
            case RINGER:
                action = new MultiChoiceAction(act, PhoneStatusHelper.getRingerState(mContext).getValue());
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(action, Action.class)));
                break;
        }
    }

    private void performAction(String nodeID, Action action) {
        ToggleAction tA;
        NormalAction nA;
        ValueAction vA;
        MultiChoiceAction mA;
        switch (action.getAction()) {
            case WIFI:
                tA = (ToggleAction) action;
                tA.setActionSuccessful(PhoneStatusHelper.setWifiEnabled(mContext, tA.isEnabled()));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(tA, Action.class)));
                break;
            case BLUETOOTH:
                tA = (ToggleAction) action;
                tA.setActionSuccessful(PhoneStatusHelper.setBluetoothEnabled(mContext, tA.isEnabled()));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(tA, Action.class)));
                break;
            case MOBILEDATA:
                tA = (ToggleAction) action;
                tA.setEnabled(PhoneStatusHelper.isMobileDataEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(tA, Action.class)));
                break;
            case LOCATION:
                tA = (ToggleAction) action;
                tA.setEnabled(PhoneStatusHelper.isLocationEnabled(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(tA, Action.class)));
                break;
            case TORCH:
                tA = (ToggleAction) action;
                tA.setActionSuccessful(PhoneStatusHelper.setTorchEnabled(mContext, tA.isEnabled()));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(tA, Action.class)));
                break;
            case LOCKSCREEN:
                nA = (NormalAction) action;
                nA.setActionSuccessful(PhoneStatusHelper.lockScreen(mContext));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(nA, Action.class)));
                break;
            case VOLUME:
                vA = (ValueAction) action;
                vA.setActionSuccessful(PhoneStatusHelper.setVolume(mContext, vA.getDirection()));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(vA, Action.class)));
                break;
            case DONOTDISTURB:
                mA = (MultiChoiceAction) action;
                mA.setActionSuccessful(PhoneStatusHelper.setDNDState(mContext, DNDChoice.valueOf(mA.getChoice())));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(mA, Action.class)));
                break;
            case RINGER:
                mA = (MultiChoiceAction) action;
                mA.setActionSuccessful(PhoneStatusHelper.setRingerState(mContext, RingerChoice.valueOf(mA.getChoice())));
                sendMessage(nodeID, WearableHelper.ActionsPath, stringToBytes(JSONParser.serializer(mA, Action.class)));
                break;
        }
    }

    private void sendMessage(String nodeID, @NonNull String path, byte[] data) {
        if (nodeID == null) {
            if (mWearNodesWithApp == null) {
                // Create requests if nodes exist with app support
                mWearNodesWithApp = findWearDevicesWithApp();

                if (mWearNodesWithApp == null || mWearNodesWithApp.size() == 0)
                    return;
            }
        }

        if (nodeID != null) {
            try {
                Tasks.await(Wearable.getMessageClient(mContext).sendMessage(nodeID, path, data));
            } catch (ExecutionException | InterruptedException e) {
                Logger.writeLine(Log.ERROR, e);
            }
        } else {
            for (Node node : mWearNodesWithApp) {
                try {
                    Tasks.await(Wearable.getMessageClient(mContext).sendMessage(node.getId(), path, data));
                } catch (ExecutionException | InterruptedException e) {
                    Logger.writeLine(Log.ERROR, e);
                }
            }
        }
    }

    private void sendSupportedMusicPlayers() {
        List<ResolveInfo> infos = mContext.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), PackageManager.GET_RESOLVED_FILTER);

        PutDataMapRequest mapRequest = PutDataMapRequest.create(WearableHelper.MusicPlayersPath);
        ArrayList<String> supportedPlayers = new ArrayList<>();

        for (final ResolveInfo info : infos) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            ComponentName activityCmpName = new ComponentName(appInfo.packageName, info.activityInfo.name);

            String key = String.format("%s/%s", appInfo.packageName, info.activityInfo.name);
            String label = mContext.getPackageManager().getApplicationLabel(appInfo).toString();

            Bitmap iconBmp = null;
            try {
                Drawable iconDrwble = mContext.getPackageManager().getActivityIcon(activityCmpName);
                iconBmp = ImageUtils.bitmapFromDrawable(mContext, iconDrwble);
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            DataMap map = new DataMap();
            map.putString(WearableHelper.KEY_LABEL, label);
            map.putString(WearableHelper.KEY_PKGNAME, appInfo.packageName);
            map.putString(WearableHelper.KEY_ACTIVITYNAME, info.activityInfo.name);
            map.putAsset(WearableHelper.KEY_ICON, iconBmp != null ? ImageUtils.createAssetFromBitmap(iconBmp) : null);

            mapRequest.getDataMap().putDataMap(key, map);
            supportedPlayers.add(key);
        }

        mapRequest.getDataMap().putStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS, supportedPlayers);
        mapRequest.setUrgent();

        try {
            Tasks.await(Wearable.getDataClient(mContext).putDataItem(mapRequest.asPutDataRequest()));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
