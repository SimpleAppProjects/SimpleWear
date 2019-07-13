package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.view.ConfirmationOverlay;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.BatteryStatus;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.adapters.ToggleActionAdapter;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;

import java.util.Locale;

public class DashboardActivity extends WearableListenerActivity {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;
    private WearableRecyclerView mActionsList;
    private ToggleActionAdapter mAdapter;

    private TextView mConnStatus;
    private TextView mBattStatus;

    private ConnectivityManager connectivityManager;
    private SparseArray<CountDownTimer> activeTimers;

    @Override
    protected BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    @Override
    public IntentFilter getIntentFilter() {
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull final Intent intent) {
                if (intent.getAction() != null) {
                    if (ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                        WearConnectionStatus connStatus = WearConnectionStatus.valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0));
                        switch (connStatus) {
                            case DISCONNECTED:
                                mConnStatus.setText(R.string.status_disconnected);

                                // Navigate
                                startActivity(new Intent(DashboardActivity.this, PhoneSyncActivity.class)
                                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                finishAffinity();
                                break;
                            case CONNECTING:
                                mConnStatus.setText(R.string.status_connecting);
                                if (mActionsList.isEnabled())
                                    mActionsList.setEnabled(false);
                                break;
                            case APPNOTINSTALLED:
                                mConnStatus.setText(R.string.error_notinstalled);

                                // Open store on remote device
                                Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(WearableHelper.getPlayStoreURI());

                                RemoteIntent.startRemoteActivity(DashboardActivity.this, intentAndroid,
                                        new ConfirmationResultReceiver(DashboardActivity.this));

                                if (mActionsList.isEnabled())
                                    mActionsList.setEnabled(false);
                                break;
                            case CONNECTED:
                                mConnStatus.setText(R.string.status_connected);
                                if (!mActionsList.isEnabled())
                                    mActionsList.setEnabled(true);
                                break;
                        }
                    } else if (ACTION_OPENONPHONE.equals(intent.getAction())) {
                        boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, false);

                        new ConfirmationOverlay()
                                .setType(success ? ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION : ConfirmationOverlay.FAILURE_ANIMATION)
                                .showOn(DashboardActivity.this);

                        if (!success) {
                            mConnStatus.setText(R.string.error_syncing);
                        }
                    } else if (WearableHelper.BatteryPath.equals(intent.getAction())) {
                        String jsonData = intent.getStringExtra(EXTRA_STATUS);
                        String value = getString(R.string.state_unknown);
                        if (!StringUtils.isNullOrWhitespace(jsonData)) {
                            BatteryStatus status = JSONParser.deserializer(jsonData, BatteryStatus.class);
                            value = String.format(Locale.ROOT, "%d%%, %s", status.batteryLevel,
                                    status.isCharging ? getString(R.string.batt_state_charging) : getString(R.string.batt_state_discharging));
                        }
                        mBattStatus.setText(value);
                    } else if (WearableHelper.ActionsPath.equals(intent.getAction())) {
                        String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                        Action action = JSONParser.deserializer(jsonData, Action.class);

                        CountDownTimer timer = activeTimers.get(action.getAction().getValue());
                        if (timer != null) {
                            timer.cancel();
                            activeTimers.delete(action.getAction().getValue());
                            timer = null;
                        }

                        if (action instanceof ToggleAction)
                            mAdapter.updateToggle((ToggleAction) action);
                        //else if (action instanceof ValueAction)
                        //    mAdapter.updateToggle((ValueAction) action);
                    } else if (ACTION_CHANGED.equals(intent.getAction())) {
                        String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                        final Action action = JSONParser.deserializer(jsonData, Action.class);
                        requestAction(jsonData);

                        CountDownTimer timer = new CountDownTimer(2500, 500) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {
                                action.setActionSuccessful(false);
                                LocalBroadcastManager.getInstance(DashboardActivity.this)
                                        .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                                .putExtra(EXTRA_ACTIONDATA, JSONParser.serializer(action, Action.class)));
                            }
                        };
                        timer.start();
                        activeTimers.append(action.getAction().getValue(), timer);
                    } else {
                        Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "DashboardActivity", intent.getAction());
                    }
                }
            }
        };

        mConnStatus = findViewById(R.id.device_stat_text);
        mBattStatus = findViewById(R.id.batt_stat_text);
        mActionsList = findViewById(R.id.actions_list);

        mConnStatus.setText(R.string.message_gettingstatus);

        mActionsList.setEdgeItemsCenteringEnabled(false);
        mActionsList.setCircularScrollingGestureEnabled(false);
        mActionsList.setLayoutManager(new GridLayoutManager(this, 3));

        mAdapter = new ToggleActionAdapter();
        mActionsList.setAdapter(mAdapter);
        mActionsList.setEnabled(false);

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS);
        intentFilter.addAction(ACTION_OPENONPHONE);
        intentFilter.addAction(ACTION_CHANGED);
        intentFilter.addAction(WearableHelper.BatteryPath);
        intentFilter.addAction(WearableHelper.ActionsPath);

        activeTimers = new SparseArray<>();

        mMainHandler = new Handler(Looper.getMainLooper());
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private ConnectivityManager.NetworkCallback mNetCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        connectivityManager.registerNetworkCallback(request, mNetCallback);

        // Update statuses
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
                requestUpdate();
            }
        });
    }

    @Override
    protected void onPause() {
        connectivityManager.unregisterNetworkCallback(mNetCallback);
        super.onPause();
    }
}
