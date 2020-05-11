package com.thewizrd.simplewear.sleeptimer;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.wearable.input.RotaryEncoder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.wear.widget.WearableLinearLayoutManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.ImageUtils;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.controls.MusicPlayerViewModel;
import com.thewizrd.simplewear.databinding.FragmentMusicplayersSleepBinding;
import com.thewizrd.simplewear.fragments.SwipeDismissFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class MusicPlayersFragment extends SwipeDismissFragment
        implements DataClient.OnDataChangedListener {

    private FragmentMusicplayersSleepBinding binding;
    private PlayerListAdapter mAdapter;
    private CountDownTimer timer;
    private RecyclerOnClickListenerInterface onClickListener;

    private SelectedPlayerViewModel selectedPlayer;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                            DataItemBuffer buff = Tasks.await(Wearable.getDataClient(mActivity)
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

        selectedPlayer = new ViewModelProvider(mActivity, new ViewModelProvider.NewInstanceFactory())
                .get(SelectedPlayerViewModel.class);
        selectedPlayer.getKey().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                PutDataMapRequest mapRequest = PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath);
                mapRequest.getDataMap().putString(SleepTimerHelper.KEY_SELECTEDPLAYER, s);
                Wearable.getDataClient(mActivity).putDataItem(
                        mapRequest.asPutDataRequest()).addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Logger.writeLine(Log.ERROR, e);
                            }
                        }
                );
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View outerView = super.onCreateView(inflater, container, savedInstanceState);
        binding = FragmentMusicplayersSleepBinding.inflate(inflater, (ViewGroup) outerView, true);

        binding.playerList.setHasFixedSize(true);
        binding.playerList.setEdgeItemsCenteringEnabled(false);
        binding.playerList.setLayoutManager(new WearableLinearLayoutManager(mActivity, null));
        binding.playerList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            /* BoxInsetLayout impl */
            private static final float FACTOR = 0.146447f; //(1 - sqrt(2)/2)/2
            private final boolean mIsRound = getResources().getConfiguration().isScreenRound();
            private final int paddingTop = binding.playerList.getPaddingTop();
            private final int paddingBottom = binding.playerList.getPaddingBottom();
            private final int paddingStart = ViewCompat.getPaddingStart(binding.playerList);
            private final int paddingEnd = ViewCompat.getPaddingEnd(binding.playerList);

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                binding.playerList.removeOnLayoutChangeListener(this);

                int verticalPadding = getResources().getDimensionPixelSize(R.dimen.inner_frame_layout_padding);

                int mScreenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
                int mScreenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;

                int rightEdge = Math.min(binding.playerList.getMeasuredWidth(), mScreenWidth);
                int bottomEdge = Math.min(binding.playerList.getMeasuredHeight(), mScreenHeight);
                int verticalInset = (int) (FACTOR * Math.max(rightEdge, bottomEdge));

                binding.playerList.setPaddingRelative(
                        paddingStart,
                        (mIsRound ? verticalInset : verticalPadding),
                        paddingEnd,
                        paddingBottom + (mIsRound ? verticalInset : verticalPadding)
                );
            }
        });
        binding.playerList.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View v, MotionEvent event) {
                if (mActivity != null && event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {

                    // Don't forget the negation here
                    float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(mActivity);

                    // Swap these axes if you want to do horizontal scrolling instead
                    v.scrollBy(0, Math.round(delta));

                    return true;
                }

                return false;
            }
        });
        binding.playerList.requestFocus();

        mAdapter = new PlayerListAdapter(mActivity);
        mAdapter.setOnClickListener(new RecyclerOnClickListenerInterface() {
            @Override
            public void onClick(View view, int position) {
                if (onClickListener != null)
                    onClickListener.onClick(view, position);
            }
        });
        binding.playerList.setAdapter(mAdapter);

        binding.playerGroup.setVisibility(View.GONE);

        return outerView;
    }

    public void setOnClickListener(RecyclerOnClickListenerInterface listener) {
        this.onClickListener = listener;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    private void showProgressBar(final boolean show) {
        binding.progressBar.post(new Runnable() {
            @Override
            public void run() {
                if (binding == null) return;
                binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        Wearable.getDataClient(mActivity).addListener(this);

        binding.playerList.requestFocus();

        LocalBroadcastManager.getInstance(mActivity)
                .sendBroadcast(new Intent(WearableHelper.MusicPlayersPath));
        timer.start();
        getSelectedPlayerData();
    }

    private void getSelectedPlayerData() {
        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                String prefKey = null;
                try {
                    DataItemBuffer buff = Tasks.await(Wearable.getDataClient(mActivity)
                            .getDataItems(WearableHelper.getWearDataUri("*", SleepTimerHelper.SleepTimerAudioPlayerPath)));
                    for (int i = 0; i < buff.getCount(); i++) {
                        DataItem item = buff.get(i);
                        if (SleepTimerHelper.SleepTimerAudioPlayerPath.equals(item.getUri().getPath())) {
                            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                            prefKey = dataMap.getString(SleepTimerHelper.KEY_SELECTEDPLAYER, null);
                            break;
                        }
                    }
                    buff.release();
                } catch (ExecutionException | InterruptedException e) {
                    Logger.writeLine(Log.ERROR, e);
                    prefKey = null;
                }
                selectedPlayer.setKey(prefKey);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        timer.cancel();
        Wearable.getDataClient(mActivity).removeListener(this);
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        super.onDestroy();
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
        final String playerPref = selectedPlayer.getKey().getValue();
        MusicPlayerViewModel selectedPlayerModel = null;
        for (String key : supported_players) {
            DataMap map = dataMap.getDataMap(key);

            MusicPlayerViewModel model = new MusicPlayerViewModel();
            model.setAppLabel(map.getString(WearableHelper.KEY_LABEL));
            model.setPackageName(map.getString(WearableHelper.KEY_PKGNAME));
            model.setActivityName(map.getString(WearableHelper.KEY_ACTIVITYNAME));
            model.setBitmapIcon(ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(mActivity), map.getAsset(WearableHelper.KEY_ICON)));

            viewModels.add(model);

            if (playerPref != null && Objects.equals(model.getKey(), playerPref)) {
                selectedPlayerModel = model;
            }
        }

        if (selectedPlayerModel == null) {
            selectedPlayer.setKey(null);
        } else {
            selectedPlayer.setKey(selectedPlayerModel.getKey());
        }

        binding.playerList.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.updateItems(viewModels);

                binding.noplayersMessageview.setVisibility(viewModels.size() > 0 ? View.GONE : View.VISIBLE);
                binding.playerGroup.setVisibility(viewModels.size() > 0 ? View.VISIBLE : View.GONE);
                binding.playerList.post(new Runnable() {
                    @Override
                    public void run() {
                        if (binding.playerList.getVisibility() == View.VISIBLE && !binding.playerList.hasFocus()) {
                            binding.playerList.requestFocus();
                        }
                    }
                });
            }
        });
    }
}