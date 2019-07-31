package com.thewizrd.simplewear;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.phone.PhoneDeviceType;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.AppState;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public abstract class WearableListenerActivity extends WearableActivity implements MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    protected volatile Node mPhoneNodeWithApp;
    protected WearConnectionStatus mConnectionStatus = WearConnectionStatus.DISCONNECTED;
    protected Handler mMainHandler;

    // Actions
    public static final String ACTION_OPENONPHONE = "SimpleWear.Droid.Wear.action.OPEN_APP_ON_PHONE";
    public static final String ACTION_SHOWSTORELISTING = "SimpleWear.Droid.Wear.action.SHOW_STORE_LISTING";
    public static final String ACTION_UPDATECONNECTIONSTATUS = "SimpleWear.Droid.Wear.action.UPDATE_CONNECTION_STATUS";
    public static final String ACTION_CHANGED = "SimpleWear.Droid.Wear.action.ACTION_CHANGED";

    // Extras
    /**
     * Extra contains success flag for open on phone action.
     *
     * @see #ACTION_OPENONPHONE
     */
    public static final String EXTRA_SUCCESS = "SimpleWear.Droid.Wear.extra.SUCCESS";
    /**
     * Extra contains flag for whether or not to show the animation for the open on phone action.
     *
     * @see #ACTION_OPENONPHONE
     */
    public static final String EXTRA_SHOWANIMATION = "SimpleWear.Droid.Wear.extra.SHOW_ANIMATION";
    /**
     * Extra contains Action type to be changed for ValueActionActivity
     *
     * @see Actions
     * @see ValueActionActivity
     */
    public static final String EXTRA_ACTION = "SimpleWear.Droid.Wear.extra.ACTION";
    /**
     * Extra contains Action data (serialized class in JSON) to be passed to BroadcastReceiver or Activity
     *
     * @see Action
     * @see WearableListenerActivity
     */
    public static final String EXTRA_ACTIONDATA = "SimpleWear.Droid.Wear.extra.ACTION_DATA";
    /**
     * Extra contains Status data (serialized class in JSON) for complex Status types
     *
     * @see BatteryStatus
     */
    public static final String EXTRA_STATUS = "SimpleWear.Droid.Wear.extra.STATUS";
    /**
     * Extra contains connection status for WearOS device and connected phone
     *
     * @see WearConnectionStatus
     * @see WearableListenerActivity
     */
    public static final String EXTRA_CONNECTIONSTATUS = "SimpleWear.Droid.Wear.extra.CONNECTION_STATUS";

    protected abstract BroadcastReceiver getBroadcastReceiver();

    protected abstract IntentFilter getIntentFilter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();

        Wearable.getCapabilityClient(this).addListener(this, WearableHelper.CAPABILITY_PHONE_APP);
        Wearable.getMessageClient(this).addListener(this);

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(getBroadcastReceiver(), getIntentFilter());
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(getBroadcastReceiver());
        Wearable.getCapabilityClient(this).removeListener(this, WearableHelper.CAPABILITY_PHONE_APP);
        Wearable.getMessageClient(this).removeListener(this);
        super.onPause();
    }

    protected void openAppOnPhone() {
        openAppOnPhone(true);
    }

    protected void openAppOnPhone(final boolean showAnimation) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                new AsyncTask<Void>().await(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        connect();

                        if (mPhoneNodeWithApp == null) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WearableListenerActivity.this, "Device is not connected or app is not installed on device...", Toast.LENGTH_SHORT).show();
                                }
                            });

                            int deviceType = PhoneDeviceType.getPhoneDeviceType(WearableListenerActivity.this);
                            switch (deviceType) {
                                case PhoneDeviceType.DEVICE_TYPE_ANDROID:
                                    LocalBroadcastManager.getInstance(WearableListenerActivity.this).sendBroadcast(
                                            new Intent(ACTION_SHOWSTORELISTING));
                                    break;
                                case PhoneDeviceType.DEVICE_TYPE_IOS:
                                default:
                                    mMainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(WearableListenerActivity.this, "Connected device is not supported", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    break;
                            }
                        } else {
                            // Send message to device to start activity
                            int result = Tasks.await(Wearable.getMessageClient(WearableListenerActivity.this)
                                    .sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.StartActivityPath, new byte[0]));

                            LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                                    .sendBroadcast(new Intent(ACTION_OPENONPHONE)
                                            .putExtra(EXTRA_SUCCESS, result != -1)
                                            .putExtra(EXTRA_SHOWANIMATION, showAnimation));
                        }
                        return null;
                    }
                });
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull final MessageEvent messageEvent) {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                if (messageEvent.getPath().contains(WearableHelper.WifiPath)) {
                    byte[] data = messageEvent.getData();
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

                    LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                            .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                    .putExtra(EXTRA_ACTIONDATA,
                                            JSONParser.serializer(new ToggleAction(Actions.WIFI, enabled), Action.class)));
                } else if (messageEvent.getPath().contains(WearableHelper.BluetoothPath)) {
                    byte[] data = messageEvent.getData();
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

                    LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                            .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                    .putExtra(EXTRA_ACTIONDATA,
                                            JSONParser.serializer(new ToggleAction(Actions.BLUETOOTH, enabled), Action.class)));
                } else if (messageEvent.getPath().equals(WearableHelper.BatteryPath)) {
                    byte[] data = messageEvent.getData();
                    String jsonData = bytesToString(data);
                    LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                            .sendBroadcast(new Intent(WearableHelper.BatteryPath)
                                    .putExtra(EXTRA_STATUS, jsonData));
                } else if (messageEvent.getPath().equals(WearableHelper.AppStatePath)) {
                    AppState appState = App.getInstance().getAppState();
                    sendMessage(messageEvent.getSourceNodeId(), messageEvent.getPath(), stringToBytes(appState.name()));
                } else if (messageEvent.getPath().equals(WearableHelper.ActionsPath)) {
                    byte[] data = messageEvent.getData();
                    String jsonData = bytesToString(data);
                    LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                            .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                    .putExtra(EXTRA_ACTIONDATA, jsonData));
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

                LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                        .sendBroadcast(new Intent(ACTION_UPDATECONNECTIONSTATUS)
                                .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.getValue()));
            }
        });
    }

    protected void updateConnectionStatus() {
        // Make sure we're not on the main thread
        if (Looper.getMainLooper() == Looper.myLooper())
            throw new IllegalStateException("This task should not be called on the main thread");

        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                checkConnectionStatus();

                LocalBroadcastManager.getInstance(WearableListenerActivity.this)
                        .sendBroadcast(new Intent(ACTION_UPDATECONNECTIONSTATUS)
                                .putExtra(EXTRA_CONNECTIONSTATUS, mConnectionStatus.getValue()));
                return null;
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
            public Boolean call() throws Exception {
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
                sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.ActionsPath, stringToBytes(actionJSONString));
            }
        });
    }

    protected void sendMessage(String nodeID, String path, byte[] data) {
        try {
            int ret = Tasks.await(Wearable.getMessageClient(this).sendMessage(nodeID, path, data));
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
