package com.thewizrd.simplewear.wearable;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.google.android.clockwork.tiles.TileData;
import com.google.android.clockwork.tiles.TileProviderService;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.MultiChoiceAction;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.LaunchActivity;
import com.thewizrd.simplewear.R;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class DashboardTileProviderService extends TileProviderService
        implements MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private static final String TAG = "DashTileProviderService";

    private Context mContext;
    private boolean mInFocus;
    private int id = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying service...");

        super.onDestroy();
    }

    @Override
    public void onTileUpdate(int tileId) {
        Log.d(TAG, "onTileUpdate called with: tileId = " + tileId);

        if (!isIdForDummyData(tileId)) {
            id = tileId;
            sendRemoteViews();
        }
    }

    @Override
    public void onTileFocus(int tileId) {
        super.onTileFocus(tileId);

        Log.d(TAG, "onTileFocus called with: tileId = " + tileId);
        if (!isIdForDummyData(tileId)) {
            id = tileId;
            mInFocus = true;
            sendRemoteViews();

            Wearable.getCapabilityClient(mContext).addListener(this, WearableHelper.CAPABILITY_PHONE_APP);
            Wearable.getMessageClient(mContext).addListener(this);

            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    checkConnectionStatus();
                    requestUpdate();
                }
            });
        }
    }

    @Override
    public void onTileBlur(int tileId) {
        super.onTileBlur(tileId);

        Log.d(TAG, "onTileBlur called with: tileId = " + tileId);
        if (!isIdForDummyData(tileId)) {
            mInFocus = false;

            Wearable.getCapabilityClient(mContext).removeListener(this, WearableHelper.CAPABILITY_PHONE_APP);
            Wearable.getMessageClient(mContext).removeListener(this);
        }
    }

    private void sendRemoteViews() {
        Log.d(TAG, "sendRemoteViews");
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                RemoteViews updateViews = buildUpdate();

                if (updateViews != null) {
                    TileData tileData = new TileData.Builder()
                            .setRemoteViews(updateViews)
                            .build();

                    sendData(id, tileData);
                }
            }
        });
    }

    private RemoteViews buildUpdate() {
        RemoteViews views;

        if (mConnectionStatus != WearConnectionStatus.CONNECTED) {
            views = new RemoteViews(mContext.getPackageName(), R.layout.tile_disconnected);
            views.setOnClickPendingIntent(R.id.tile, getTapIntent(mContext));
            return views;
        }

        views = new RemoteViews(mContext.getPackageName(), R.layout.tile_layout_dashboard);
        views.setOnClickPendingIntent(R.id.tile, getTapIntent(mContext));

        if (battStatus != null) {
            String battValue = String.format(Locale.ROOT, "%d%%, %s", battStatus.batteryLevel,
                    battStatus.isCharging ? mContext.getString(R.string.batt_state_charging) : mContext.getString(R.string.batt_state_discharging));
            views.setTextViewText(R.id.batt_stat_text, battValue);
        }

        if (wifiAction != null) {
            views.setImageViewResource(R.id.wifi_toggle, wifiAction.isEnabled() ? R.drawable.ic_network_wifi_white_24dp : R.drawable.ic_signal_wifi_off_white_24dp);
            views.setInt(R.id.wifi_toggle, "setBackgroundResource", wifiAction.isEnabled() ? R.drawable.round_button_enabled : R.drawable.round_button_disabled);
            views.setOnClickPendingIntent(R.id.wifi_toggle, getActionClickIntent(mContext, Actions.WIFI));
        }

        if (btAction != null) {
            views.setImageViewResource(R.id.bt_toggle, btAction.isEnabled() ? R.drawable.ic_bluetooth_white_24dp : R.drawable.ic_bluetooth_disabled_white_24dp);
            views.setInt(R.id.bt_toggle, "setBackgroundResource", btAction.isEnabled() ? R.drawable.round_button_enabled : R.drawable.round_button_disabled);
            views.setOnClickPendingIntent(R.id.bt_toggle, getActionClickIntent(mContext, Actions.BLUETOOTH));
        }

        views.setOnClickPendingIntent(R.id.lock_toggle, getActionClickIntent(mContext, Actions.LOCKSCREEN));

        if (dndAction != null) {
            DNDChoice dndChoice = DNDChoice.valueOf(dndAction.getChoice());
            int mDrawableID = R.drawable.ic_do_not_disturb_off_white_24dp;

            switch (dndChoice) {
                case PRIORITY:
                    mDrawableID = R.drawable.ic_error_white_24dp;
                    break;
                case ALARMS:
                    mDrawableID = R.drawable.ic_alarm_white_24dp;
                    break;
                case SILENCE:
                    mDrawableID = R.drawable.ic_notifications_off_white_24dp;
                    break;
            }

            views.setImageViewResource(R.id.dnd_toggle, mDrawableID);
            views.setOnClickPendingIntent(R.id.dnd_toggle, getActionClickIntent(mContext, Actions.DONOTDISTURB));
        }

        if (ringerAction != null) {
            RingerChoice ringerChoice = RingerChoice.valueOf(ringerAction.getChoice());
            int mDrawableID = R.drawable.ic_vibration_white_24dp;

            switch (ringerChoice) {
                case SOUND:
                    mDrawableID = R.drawable.ic_notifications_active_white_24dp;
                    break;
                case SILENT:
                    mDrawableID = R.drawable.ic_volume_off_white_24dp;
                    break;
            }

            views.setImageViewResource(R.id.ringer_toggle, mDrawableID);
            views.setOnClickPendingIntent(R.id.ringer_toggle, getActionClickIntent(mContext, Actions.RINGER));
        }

        if (torchAction != null) {
            views.setInt(R.id.torch_toggle, "setBackgroundResource", torchAction.isEnabled() ? R.drawable.round_button_enabled : R.drawable.round_button_disabled);
            views.setOnClickPendingIntent(R.id.torch_toggle, getActionClickIntent(mContext, Actions.TORCH));
        }

        return views;
    }

    private PendingIntent getTapIntent(Context context) {
        Intent onClickIntent = new Intent(context.getApplicationContext(), LaunchActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(context, 0, onClickIntent, 0);
    }

    private PendingIntent getActionClickIntent(Context context, Actions action) {
        Intent onClickIntent = new Intent(context.getApplicationContext(), DashboardTileProviderService.class)
                .setAction(action.name());
        return PendingIntent.getService(context, action.getValue(), onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            Actions action = Actions.valueOf(intent.getAction());
            switch (action) {
                case WIFI:
                    if (wifiAction == null) {
                        requestUpdate();
                        break;
                    }

                    requestAction(new ToggleAction(Actions.WIFI, !wifiAction.isEnabled()));
                    break;
                case BLUETOOTH:
                    if (btAction == null) {
                        requestUpdate();
                        break;
                    }

                    requestAction(new ToggleAction(Actions.BLUETOOTH, !btAction.isEnabled()));
                    break;
                case LOCKSCREEN:
                    requestAction(new NormalAction(Actions.LOCKSCREEN));
                    break;
                case DONOTDISTURB:
                    if (dndAction == null) {
                        requestUpdate();
                        break;
                    }

                    requestAction(new MultiChoiceAction(Actions.DONOTDISTURB, dndAction.getChoice() + 1));
                    break;
                case RINGER:
                    if (ringerAction == null) {
                        requestUpdate();
                        break;
                    }

                    requestAction(new MultiChoiceAction(Actions.RINGER, ringerAction.getChoice() + 1));
                    break;
                case TORCH:
                    if (torchAction == null) {
                        requestUpdate();
                        break;
                    }

                    requestAction(new ToggleAction(Actions.TORCH, !torchAction.isEnabled()));
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    protected volatile Node mPhoneNodeWithApp;
    protected WearConnectionStatus mConnectionStatus = WearConnectionStatus.DISCONNECTED;

    private BatteryStatus battStatus;
    private ToggleAction wifiAction;
    private ToggleAction btAction;
    private MultiChoiceAction dndAction;
    private MultiChoiceAction ringerAction;
    private ToggleAction torchAction;

    @Override
    public void onMessageReceived(@NonNull final MessageEvent messageEvent) {
        final byte[] data = messageEvent.getData();
        if (data == null) return;

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                if (messageEvent.getPath().contains(WearableHelper.WifiPath)) {
                    int wifiStatus = data[0];
                    boolean enabled = false;
                    switch (wifiStatus) {
                        case WifiManager.WIFI_STATE_DISABLING:
                        case WifiManager.WIFI_STATE_DISABLED:
                        case WifiManager.WIFI_STATE_UNKNOWN:
                            enabled = false;
                            break;
                        case WifiManager.WIFI_STATE_ENABLING:
                        case WifiManager.WIFI_STATE_ENABLED:
                            enabled = true;
                            break;
                    }

                    wifiAction = new ToggleAction(Actions.BLUETOOTH, enabled);
                } else if (messageEvent.getPath().contains(WearableHelper.BluetoothPath)) {
                    int bt_status = data[0];
                    boolean enabled = false;

                    switch (bt_status) {
                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            enabled = false;
                            break;
                        case BluetoothAdapter.STATE_ON:
                        case BluetoothAdapter.STATE_TURNING_ON:
                            enabled = true;
                            break;
                    }

                    btAction = new ToggleAction(Actions.BLUETOOTH, enabled);
                } else if (messageEvent.getPath().equals(WearableHelper.BatteryPath)) {
                    String jsonData = bytesToString(data);
                    battStatus = JSONParser.deserializer(jsonData, BatteryStatus.class);
                } else if (messageEvent.getPath().equals(WearableHelper.ActionsPath)) {
                    String jsonData = bytesToString(data);
                    final Action action = JSONParser.deserializer(jsonData, Action.class);
                    switch (action.getAction()) {
                        case WIFI:
                            wifiAction = (ToggleAction) action;
                            break;
                        case BLUETOOTH:
                            btAction = (ToggleAction) action;
                            break;
                        case TORCH:
                            torchAction = (ToggleAction) action;
                            break;
                        case DONOTDISTURB:
                            dndAction = (MultiChoiceAction) action;
                            break;
                        case RINGER:
                            ringerAction = (MultiChoiceAction) action;
                            break;
                    }
                }

                // Send update if tile is in focus
                if (mInFocus && !isIdForDummyData(id)) {
                    sendRemoteViews();
                }
            }
        });
    }

    @Override
    public void onCapabilityChanged(@NonNull final CapabilityInfo capabilityInfo) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.getNodes());

                if (mPhoneNodeWithApp == null) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED;
                } else {
                    mConnectionStatus = WearConnectionStatus.CONNECTED;
                }

                if (mInFocus && !isIdForDummyData(id)) {
                    sendRemoteViews();
                }
            }
        });
    }

    protected void checkConnectionStatus() {
        mPhoneNodeWithApp = checkIfPhoneHasApp();

        if (mPhoneNodeWithApp == null) {
            mConnectionStatus = WearConnectionStatus.DISCONNECTED;
        } else {
            mConnectionStatus = WearConnectionStatus.CONNECTED;
        }

        if (mInFocus && !isIdForDummyData(id)) {
            sendRemoteViews();
        }
    }

    protected Node checkIfPhoneHasApp() {
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
    protected static Node pickBestNodeId(Collection<Node> nodes) {
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

    protected boolean connect() {
        return new AsyncTask<Boolean>().await(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                if (mPhoneNodeWithApp == null)
                    mPhoneNodeWithApp = checkIfPhoneHasApp();

                return mPhoneNodeWithApp != null;
            }
        });
    }

    protected void requestUpdate() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.UpdatePath, null);
        }
    }

    protected final void requestAction(Action action) {
        requestAction(JSONParser.serializer(action, Action.class));
    }

    protected final void requestAction(final String actionJSONString) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                if (connect()) {
                    sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.ActionsPath, stringToBytes(actionJSONString));
                }
            }
        });
    }

    protected void sendMessage(String nodeID, String path, byte[] data) {
        try {
            Tasks.await(Wearable.getMessageClient(this).sendMessage(nodeID, path, data));
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof ApiException) {
                ApiException apiEx = (ApiException) ex.getCause();
                if (apiEx.getStatusCode() == WearableStatusCodes.TARGET_NODE_NOT_CONNECTED) {
                    mConnectionStatus = WearConnectionStatus.DISCONNECTED;

                    if (mInFocus && !isIdForDummyData(id)) {
                        sendRemoteViews();
                    }
                }
            }

            Logger.writeLine(Log.ERROR, ex);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}