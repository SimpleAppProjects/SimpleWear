package com.thewizrd.shared_resources.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncTask<T> {

    private static ExecutorService executorService;

    public T await(Callable<T> callable) {
        if (callable == null) return null;
        initExecutor();

        try {
            return executorService.submit(callable).get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw this.<RuntimeException>maskException(ex.getCause());
        }
    }

    private static void initExecutor() {
        if (executorService == null) {
            /*
             * Cached ThreadPoolExecutor
             * Based on implementation from https://stackoverflow.com/a/24493856
             */
            BlockingQueue<Runnable> queue = new LinkedTransferQueue<Runnable>() {
                @Override
                public boolean offer(Runnable e) {
                    return tryTransfer(e);
                }
            };
            ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, queue);
            threadPool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    try {
                        executor.getQueue().put(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            threadPool.allowCoreThreadTimeOut(true);

            executorService = threadPool;
        }
    }

    public static void run(Runnable runnable) {
        if (runnable == null) return;

        initExecutor();
        executorService.execute(runnable);
    }

    public static void run(final Runnable runnable, final long millisDelay) {
        run(runnable, millisDelay, null);
    }

    public static void run(final Runnable runnable, final long millisDelay, @Nullable final CancellationToken token) {
        initExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(millisDelay);
                } catch (InterruptedException ignored) {
                }

                if (token != null && token.isCancellationRequested())
                    return;

                if (runnable != null) runnable.run();
            }
        });
    }

    public static Task<Void> create(Callable<Void> callable) {
        initExecutor();
        return Tasks.call(executorService, callable);
    }

    public static void awaitTask(Callable<Void> callable) throws ExecutionException {
        initExecutor();
        try {
            executorService.submit(callable).get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }

    protected <T extends Throwable> T maskException(Throwable t) throws T {
        throw (T) t;
    }
}
