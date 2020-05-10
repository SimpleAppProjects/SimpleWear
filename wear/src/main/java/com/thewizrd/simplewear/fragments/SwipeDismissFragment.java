package com.thewizrd.simplewear.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.wear.widget.SwipeDismissFrameLayout;

import com.thewizrd.simplewear.databinding.SwipeDismissLayoutBinding;

public class SwipeDismissFragment extends Fragment {
    protected FragmentActivity mActivity;

    private SwipeDismissLayoutBinding binding;
    private SwipeDismissFrameLayout.Callback swipeCallback;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (FragmentActivity) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mActivity = null;
    }

    protected void runOnUiThread(Runnable action) {
        if (mActivity != null)
            mActivity.runOnUiThread(action);
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        binding = SwipeDismissLayoutBinding.inflate(inflater, container, false);

        binding.swipeLayout.setSwipeable(true);
        swipeCallback = new SwipeDismissFrameLayout.Callback() {
            @Override
            public void onDismissed(SwipeDismissFrameLayout layout) {
                if (mActivity != null) mActivity.onBackPressed();
            }
        };
        binding.swipeLayout.addCallback(swipeCallback);

        return binding.swipeLayout;
    }

    @Override
    public void onDestroyView() {
        binding.swipeLayout.removeCallback(swipeCallback);
        super.onDestroyView();
        binding = null;
    }
}