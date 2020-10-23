package com.thewizrd.shared_resources.tasks;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.thewizrd.shared_resources.utils.Logger;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AsyncTask {
    private static final ExecutorService sThreadPool = Executors.newCachedThreadPool();

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     *
     * @param callable The task to run
     * @param <T>      The result type
     * @return The result of the task
     */
    public static <T> T await(@NonNull final Callable<T> callable) {
        return await(callable, (CancellationToken) null);
    }

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     * Task can be cancelled by passing {@link CancellationToken}; NOTE: Task thread will be interrupted
     *
     * @param callable The task to run
     * @param token    An optional token to pass to signal cancellation
     * @param <T>      The result type
     * @return The result of the task; Will return null if task if interrupted
     * @see CancellationToken
     */
    @Nullable
    public static <T> T await(@NonNull final Callable<T> callable, @Nullable CancellationToken token) {
        try {
            final Future<T> task = sThreadPool.submit(callable);
            if (token != null) {
                token.onCanceledRequested(new OnTokenCanceledListener() {
                    @Override
                    public void onCanceled() {
                        task.cancel(true);
                    }
                });
            }
            return task.get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw AsyncTask.<RuntimeException>maskException(ex.getCause());
        }
    }

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     * Task will be cancelled if {@link Lifecycle#getCurrentState()} is {@link Lifecycle.State#DESTROYED}; NOTE: Task thread will be interrupted
     *
     * @param callable  The task to run
     * @param lifecycle The lifecycle to observe
     * @param <T>       The result type
     * @return The result of the task; Will return null if task if interrupted
     * @see Lifecycle
     */
    @Nullable
    public static <T> T await(@NonNull final Callable<T> callable, @NonNull final Lifecycle lifecycle) {
        LifecycleEventObserver observer = null;
        try {
            final Future<T> task = sThreadPool.submit(callable);
            lifecycle.addObserver(observer = new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                    if (lifecycle.getCurrentState().compareTo(Lifecycle.State.DESTROYED) <= 0) {
                        lifecycle.removeObserver(this);
                        if (!task.isDone() && !task.isCancelled()) {
                            task.cancel(true);
                        }
                    }
                }
            });
            return task.get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw AsyncTask.<RuntimeException>maskException(ex.getCause());
        } finally {
            if (observer != null) {
                lifecycle.removeObserver(observer);
            }
        }
    }

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     *
     * @param callable The task to run
     * @param <T>      The result type
     * @param <X>      The checked exception type that may be thrown (masked from {@link ExecutionException#getCause()}
     * @return The result of the task
     * @throws X Checked exception
     */
    public static <T, X extends Exception> T await(@NonNull final CallableEx<T, X> callable) throws X {
        return await(callable, (CancellationToken) null);
    }

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     * Task can be cancelled by passing {@link CancellationToken}; NOTE: Task thread will be interrupted
     *
     * @param callable The task to run
     * @param token    An optional token to pass to signal cancellation
     * @param <T>      The result type
     * @param <X>      The checked exception type that may be thrown (masked from {@link ExecutionException#getCause()}
     * @return The result of the task; Will return null if task if interrupted
     * @throws X Checked exception
     * @see CancellationToken
     */
    @Nullable
    public static <T, X extends Exception> T await(@NonNull final CallableEx<T, X> callable, @Nullable CancellationToken token) throws X {
        try {
            final Future<T> task = sThreadPool.submit(callable);
            if (token != null) {
                token.onCanceledRequested(new OnTokenCanceledListener() {
                    @Override
                    public void onCanceled() {
                        task.cancel(true);
                    }
                });
            }
            return task.get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw AsyncTask.<RuntimeException>maskException(ex.getCause());
        }
    }

    /**
     * A blocking call which executes a task on a background thread and waits for the result
     * Equivalent to calling {@link Future#get()}
     * Task will be cancelled if {@link Lifecycle#getCurrentState()} is {@link Lifecycle.State#DESTROYED}; NOTE: Task thread will be interrupted
     *
     * @param callable  The task to run
     * @param lifecycle The lifecycle to observe
     * @param <T>       The result type
     * @param <X>       The checked exception type that may be thrown (masked from {@link ExecutionException#getCause()}
     * @return The result of the task; Will return null if task if interrupted
     * @throws X Checked exception
     * @see Lifecycle
     */
    @Nullable
    public static <T, X extends Exception> T await(@NonNull final CallableEx<T, X> callable, @NonNull final Lifecycle lifecycle) throws X {
        LifecycleEventObserver observer = null;
        try {
            final Future<T> task = sThreadPool.submit(callable);
            lifecycle.addObserver(observer = new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                    if (lifecycle.getCurrentState().compareTo(Lifecycle.State.DESTROYED) <= 0) {
                        lifecycle.removeObserver(this);
                        if (!task.isDone() && !task.isCancelled()) {
                            task.cancel(true);
                        }
                    }
                }
            });
            return task.get();
        } catch (InterruptedException e) {
            Logger.writeLine(Log.ERROR, e);
            return null;
        } catch (ExecutionException ex) {
            throw AsyncTask.<RuntimeException>maskException(ex.getCause());
        } finally {
            if (observer != null) {
                lifecycle.removeObserver(observer);
            }
        }
    }

    public static <T> T await(@NonNull Task<T> task) throws ExecutionException, InterruptedException {
        return Tasks.await(task);
    }

    public static <T> T await(@NonNull Task<T> task, long timeout, @NonNull TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return Tasks.await(task, timeout, unit);
    }

    /**
     * Executes the specified runnable on a background thread
     * Fire-and-forget task
     */
    public static void run(@NonNull final Runnable runnable) {
        sThreadPool.submit(runnable);
    }

    public static void run(@NonNull final Runnable runnable, @NonNull final Lifecycle lifecycle) {
        final Future<?> task = sThreadPool.submit(runnable);
        lifecycle.addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                if (lifecycle.getCurrentState().compareTo(Lifecycle.State.DESTROYED) <= 0) {
                    lifecycle.removeObserver(this);
                    if (!task.isDone() && !task.isCancelled()) {
                        task.cancel(true);
                    }
                }
            }
        });
    }

    /**
     * Executes the specified runnable on a background thread after a specified delay
     * Fire-and-forget task
     *
     * @param runnable    The task to run
     * @param millisDelay Delay (in milliseconds) before the task runs
     */
    public static void run(@NonNull final Runnable runnable, final long millisDelay) {
        run(runnable, millisDelay, null);
    }

    /**
     * Executes the specified runnable on a background thread after a specified delay
     * Fire-and-forget task
     * Task can be cancelled by passing {@link CancellationToken}; NOTE: Task thread will be interrupted
     *
     * @param runnable    The task to run
     * @param millisDelay Delay (in milliseconds) before the task runs
     * @param token       An optional token to pass to signal cancellation
     * @see CancellationToken
     */
    public static void run(@NonNull final Runnable runnable, final long millisDelay, @Nullable final CancellationToken token) {
        final Future<?> task = sThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (millisDelay > 0) {
                        Thread.sleep(millisDelay);
                    }
                    runnable.run();
                } catch (InterruptedException e) {
                    Logger.writeLine(Log.ERROR, e);
                }
            }
        });

        if (token != null) {
            token.onCanceledRequested(new OnTokenCanceledListener() {
                @Override
                public void onCanceled() {
                    task.cancel(true);
                }
            });
        }
    }

    /**
     * Creates a {@link Task} which is run on a background thread
     *
     * @param callable The task to run
     * @param <T>      The result type
     * @return The task object
     */
    @NonNull
    public static <T> Task<T> create(@NonNull final Callable<T> callable) {
        return create(callable, (CancellationToken) null);
    }

    /**
     * Creates a {@link Task} which is run on a background thread
     * Task can be cancelled by passing {@link CancellationToken}; NOTE: Task thread will be interrupted
     *
     * @param callable The task to run
     * @param token    An optional token to pass to signal cancellation
     * @return The task object
     * @see CancellationToken
     */
    @NonNull
    public static <T> Task<T> create(@NonNull final Callable<T> callable, @Nullable CancellationToken token) {
        final TaskCompletionSource<T> tcs;
        if (token != null) {
            tcs = new TaskCompletionSource<>(token);
        } else {
            tcs = new TaskCompletionSource<>();
        }

        final ListenableFuture<?> task = Futures.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    T result = callable.call();
                    tcs.setResult(result);
                } catch (Exception e) {
                    tcs.setException(e);
                }
            }
        }, sThreadPool);

        if (token != null) {
            token.onCanceledRequested(new OnTokenCanceledListener() {
                @Override
                public void onCanceled() {
                    task.cancel(true);
                }
            });
        }

        Futures.addCallback(task, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@NullableDecl Object result) {
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                tcs.trySetException((Exception) t);
            }
        }, MainThreadExecutor.Instance);

        return tcs.getTask();
    }

    /**
     * Creates a {@link Task} which is run on a background thread
     * Task will be cancelled if {@link Lifecycle#getCurrentState()} is {@link Lifecycle.State#DESTROYED}; NOTE: Task thread will be interrupted
     *
     * @param callable  The task to run
     * @param lifecycle The lifecycle to observe
     * @return The task object
     * @see Lifecycle
     */
    @NonNull
    @MainThread
    public static <T> Task<T> create(@NonNull final Callable<T> callable, @NonNull final Lifecycle lifecycle) {
        final TaskCompletionSource<T> tcs = new TaskCompletionSource<>();

        final ListenableFuture<?> task = Futures.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    T result = callable.call();
                    tcs.setResult(result);
                } catch (Exception e) {
                    tcs.setException(e);
                }
            }
        }, sThreadPool);

        final LifecycleEventObserver observer = new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                if (lifecycle.getCurrentState().compareTo(Lifecycle.State.DESTROYED) <= 0) {
                    lifecycle.removeObserver(this);
                    if (!task.isDone() && !task.isCancelled()) {
                        task.cancel(true);
                    }
                }
            }
        };
        lifecycle.addObserver(observer);

        Futures.addCallback(task, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@NullableDecl Object result) {
                lifecycle.removeObserver(observer);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                lifecycle.removeObserver(observer);
                tcs.trySetException((Exception) t);
            }
        }, MainThreadExecutor.Instance);

        return tcs.getTask();
    }

    protected static <T extends Throwable> T maskException(Throwable t) throws T {
        throw (T) t;
    }
}
