package com.thewizrd.simplewear.sleeptimer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.simplewear.databinding.FragmentSleeptimerStopBinding;

import java.util.Locale;

import static com.thewizrd.shared_resources.utils.SerializationUtils.bytesToString;

public class TimerProgressFragment extends Fragment implements MessageClient.OnMessageReceivedListener {

    private FragmentSleeptimerStopBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSleeptimerStopBinding.inflate(inflater, container, false);

        binding.timerProgressScroller.setIsTouchEnabled(false);
        binding.timerProgressScroller.setShouldSaveColorState(false);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(new Intent(SleepTimerHelper.SleepTimerStopPath));
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        // stringToBytes(String.format(Locale.ROOT, "%d;%d", timeStartInMillis, timeInMillis)));
        if (messageEvent.getPath().equals(SleepTimerHelper.SleepTimerStatusPath)) {
            String data = bytesToString(messageEvent.getData());
            String[] datas = data.split(";");
            long startTimeMs = Long.parseLong(datas[0]);
            long progressMs = Long.parseLong(datas[1]);

            binding.timerProgressScroller.setMax((int) startTimeMs);
            binding.timerProgressScroller.setProgress((int) (startTimeMs - progressMs));
            setProgressText(progressMs);
        }
    }

    private void setProgressText(long progressMs) {
        long hours = progressMs / 3600000L;
        long mins = progressMs % 3600000L / 60000L;
        long secs = (progressMs / 1000) % 60;

        if (hours > 0) {
            binding.progressText.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", hours, mins, secs));
        } else if (mins > 0) {
            binding.progressText.setText(String.format(Locale.ROOT, "%02d:%02d", mins, secs));
        } else {
            binding.progressText.setText(String.format(Locale.ROOT, "%02d", secs));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Wearable.getMessageClient(requireActivity()).addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Wearable.getMessageClient(requireActivity()).removeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
