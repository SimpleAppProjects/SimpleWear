package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.ValueAction;
import com.thewizrd.shared_resources.helpers.ValueDirection;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;

import static com.thewizrd.shared_resources.helpers.Actions.VOLUME;

public class ValueActionActivity extends WearableListenerActivity {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    private Actions mAction;

    private TextView mTitleView;
    private ImageView mIconView;
    private FloatingActionButton mPlusBtn;
    private FloatingActionButton mMinBtn;

    private CountDownTimer timer;

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
        setContentView(R.layout.activity_valueaction);

        Intent intent = getIntent();
        if (intent.hasExtra(WearableListenerActivity.EXTRA_ACTION)) {
            mAction = (Actions) intent.getSerializableExtra(WearableListenerActivity.EXTRA_ACTION);
            if (mAction != VOLUME) {
                // Not a ValueAction
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            // No action given
            setResult(RESULT_CANCELED);
            finish();
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull final Intent intent) {
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        if (intent.getAction() != null) {
                            if (ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                                WearConnectionStatus connStatus = WearConnectionStatus.valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0));
                                switch (connStatus) {
                                    case DISCONNECTED:
                                        // Navigate
                                        startActivity(new Intent(ValueActionActivity.this, PhoneSyncActivity.class)
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                        finishAffinity();
                                        break;
                                    case APPNOTINSTALLED:
                                        // Open store on remote device
                                        Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .setData(WearableHelper.getPlayStoreURI());

                                        RemoteIntent.startRemoteActivity(ValueActionActivity.this, intentAndroid,
                                                new ConfirmationResultReceiver(ValueActionActivity.this));
                                        break;
                                    case CONNECTED:
                                        break;
                                }
                            } else if (WearableHelper.ActionsPath.equals(intent.getAction())) {
                                if (timer != null) timer.cancel();

                                final String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                                final Action action = JSONParser.deserializer(jsonData, Action.class);

                                if (!action.isActionSuccessful()) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            switch (action.getActionStatus()) {
                                                case UNKNOWN:
                                                case FAILURE:
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            new CustomConfirmationOverlay()
                                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                                    .setCustomDrawable(ValueActionActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                                    .setMessage(ValueActionActivity.this.getString(R.string.error_actionfailed))
                                                                    .showOn(ValueActionActivity.this);
                                                        }
                                                    });
                                                    break;
                                                case PERMISSION_DENIED:
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            new CustomConfirmationOverlay()
                                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                                    .setCustomDrawable(ValueActionActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                                    .setMessage(ValueActionActivity.this.getString(R.string.error_permissiondenied))
                                                                    .showOn(ValueActionActivity.this);
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
                                                                    .setCustomDrawable(ValueActionActivity.this.getDrawable(R.drawable.ic_full_sad))
                                                                    .setMessage(ValueActionActivity.this.getString(R.string.error_sendmessage))
                                                                    .showOn(ValueActionActivity.this);
                                                        }
                                                    });
                                                    break;
                                                case SUCCESS:
                                                    break;
                                            }
                                        }
                                    });
                                }
                            } else if (ACTION_CHANGED.equals(intent.getAction())) {
                                String jsonData = intent.getStringExtra(EXTRA_ACTIONDATA);
                                final Action action = JSONParser.deserializer(jsonData, Action.class);
                                requestAction(jsonData);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (timer != null) timer.cancel();
                                        timer = new CountDownTimer(3000, 500) {
                                            @Override
                                            public void onTick(long millisUntilFinished) {

                                            }

                                            @Override
                                            public void onFinish() {
                                                action.setActionSuccessful(ActionStatus.TIMEOUT);
                                                LocalBroadcastManager.getInstance(ValueActionActivity.this)
                                                        .sendBroadcast(new Intent(WearableHelper.ActionsPath)
                                                                .putExtra(EXTRA_ACTIONDATA, JSONParser.serializer(action, Action.class)));
                                            }
                                        };
                                        timer.start();
                                    }
                                });
                            } else {
                                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "ValueActionActivity", intent.getAction());
                            }
                        }
                    }
                });
            }
        };

        mTitleView = findViewById(R.id.action_title);
        mIconView = findViewById(R.id.action_icon);
        mPlusBtn = findViewById(R.id.increase_btn);
        mMinBtn = findViewById(R.id.decrease_btn);

        mPlusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ValueAction actionData = new ValueAction(mAction, ValueDirection.UP);

                LocalBroadcastManager.getInstance(ValueActionActivity.this)
                        .sendBroadcast(new Intent(WearableListenerActivity.ACTION_CHANGED)
                                .putExtra(WearableListenerActivity.EXTRA_ACTIONDATA,
                                        JSONParser.serializer(actionData, Action.class)));
            }
        });
        mMinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ValueAction actionData = new ValueAction(mAction, ValueDirection.DOWN);

                LocalBroadcastManager.getInstance(ValueActionActivity.this)
                        .sendBroadcast(new Intent(WearableListenerActivity.ACTION_CHANGED)
                                .putExtra(WearableListenerActivity.EXTRA_ACTIONDATA,
                                        JSONParser.serializer(actionData, Action.class)));
            }
        });

        switch (mAction) {
            case VOLUME:
                mTitleView.setText(R.string.action_volume);
                mIconView.setImageDrawable(getDrawable(R.drawable.ic_volume_up_white_24dp));
                break;
        }

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS);
        intentFilter.addAction(ACTION_CHANGED);
        intentFilter.addAction(WearableHelper.ActionsPath);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update statuses
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
