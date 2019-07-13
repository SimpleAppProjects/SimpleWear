package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.wearable.view.ConfirmationOverlay;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.wear.widget.CircularProgressLayout;

import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;

public class PhoneSyncActivity extends WearableListenerActivity {
    private CircularProgressLayout mCircularProgress;
    private ImageView mButtonView;
    private TextView mTextView;
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    @Override
    protected BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    @Override
    public IntentFilter getIntentFilter() {
        return intentFilter;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create your application here
        setContentView(R.layout.activity_setup_sync);

        mCircularProgress = findViewById(R.id.circular_progress);
        mCircularProgress.setIndeterminate(true);

        mButtonView = findViewById(R.id.button);
        mTextView = findViewById(R.id.message);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATECONNECTIONSTATUS.equals(intent.getAction())) {
                    WearConnectionStatus connStatus = WearConnectionStatus.valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0));
                    switch (connStatus) {
                        case DISCONNECTED:
                            mTextView.setText(R.string.status_disconnected);
                            mButtonView.setImageDrawable(context.getDrawable(R.drawable.ic_phonelink_erase_white_24dp));
                            stopProgressBar();
                            break;
                        case CONNECTING:
                            mTextView.setText(R.string.status_connecting);
                            mButtonView.setImageDrawable(context.getDrawable(android.R.drawable.ic_popup_sync));
                            break;
                        case APPNOTINSTALLED:
                            mTextView.setText(R.string.error_notinstalled);

                            mCircularProgress.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // Open store on remote device
                                    Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(WearableHelper.getPlayStoreURI());

                                    RemoteIntent.startRemoteActivity(PhoneSyncActivity.this, intentAndroid, null);

                                    // Show open on phone animation
                                    new ConfirmationOverlay().setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                            .setMessage(PhoneSyncActivity.this.getString(R.string.message_openedonphone))
                                            .showOn(PhoneSyncActivity.this);
                                }
                            });
                            mButtonView.setImageDrawable(context.getDrawable(R.drawable.common_full_open_on_phone));

                            stopProgressBar();
                            break;
                        case CONNECTED:
                            mTextView.setText(R.string.status_connected);
                            mButtonView.setImageDrawable(context.getDrawable(android.R.drawable.ic_popup_sync));

                            // Continue operation
                            startActivity(new Intent(PhoneSyncActivity.this, DashboardActivity.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            stopProgressBar();
                            break;
                    }
                } else if (ACTION_OPENONPHONE.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(EXTRA_SUCCESS, false);

                    new ConfirmationOverlay()
                            .setType(success ? ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION : ConfirmationOverlay.FAILURE_ANIMATION)
                            .showOn(PhoneSyncActivity.this);

                    if (!success) {
                        mTextView.setText(R.string.error_syncing);
                    }
                }
            }
        };

        mTextView.setText(R.string.message_gettingstatus);

        intentFilter = new IntentFilter(ACTION_UPDATECONNECTIONSTATUS);
    }

    private void stopProgressBar() {
        mCircularProgress.setIndeterminate(false);
        mCircularProgress.setTotalTime(1);
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
