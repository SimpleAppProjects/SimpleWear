package com.thewizrd.simplewear.wearable;

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
import android.util.TypedValue;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.MultiChoiceAction;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.ValueAction;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.shared_resources.tasks.AsyncTask;
import com.thewizrd.shared_resources.utils.ImageUtils;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.helpers.PhoneStatusHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class WearableManager implements CapabilityClient.OnCapabilityChangedListener {
    private final Context mContext;

    private Collection<Node> mWearNodesWithApp;

    public WearableManager(@NonNull Context context) {
        this.mContext = context;
        init();
    }

    private void init() {
        CapabilityClient mCapabilityClient = Wearable.getCapabilityClient(mContext);
        mCapabilityClient.addListener(this, WearableHelper.CAPABILITY_WEAR_APP);
    }

    public void unregister() {
        CapabilityClient mCapabilityClient = Wearable.getCapabilityClient(mContext);
        mCapabilityClient.removeListener(this);
    }

    @WorkerThread
    public boolean isWearNodesAvailable() {
        if (mWearNodesWithApp == null) {
            mWearNodesWithApp = findWearDevicesWithApp();
        }
        return mWearNodesWithApp != null && !mWearNodesWithApp.isEmpty();
    }

    @Override
    public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
        mWearNodesWithApp = capabilityInfo.getNodes();
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                requestWearAppState();
            }
        });
    }

    @WorkerThread
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

    @WorkerThread
    public void startMusicPlayer(String nodeID, String pkgName, String activityName, boolean playMusic) {
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

                if (playMusic) {
                    // Give the system enough time to start the app
                    try {
                        Thread.sleep(4500);
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

                    if (playMusic) {
                        // Give the system enough time to start the app
                        try {
                            Thread.sleep(4500);
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
    }

    @WorkerThread
    public void sendSupportedMusicPlayers() {
        List<ResolveInfo> infos = mContext.getPackageManager().queryBroadcastReceivers(
                new Intent(Intent.ACTION_MEDIA_BUTTON), PackageManager.GET_RESOLVED_FILTER);

        PutDataMapRequest mapRequest = PutDataMapRequest.create(WearableHelper.MusicPlayersPath);
        ArrayList<String> supportedPlayers = new ArrayList<>();

        for (final ResolveInfo info : infos) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                ResolveInfo activityInfo = mContext.getPackageManager().resolveActivity(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);

                if (activityInfo == null) return;

                ComponentName activityCmpName = new ComponentName(appInfo.packageName, activityInfo.activityInfo.name);
                String key = String.format("%s/%s", appInfo.packageName, activityInfo.activityInfo.name);

                if (!supportedPlayers.contains(key)) {
                    String label = mContext.getPackageManager().getApplicationLabel(appInfo).toString();

                    Bitmap iconBmp = null;
                    try {
                        Drawable iconDrwble = mContext.getPackageManager().getActivityIcon(activityCmpName);
                        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, mContext.getResources().getDisplayMetrics());
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size);
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }

                    DataMap map = new DataMap();
                    map.putString(WearableHelper.KEY_LABEL, label);
                    map.putString(WearableHelper.KEY_PKGNAME, appInfo.packageName);
                    map.putString(WearableHelper.KEY_ACTIVITYNAME, activityInfo.activityInfo.name);
                    map.putAsset(WearableHelper.KEY_ICON, iconBmp != null ? ImageUtils.createAssetFromBitmap(iconBmp) : null);

                    mapRequest.getDataMap().putDataMap(key, map);
                    supportedPlayers.add(key);
                }
            }
        }

        mapRequest.getDataMap().putStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS, supportedPlayers);
        mapRequest.setUrgent();

        try {
            Tasks.await(Wearable.getDataClient(mContext).putDataItem(mapRequest.asPutDataRequest()));
        } catch (ExecutionException | InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }

    @WorkerThread
    public void requestWearAppState() {
        if (mWearNodesWithApp == null) return;

        for (Node node : mWearNodesWithApp) {
            sendMessage(node.getId(), WearableHelper.AppStatePath, null);
        }
    }

    @WorkerThread
    public void sendStatusUpdate(String nodeID, String path) {
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

    public void sendActionsUpdate(final String nodeID) {
        for (final Actions act : Actions.values()) {
            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    sendActionsUpdate(nodeID, act);
                }
            });
        }
    }

    @WorkerThread
    public void sendActionsUpdate(final String nodeID, final Actions act) {
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

    @WorkerThread
    public void performAction(String nodeID, Action action) {
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

    @WorkerThread
    public void sendSleepTimerUpdate(String nodeID, long timeStartInMillis, long timeInMillis) {
        sendMessage(nodeID, SleepTimerHelper.SleepTimerStatusPath,
                stringToBytes(String.format(Locale.ROOT, "%d;%d", timeStartInMillis, timeInMillis)));
    }

    @WorkerThread
    public void sendMessage(String nodeID, @NonNull String path, byte[] data) {
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
}
