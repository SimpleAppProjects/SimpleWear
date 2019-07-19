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
import android.os.Looper;
import android.support.wearable.input.RotaryEncoder;
import android.support.wearable.view.ConfirmationOverlay;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.wear.widget.WearableRecyclerView;

import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.adapters.ActionItemAdapter;
import com.thewizrd.simplewear.controls.ActionButtonViewModel;
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;

import java.util.Locale;
import java.util.concurrent.Callable;

public class DashboardActivity extends WearableListenerActivity {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;
    private WearableRecyclerView mActionsList;
    private ActionItemAdapter mAdapter;
    private SwipeRefreshLayout mSwipeLayout;
    private NestedScrollView mScrollView;

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
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        if (intent.getAction() != null) {
                            if (ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                                WearConnectionStatus connStatus = WearConnectionStatus.valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0));
                                switch (connStatus) {
                                    case DISCONNECTED:
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mConnStatus.setText(R.string.status_disconnected);

                                                // Navigate
                                                startActivity(new Intent(DashboardActivity.this, PhoneSyncActivity.class)
                                                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                                finishAffinity();
                                            }
                                        });
                                        break;
                                    case CONNECTING:
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mConnStatus.setText(R.string.status_connecting);
                                                if (mActionsList.isEnabled())
                                                    mActionsList.setEnabled(false);
                                            }
                                        });
                                        break;
                                    case APPNOTINSTALLED:
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mConnStatus.setText(R.string.error_notinstalled);

                                                // Open store on remote device
                                                Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                                        .setData(WearableHelper.getPlayStoreURI());

                                                RemoteIntent.startRemoteActivity(DashboardActivity.this, intentAndroid,
                                                        new ConfirmationResultReceiver(DashboardActivity.this));

                                                if (mActionsList.isEnabled())
                                                    mActionsList.setEnabled(false);
                                            }
                                        });
                                        break;
                                    case CONNECTED:
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mConnStatus.setText(R.string.status_connected);
                                                if (!mActionsList.isEnabled())
                                                    mActionsList.setEnabled(true);
                                            }
                                        });
                                        break;
                                }
                            } else if (ACTION_OPENONPHONE.equals(intent.getAction())) {
                                final boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, false);
                                final boolean showAni = intent.getBooleanExtra(EXTRA_SHOWANIMATION, false);

                                if (showAni) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            new ConfirmationOverlay()
                                                    .setType(success ? ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION : ConfirmationOverlay.FAILURE_ANIMATION)
                                                    .showOn(DashboardActivity.this);

                                            if (!success) {
                                                mConnStatus.setText(R.string.error_syncing);
                                            }
                                        }
                                    });
                                }
                            } else if (WearableHelper.BatteryPath.equals(intent.getAction())) {
                                final String jsonData = intent.getStringExtra(EXTRA_STATUS);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String value = getString(R.string.state_unknown);
                                        if (!StringUtils.isNullOrWhitespace(jsonData)) {
                                            BatteryStatus status = JSONParser.deserializer(jsonData, BatteryStatus.class);
                                            value = String.format(Locale.ROOT, "%d%%, %s", status.batteryLevel,
                                                    status.isCharging ? getString(R.string.batt_state_charging) : getString(R.string.batt_state_discharging));
                                        }

                                        mBattStatus.setText(value);
                                    }
                                });
                            } else if (WearableHelper.ActionsPath.equals(intent.getAction())) {
                                final String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                                final Action action = JSONParser.deserializer(jsonData, Action.class);

                                cancelTimer(action.getAction());

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mAdapter.updateButton(new ActionButtonViewModel(action));
                                    }
                                });

                                if (!action.isActionSuccessful()) {
                                    switch (action.getActionStatus()) {
                                        case UNKNOWN:
                                        case FAILURE:
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    new CustomConfirmationOverlay()
                                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                            .setCustomDrawable(DashboardActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                            .setMessage(DashboardActivity.this.getString(R.string.error_actionfailed))
                                                            .showOn(DashboardActivity.this);
                                                }
                                            });
                                            break;
                                        case PERMISSION_DENIED:
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (action.getAction() == Actions.TORCH)
                                                        new CustomConfirmationOverlay()
                                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                                .setCustomDrawable(DashboardActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                                .setMessage(DashboardActivity.this.getString(R.string.error_torch_action))
                                                                .showOn(DashboardActivity.this);
                                                    else {
                                                        new CustomConfirmationOverlay()
                                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                                .setCustomDrawable(DashboardActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                                .setMessage(DashboardActivity.this.getString(R.string.error_permissiondenied))
                                                                .showOn(DashboardActivity.this);
                                                    }
                                                }
                                            });

                                            openAppOnPhone(false);
                                            break;
                                        case TIMEOUT:
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    new CustomConfirmationOverlay()
                                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                            .setCustomDrawable(DashboardActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                            .setMessage(DashboardActivity.this.getString(R.string.error_sendmessage))
                                                            .showOn(DashboardActivity.this);
                                                }
                                            });
                                            break;
                                        case SUCCESS:
                                            break;
                                    }
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Re-enable click action
                                        mAdapter.setItemsClickable(true);
                                    }
                                });
                            } else if (ACTION_CHANGED.equals(intent.getAction())) {
                                String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                                final Action action = JSONParser.deserializer(jsonData, Action.class);
                                requestAction(jsonData);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        CountDownTimer timer = new CountDownTimer(3000, 500) {
                                            @Override
                                            public void onTick(long millisUntilFinished) {

                                            }

                                            @Override
                                            public void onFinish() {
                                                action.setActionSuccessful(ActionStatus.TIMEOUT);
                                                LocalBroadcastManager.getInstance(DashboardActivity.this)
                                                        .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                                                .putExtra(EXTRA_ACTIONDATA, JSONParser.serializer(action, Action.class)));
                                            }
                                        };
                                        timer.start();
                                        activeTimers.append(action.getAction().getValue(), timer);

                                        // Disable click action for all items until a response is received
                                        mAdapter.setItemsClickable(false);
                                    }
                                });
                            } else {
                                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "DashboardActivity", intent.getAction());
                            }
                        }
                    }
                });
            }
        };

        mSwipeLayout = findViewById(R.id.swipe_layout);
        mConnStatus = findViewById(R.id.device_stat_text);
        mBattStatus = findViewById(R.id.batt_stat_text);
        mActionsList = findViewById(R.id.actions_list);

        mSwipeLayout.setColorSchemeColors(getColor(R.color.colorPrimary));
        mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        requestUpdate();
                    }
                });
                mSwipeLayout.setRefreshing(false);
            }
        });

        mConnStatus.setText(R.string.message_gettingstatus);

        mActionsList.setEdgeItemsCenteringEnabled(false);
        mActionsList.setCircularScrollingGestureEnabled(false);
        mActionsList.setLayoutManager(new GridLayoutManager(this, 3));

        mScrollView = findViewById(R.id.scrollView);
        mActionsList.setFocusable(false);
        mActionsList.clearFocus();

        mAdapter = new ActionItemAdapter(this);
        mActionsList.setAdapter(mAdapter);
        mActionsList.setEnabled(false);

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS);
        intentFilter.addAction(ACTION_OPENONPHONE);
        intentFilter.addAction(ACTION_CHANGED);
        intentFilter.addAction(WearableHelper.BatteryPath);
        intentFilter.addAction(WearableHelper.ActionsPath);

        activeTimers = new SparseArray<>();

        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            // Don't forget the negation here
            float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                    DashboardActivity.this);

            // Swap these axes if you want to do horizontal scrolling instead
            mScrollView.scrollBy(0, Math.round(delta));

            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private void cancelTimer(Actions action) {
        CountDownTimer timer = activeTimers.get(action.getValue());
        if (timer != null) {
            timer.cancel();
            activeTimers.delete(action.getValue());
            timer = null;
        }
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
        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                updateConnectionStatus();
                requestUpdate();
                return null;
            }
        });
    }

    @Override
    protected void onPause() {
        connectivityManager.unregisterNetworkCallback(mNetCallback);
        super.onPause();
    }
}
