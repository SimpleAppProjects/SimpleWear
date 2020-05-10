package com.thewizrd.simplewear.sleeptimer;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.input.RotaryEncoder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.devadvance.circularseekbar.CircularSeekBar;
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.controls.SleepTimerViewModel;
import com.thewizrd.simplewear.databinding.FragmentSleeptimerStartBinding;

import java.util.Locale;

public class TimerStartFragment extends Fragment {

    private FragmentSleeptimerStartBinding binding;
    private SleepTimerViewModel viewModel;
    private OnBackPressedCallback backPressedCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory())
                .get(SleepTimerViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSleeptimerStartBinding.inflate(inflater, container, false);

        binding.timerProgressScroller.setShouldSaveColorState(false);
        binding.timerProgressScroller.setOnSeekBarChangeListener(new CircularSeekBar.OnCircularSeekBarChangeListener() {
            @Override
            public void onProgressChanged(CircularSeekBar circularSeekBar, int progress, boolean fromUser) {
                viewModel.setProgressTimeInMins(progress);
                setProgressText(progress);
                if (progress >= 1) {
                    binding.fab.post(new Runnable() {
                        @Override
                        public void run() {
                            binding.fab.show();
                        }
                    });
                } else {
                    binding.fab.post(new Runnable() {
                        @Override
                        public void run() {
                            binding.fab.hide();
                        }
                    });
                }
            }

            @Override
            public void onStopTrackingTouch(CircularSeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(CircularSeekBar seekBar) {
            }
        });
        binding.timerProgressScroller.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
                    // Don't forget the negation here
                    float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                            view.getContext());

                    // Swap these axes if you want to do horizontal scrolling instead
                    int sign = (int) Math.signum(delta);
                    if (sign > 0) {
                        binding.timerProgressScroller.setProgress(
                                Math.min(
                                        binding.timerProgressScroller.getProgress() + 1,
                                        binding.timerProgressScroller.getMax())
                        );
                    } else if (sign < 0) {
                        binding.timerProgressScroller.setProgress(
                                Math.max(binding.timerProgressScroller.getProgress() - 1, 0));
                    }

                    return true;
                }

                return false;
            }
        });
        binding.timerProgressScroller.requestFocus();

        binding.timerProgressScroller.setMax(SleepTimerViewModel.MAX_TIME_IN_MINS);
        binding.timerProgressScroller.setProgress(viewModel.getProgressTimeInMins());

        binding.minus5minbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.timerProgressScroller.setProgress(
                        Math.max(binding.timerProgressScroller.getProgress() - 5, 0)
                );
            }
        });
        binding.minus1minbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.timerProgressScroller.setProgress(
                        Math.max(binding.timerProgressScroller.getProgress() - 1, 0)
                );
            }
        });
        binding.plus1minbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.timerProgressScroller.setProgress(
                        Math.min(
                                binding.timerProgressScroller.getProgress() + 1,
                                binding.timerProgressScroller.getMax())
                );
            }
        });
        binding.plus5minbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.timerProgressScroller.setProgress(
                        Math.min(
                                binding.timerProgressScroller.getProgress() + 5,
                                binding.timerProgressScroller.getMax())
                );
            }
        });

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(new Intent(SleepTimerHelper.SleepTimerStartPath)
                                .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, viewModel.getProgressTimeInMins()));
            }
        });

        binding.bottomActionDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getFragmentManager() != null) {
                    getFragmentManager().beginTransaction()
                            .add(R.id.fragment_container, new MusicPlayersFragment())
                            .hide(TimerStartFragment.this)
                            .addToBackStack("players")
                            .commit();
                    backPressedCallback.setEnabled(true);
                }
            }
        });

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (getFragmentManager() != null) {
                    getFragmentManager().popBackStack("players", FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
                this.setEnabled(false);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

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
    public void onResume() {
        super.onResume();
        binding.timerProgressScroller.requestFocus();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setProgressText(int progress) {
        int hours = progress / 60;
        int minutes = progress - (hours * 60);

        if (hours > 0) {
            binding.progressText.setText(String.format(Locale.ROOT, "%02dh:%02dm", hours, minutes));
        } else {
            binding.progressText.setText(String.format(Locale.ROOT, "%02dm", minutes));
        }
    }
}
