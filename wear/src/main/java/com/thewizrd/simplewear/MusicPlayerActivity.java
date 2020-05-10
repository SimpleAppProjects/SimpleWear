package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;
import androidx.wear.widget.drawer.WearableDrawerLayout;
import androidx.wear.widget.drawer.WearableDrawerView;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.wearable.intent.RemoteIntent;
import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface;
import com.thewizrd.shared_resources.helpers.WearConnectionStatus;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.ImageUtils;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.adapters.MusicPlayerListAdapter;
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay;
import com.thewizrd.simplewear.controls.MusicPlayerViewModel;
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver;
import com.thewizrd.simplewear.preferences.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class MusicPlayerActivity extends WearableListenerActivity implements DataClient.OnDataChangedListener {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    private WearableRecyclerView mRecyclerView;
    private MusicPlayerListAdapter mAdapter;
    private ImageView mMediaCtrlIcon;
    private View mMediaCtrlBtn;
    private ProgressBar mProgressBar;
    private WearableDrawerLayout mDrawerLayout;
    private WearableDrawerView mDrawerView;
    private CountDownTimer timer;
    private TextView mNoPlayerTextView;

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
        setContentView(R.layout.activity_musicplayback);

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
                                        startActivity(new Intent(MusicPlayerActivity.this, PhoneSyncActivity.class)
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                        finishAffinity();
                                        break;
                                    case APPNOTINSTALLED:
                                        // Open store on remote device
                                        Intent intentAndroid = new Intent(Intent.ACTION_VIEW)
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .setData(WearableHelper.getPlayStoreURI());

                                        RemoteIntent.startRemoteActivity(MusicPlayerActivity.this, intentAndroid,
                                                new ConfirmationResultReceiver(MusicPlayerActivity.this));
                                        break;
                                    case CONNECTED:
                                        break;
                                }
                            } else {
                                Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", "MusicPlayerActivity", intent.getAction());
                            }
                        }
                    }
                });
            }
        };

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerStateCallback(new WearableDrawerLayout.DrawerStateCallback() {
            @Override
            public void onDrawerOpened(WearableDrawerLayout layout, WearableDrawerView drawerView) {
                super.onDrawerOpened(layout, drawerView);
                drawerView.requestFocus();
            }

            @Override
            public void onDrawerClosed(WearableDrawerLayout layout, WearableDrawerView drawerView) {
                super.onDrawerClosed(layout, drawerView);
                drawerView.clearFocus();
                mRecyclerView.requestFocus();
            }

            @Override
            public void onDrawerStateChanged(WearableDrawerLayout layout, int newState) {
                super.onDrawerStateChanged(layout, newState);

                if (newState == WearableDrawerView.STATE_IDLE &&
                        mDrawerView.isPeeking() && mDrawerView.hasFocus()) {
                    mDrawerView.clearFocus();
                }
            }
        });

        mProgressBar = findViewById(R.id.progressBar);
        mDrawerView = findViewById(R.id.bottom_action_drawer);
        mMediaCtrlIcon = findViewById(R.id.launch_mediacontrols_icon);
        mMediaCtrlBtn = findViewById(R.id.launch_mediacontrols_ctrl);
        mNoPlayerTextView = findViewById(R.id.noplayers_messageview);

        final Switch mSwitch = findViewById(R.id.autolaunch_pref_switch);
        mSwitch.setChecked(Settings.isAutoLaunchMediaCtrlsEnabled());
        findViewById(R.id.autolaunch_pref).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean state = !Settings.isAutoLaunchMediaCtrlsEnabled();
                Settings.setAutoLaunchMediaCtrls(state);
                mSwitch.setChecked(state);
            }
        });

        mRecyclerView = findViewById(R.id.player_list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setEdgeItemsCenteringEnabled(true);

        mRecyclerView.setLayoutManager(new WearableLinearLayoutManager(this));
        mAdapter = new MusicPlayerListAdapter(this);
        mAdapter.setOnClickListener(new RecyclerOnClickListenerInterface() {
            @Override
            public void onClick(View v, int position) {
                final MusicPlayerViewModel vm = mAdapter.getDataset().get(position);
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.PlayCommandPath,
                                stringToBytes(JSONParser.serializer(Pair.create(vm.getPackageName(), vm.getActivityName()), Pair.class)));
                    }
                });
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS);

        // Set timer for retrieving music player data
        timer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                AsyncTask.run(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DataItemBuffer buff = Tasks.await(Wearable.getDataClient(MusicPlayerActivity.this)
                                    .getDataItems(WearableHelper.getWearDataUri("*", WearableHelper.MusicPlayersPath)));
                            for (int i = 0; i < buff.getCount(); i++) {
                                DataItem item = buff.get(i);
                                if (WearableHelper.MusicPlayersPath.equals(item.getUri().getPath())) {
                                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                                    updateMusicPlayers(dataMap);
                                    showProgressBar(false);
                                }
                            }
                            buff.release();
                        } catch (ExecutionException | InterruptedException e) {
                            Logger.writeLine(Log.ERROR, e);
                        }
                    }
                });
            }
        };
    }

    private void showProgressBar(final boolean show) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals(WearableHelper.PlayCommandPath)) {
            ActionStatus status = ActionStatus.valueOf(bytesToString(messageEvent.getData()));

            switch (status) {
                case SUCCESS:
                    if (Settings.isAutoLaunchMediaCtrlsEnabled()) {
                        final ComponentName mediaCtrlCmpName = new ComponentName("com.google.android.wearable.app",
                                "com.google.android.clockwork.home.media.MediaControlActivity");

                        try {
                            // Check if activity exists
                            getPackageManager().getActivityInfo(mediaCtrlCmpName, 0);

                            Intent mediaCtrlIntent = new Intent();
                            mediaCtrlIntent
                                    .setAction(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .setComponent(mediaCtrlCmpName);
                            startActivity(mediaCtrlIntent);
                        } catch (PackageManager.NameNotFoundException e) {
                            // Do nothing
                        }
                    }
                    break;
                case PERMISSION_DENIED:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(MusicPlayerActivity.this.getDrawable(R.drawable.ic_full_sad))
                                    .setMessage(MusicPlayerActivity.this.getString(R.string.error_permissiondenied))
                                    .showOn(MusicPlayerActivity.this);
                        }
                    });
                    break;
                case FAILURE:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(MusicPlayerActivity.this.getDrawable(R.drawable.ic_full_sad))
                                    .setMessage(MusicPlayerActivity.this.getString(R.string.action_failed_playmusic))
                                    .showOn(MusicPlayerActivity.this);
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
        // Cancel timer
        timer.cancel();
        showProgressBar(false);

        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (WearableHelper.MusicPlayersPath.equals(item.getUri().getPath())) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    updateMusicPlayers(dataMap);
                }
            }
        }
    }

    private void updateMusicPlayers(DataMap dataMap) {
        List<String> supported_players = dataMap.getStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS);
        final List<MusicPlayerViewModel> viewModels = new ArrayList<>();
        for (String key : supported_players) {
            DataMap map = dataMap.getDataMap(key);

            MusicPlayerViewModel model = new MusicPlayerViewModel();
            model.setAppLabel(String.format("%s %s", getString(R.string.prefix_playmusic), map.getString(WearableHelper.KEY_LABEL)));
            model.setPackageName(map.getString(WearableHelper.KEY_PKGNAME));
            model.setActivityName(map.getString(WearableHelper.KEY_ACTIVITYNAME));
            model.setBitmapIcon(ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(this), map.getAsset(WearableHelper.KEY_ICON)));

            viewModels.add(model);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.updateItems(viewModels);

                mNoPlayerTextView.setVisibility(viewModels.size() > 0 ? View.GONE : View.VISIBLE);
                mRecyclerView.setVisibility(viewModels.size() > 0 ? View.VISIBLE : View.GONE);
                if (mRecyclerView.getVisibility() == View.VISIBLE && !mRecyclerView.hasFocus()) {
                    mRecyclerView.requestFocus();
                }
            }
        });
    }

    private void requestPlayersUpdate() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp.getId(), WearableHelper.MusicPlayersPath, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(this).addListener(this);
        Wearable.getDataClient(this).addListener(this);

        mRecyclerView.requestFocus();

        final ComponentName mediaCtrlCmpName = new ComponentName("com.google.android.wearable.app",
                "com.google.android.clockwork.home.media.MediaControlActivity");
        try {
            mMediaCtrlIcon.setImageDrawable(getPackageManager().getActivityIcon(mediaCtrlCmpName));
            mMediaCtrlBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent mediaCtrlIntent = new Intent();
                    mediaCtrlIntent
                            .setAction(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .setComponent(mediaCtrlCmpName);
                    startActivity(mediaCtrlIntent);
                }
            });
            mDrawerView.setVisibility(View.VISIBLE);
            mDrawerView.setPeekOnScrollDownEnabled(true);
            mDrawerView.setIsAutoPeekEnabled(true);
            mDrawerView.setIsLocked(false);
        } catch (PackageManager.NameNotFoundException e) {
            mMediaCtrlBtn.setOnClickListener(null);
            mMediaCtrlBtn.setVisibility(View.GONE);
            mDrawerView.setVisibility(View.GONE);
            mDrawerView.setPeekOnScrollDownEnabled(false);
            mDrawerView.setIsAutoPeekEnabled(false);
            mDrawerView.setIsLocked(true);
        }

        // Update statuses
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
                requestPlayersUpdate();
                // Wait for music player update
                timer.start();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(this).removeListener(this);
        Wearable.getDataClient(this).removeListener(this);
    }
}
