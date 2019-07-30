package com.thewizrd.simplewear;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.wearable.input.RotaryEncoder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;
import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class MusicPlayerActivity extends WearableListenerActivity implements DataClient.OnDataChangedListener {
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter intentFilter;

    private WearableRecyclerView mRecyclerView;
    private MusicPlayerListAdapter mAdapter;
    private ImageView mMediaCtrlIcon;
    private View mMediaCtrlBtn;

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

        mMediaCtrlIcon = findViewById(R.id.media_ctrl_icon);
        mMediaCtrlBtn = findViewById(R.id.ctrl_launcher);

        mRecyclerView = findViewById(R.id.player_list);
        mRecyclerView.setEdgeItemsCenteringEnabled(false);
        mRecyclerView.setCircularScrollingGestureEnabled(false);

        mRecyclerView.setLayoutManager(new WearableLinearLayoutManager(this, null));
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
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            // Don't forget the negation here
            float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                    MusicPlayerActivity.this);

            // Swap these axes if you want to do horizontal scrolling instead
            mRecyclerView.scrollBy(0, Math.round(delta));

            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals(WearableHelper.PlayCommandPath)) {
            ActionStatus status = ActionStatus.valueOf(bytesToString(messageEvent.getData()));

            switch (status) {
                case SUCCESS:
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
                default:
                    break;
            }
        }
    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
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
        List<MusicPlayerViewModel> viewModels = new ArrayList<>();
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

        mAdapter.updateItems(viewModels);
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
        } catch (PackageManager.NameNotFoundException e) {
            mMediaCtrlBtn.setOnClickListener(null);
            mMediaCtrlBtn.setVisibility(View.GONE);
        }

        // Update statuses
        new AsyncTask<Void>().await(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                updateConnectionStatus();
                requestPlayersUpdate();
                return null;
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
