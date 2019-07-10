package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.ConfirmationOverlay;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.BatteryStatus;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

import java.util.Locale;

public class DashboardActivity extends WearableActivity {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    private TextView mConnStatus;
    private TextView mBattStatus;
    private TextView mWiFiStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull Intent intent) {
                if (intent.getAction() != null) {
                    if (WearableDataListenerService.ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                        WearConnectionStatus connStatus = WearConnectionStatus.valueOf(intent.getIntExtra(WearableDataListenerService.EXTRA_CONNECTIONSTATUS, 0));
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
                                break;
                            case APPNOTINSTALLED:
                                mConnStatus.setText(R.string.error_notinstalled);

                                // Open store on remote device
                                Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(WearableHelper.getPlayStoreURI());

                                RemoteIntent.startRemoteActivity(DashboardActivity.this, intentAndroid,
                                        new ConfirmationResultReceiver(DashboardActivity.this));
                                break;
                            case CONNECTED:
                                mConnStatus.setText(R.string.status_connected);
                                break;
                        }
                    } else if (WearableDataListenerService.ACTION_OPENONPHONE.equals(intent.getAction())) {
                        boolean success = intent.getBooleanExtra(WearableDataListenerService.EXTRA_SUCCESS, false);

                        new ConfirmationOverlay()
                                .setType(success ? ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION : ConfirmationOverlay.FAILURE_ANIMATION)
                                .showOn(DashboardActivity.this);

                        if (!success) {
                            mConnStatus.setText(R.string.error_syncing);
                        }
                    } else if (WearableHelper.WifiPath.equals(intent.getAction())) {
                        int wifiState = intent.getIntExtra(WearableDataListenerService.EXTRA_STATUS, WifiManager.WIFI_STATE_UNKNOWN);
                        switch (wifiState) {
                            case WifiManager.WIFI_STATE_DISABLING:
                                mWiFiStatus.setText(getString(R.string.wifi_state_disabling));
                                break;
                            case WifiManager.WIFI_STATE_DISABLED:
                                mWiFiStatus.setText(getString(R.string.wifi_state_disabled));
                                break;
                            case WifiManager.WIFI_STATE_ENABLING:
                                mWiFiStatus.setText(getString(R.string.wifi_state_enabling));
                                break;
                            case WifiManager.WIFI_STATE_ENABLED:
                                mWiFiStatus.setText(getString(R.string.wifi_state_enabled));
                                break;
                            case WifiManager.WIFI_STATE_UNKNOWN:
                            default:
                                mWiFiStatus.setText(getString(R.string.wifi_state_unknown));
                                break;
                        }
                    } else if (WearableHelper.BatteryPath.equals(intent.getAction())) {
                        String jsonData = intent.getStringExtra(WearableDataListenerService.EXTRA_STATUS);
                        String value = getString(R.string.state_unknown);
                        if (!StringUtils.isNullOrWhitespace(jsonData)) {
                            BatteryStatus status = JSONParser.deserializer(jsonData, BatteryStatus.class);
                            value = String.format(Locale.ROOT, "%d%%, %s", status.batteryLevel,
                                    status.isCharging ? getString(R.string.batt_state_charging) : getString(R.string.batt_state_discharging));
                        }
                        mBattStatus.setText(value);
                    } else {
                        Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "DashboardActivity", intent.getAction());
                    }
                }
            }
        };

        mConnStatus = findViewById(R.id.device_stat_text);
        mBattStatus = findViewById(R.id.batt_stat_text);
        mWiFiStatus = findViewById(R.id.wifi_stat_text);

        mConnStatus.setText(R.string.message_gettingstatus);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WearableDataListenerService.ACTION_UPDATECONNECTIONSTATUS);
        intentFilter.addAction(WearableDataListenerService.ACTION_OPENONPHONE);
        intentFilter.addAction(WearableHelper.WifiPath);
        intentFilter.addAction(WearableHelper.BatteryPath);

        startService(new Intent(this, WearableDataListenerService.class)
                .setAction(WearableDataListenerService.ACTION_UPDATECONNECTIONSTATUS));
    }


    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mBroadcastReceiver, intentFilter);

        // Update statuses
        startService(new Intent(this, WearableDataListenerService.class)
                .setAction(WearableHelper.UpdatePath));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }
}
