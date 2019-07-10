package com.thewizrd.shared_resources;

import android.util.Log;

import com.thewizrd.shared_resources.utils.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class AsyncTask<T> {

    public T await(Callable<T> callable) {
        try {
            return Executors.newSingleThreadExecutor().submit(callable).get();
        } catch (InterruptedException | NullPointerException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw this.<RuntimeException>maskException(ex.getCause());
        }
    }

    public static void run(Runnable runnable) {
        try {
            new Thread(runnable).start();
        } catch (NullPointerException e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }

    protected <T extends Throwable> T maskException(Throwable t) throws T {
        throw (T) t;
    }
}