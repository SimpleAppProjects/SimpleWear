package com.thewizrd.shared_resources.tasks;

import com.google.android.gms.tasks.CancellationToken;

public class TaskUtils {
    public static void throwIfCancellationRequested(CancellationToken token) throws InterruptedException {
        if (token != null && token.isCancellationRequested()) {
            throw new InterruptedException();
        }
    }
}