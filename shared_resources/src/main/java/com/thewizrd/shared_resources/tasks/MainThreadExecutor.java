package com.thewizrd.shared_resources.tasks;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

public class MainThreadExecutor implements Executor {
    public static Executor Instance = new MainThreadExecutor();

    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(@NonNull Runnable runnable) {
        mMainHandler.post(runnable);
    }
}