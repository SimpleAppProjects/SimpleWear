package com.thewizrd.simplewear.sleeptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.shared_resources.tasks.AsyncTask;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.PhoneSyncActivity;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.WearableListenerActivity;
import com.thewizrd.simplewear.databinding.ActivitySleeptimerBinding;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToBool;
import static com.thewizrd.shared_resources.utils.SerializationUtils.intToBytes;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class SleepTimerActivity extends WearableListenerActivity {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    private ActivitySleeptimerBinding binding;

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
        binding = ActivitySleeptimerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
                                        startActivity(new Intent(SleepTimerActivity.this, PhoneSyncActivity.class)
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                        finishAffinity();
                                        break;
                                    case APPNOTINSTALLED:
                                        // Open store on remote device
                                        Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .setData(WearableHelper.getPlayStoreURI());

                                        RemoteIntent.startRemoteActivity(SleepTimerActivity.this, intentAndroid,
                                                new ConfirmationResultReceiver(SleepTimerActivity.this));
                                        break;
                                    case CONNECTED:
                                        break;
                                }
                            } else if (SleepTimerHelper.SleepTimerStartPath.equals(intent.getAction())) {
                                sendMessage(mPhoneNodeWithApp.getId(), SleepTimerHelper.SleepTimerStartPath,
                                        intToBytes(intent.getIntExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, 0)));

                                SelectedPlayerViewModel selectedPlayer = new ViewModelProvider(SleepTimerActivity.this, new ViewModelProvider.NewInstanceFactory())
                                        .get(SelectedPlayerViewModel.class);
                                if (selectedPlayer.getKeyValue() != null) {
                                    String[] data = selectedPlayer.getKeyValue().split("/");
                                    if (data.length == 2) {
                                        String packageName = data[0];
                                        String activityName = data[1];
                                        sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.OpenMusicPlayerPath,
                                                stringToBytes(JSONParser.serializer(Pair.create(packageName, activityName), Pair.class)));
                                    }
                                }
                            } else if (SleepTimerHelper.SleepTimerStopPath.equals(intent.getAction())) {
                                sendMessage(mPhoneNodeWithApp.getId(), SleepTimerHelper.SleepTimerStopPath, null);
                            } else if (WearableHelper.MusicPlayersPath.equals(intent.getAction())) {
                                sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.MusicPlayersPath, null);
                            } else {
                                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "MusicPlayerActivity", intent.getAction());
                            }
                        }
                    }
                });
            }
        };

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS);
        intentFilter.addAction(SleepTimerHelper.SleepTimerStartPath);
        intentFilter.addAction(SleepTimerHelper.SleepTimerStopPath);
        intentFilter.addAction(WearableHelper.MusicPlayersPath);
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerEnabledPath)) {
            boolean isEnabled = bytesToBool(messageEvent.getData());

            showProgressBar(false);
            if (isEnabled) {
                binding.fragmentContainer.setVisibility(View.VISIBLE);
                binding.nosleeptimerMessageview.setVisibility(View.GONE);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TimerStartFragment())
                        .commit();
            } else {
                binding.fragmentContainer.setVisibility(View.GONE);
                binding.nosleeptimerMessageview.setVisibility(View.VISIBLE);
                binding.nosleeptimerMessageview.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intentapp = new Intent(Intent.ACTION_VIEW)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setData(SleepTimerHelper.getPlayStoreURI());

                        RemoteIntent.startRemoteActivity(SleepTimerActivity.this, intentapp,
                                new ConfirmationResultReceiver(SleepTimerActivity.this));
                    }
                });

                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                    if (fragment != null) {
                        getSupportFragmentManager().beginTransaction()
                                .remove(fragment)
                                .commit();
                    }
                }
            }
        } else if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStatusPath) ||
                messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStartPath)) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (!(fragment instanceof TimerProgressFragment)) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TimerProgressFragment())
                        .commit();
            }
        } else if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStopPath)) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (!(fragment instanceof TimerStartFragment)) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new TimerStartFragment())
                        .commit();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(this).addListener(this);

        // Update statuses
        showProgressBar(true);
        binding.nosleeptimerMessageview.setVisibility(View.GONE);
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
                sendMessage(mPhoneNodeWithApp.getId(), SleepTimerHelper.SleepTimerEnabledPath, null);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(this).removeListener(this);
    }

    private void showProgressBar(final boolean show) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }
}