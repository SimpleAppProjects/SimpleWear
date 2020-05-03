package com.thewizrd.simplewear.sleeptimer;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import com.thewizrd.simplewear.controls.MusicPlayerViewModel;
import com.thewizrd.simplewear.databinding.FragmentMusicplayersSleepBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class MusicPlayersFragment extends Fragment
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
                            DataItemBuffer buff = Tasks.await(Wearable.getDataClient(requireContext())
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

        selectedPlayer = new ViewModelProvider(requireActivity(), new ViewModelProvider.NewInstanceFactory())
                .get(SelectedPlayerViewModel.class);
        selectedPlayer.getKey().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                PutDataMapRequest mapRequest = PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath);
                mapRequest.getDataMap().putString(SleepTimerHelper.KEY_SELECTEDPLAYER, s);
                Wearable.getDataClient(requireActivity()).putDataItem(
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
        binding = FragmentMusicplayersSleepBinding.inflate(inflater, container, false);

        binding.playerList.setHasFixedSize(true);
        binding.playerList.setEdgeItemsCenteringEnabled(false);
        binding.playerList.setLayoutManager(new WearableLinearLayoutManager(requireContext(), null));

        mAdapter = new PlayerListAdapter(requireActivity());
        mAdapter.setOnClickListener(new RecyclerOnClickListenerInterface() {
            @Override
            public void onClick(View view, int position) {
                if (onClickListener != null)
                    onClickListener.onClick(view, position);
            }
        });
        binding.playerList.setAdapter(mAdapter);

        binding.playerGroup.setVisibility(View.GONE);

        return binding.getRoot();
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
        Wearable.getDataClient(requireContext()).addListener(this);

        LocalBroadcastManager.getInstance(requireContext())
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
                    DataItemBuffer buff = Tasks.await(Wearable.getDataClient(requireContext())
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
        Wearable.getDataClient(requireContext()).removeListener(this);
    }

    @Override
    public void onDestroy() {
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
                    Wearable.getDataClient(requireContext()), map.getAsset(WearableHelper.KEY_ICON)));

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
                if (binding.playerList.getVisibility() == View.VISIBLE && !binding.playerList.hasFocus()) {
                    binding.playerList.requestFocus();
                }
            }
        });
    }
}