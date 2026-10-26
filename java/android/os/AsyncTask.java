// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One host session's background work with completion on the prepared main Looper. */
public abstract class AsyncTask<Params, Progress, Result> {
    public enum Status { PENDING, RUNNING, FINISHED }
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadFactory FACTORY = new ThreadFactory() {
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Shiraz AsyncTask #" + IDS.incrementAndGet());
            thread.setDaemon(true); return thread;
        }
    };
    private static ThreadPoolExecutor pool(int core, int maximum) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(core, maximum, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(128), FACTORY, new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
    public static final Executor THREAD_POOL_EXECUTOR = pool(Math.max(8, Runtime.getRuntime().availableProcessors()), Math.max(16, Runtime.getRuntime().availableProcessors() * 2 + 1));
    public static final Executor SERIAL_EXECUTOR = pool(1, 1);
    private static volatile boolean closing;
    private static volatile Executor defaultExecutor = SERIAL_EXECUTOR;
    private static final ConcurrentHashMap<Object, AsyncTask<?, ?, ?>> active = new ConcurrentHashMap<Object, AsyncTask<?, ?, ?>>();
    private final Object token = new Object();
    private final Handler handler;
    private final AtomicBoolean canceled = new AtomicBoolean(), posted = new AtomicBoolean();
    private volatile boolean aborted;
    private volatile Status status = Status.PENDING;
    private boolean invoked;
    private Params[] parameters;
    private final FutureTask<Result> future;

    public AsyncTask() {
        if (Looper.getMainLooper() == null) throw new IllegalStateException("AsyncTask requires a prepared main Looper");
        handler = new Handler(Looper.getMainLooper());
        future = new FutureTask<Result>(new Callable<Result>() {
            public Result call() throws Exception {
                synchronized (AsyncTask.this) {
                    if (future.isCancelled()) return null;
                    invoked = true;
                }
                Result result = null; Throwable failure = null;
                try {
                    if (Looper.myLooper() != Looper.getMainLooper() && Process.getThreadPriority(Process.myTid()) < Process.THREAD_PRIORITY_BACKGROUND)
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    result = doInBackground(parameters);
                    return result;
                } catch (Throwable error) {
                    failure = error;
                    throw error;
                } finally { complete(result, failure); }
            }
        }) {
            @Override protected void done() {
                try { get(); }
                catch (CancellationException error) {
                    synchronized (AsyncTask.this) { if (!invoked) complete(null, null); }
                } catch (ExecutionException error) { complete(null, error.getCause()); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); complete(null, error); }
            }
        };
    }
    private void complete(final Result result, final Throwable failure) {
        if (!posted.compareAndSet(false, true)) return;
        if (closing) { parameters = null; status = Status.FINISHED; active.remove(token); return; }
        Runnable finish = new Runnable() { public void run() {
            try {
                if (aborted || closing) return;
                if (failure != null && !future.isCancelled()) throw new RuntimeException("AsyncTask background execution failed", failure);
                if (isCancelled()) onCancelled(result); else onPostExecute(result);
            } finally { parameters = null; status = Status.FINISHED; active.remove(token); }
        } };
        try {
            if (handler.post(finish)) return;
        } catch (RuntimeException error) {
            parameters = null; status = Status.FINISHED; active.remove(token); throw error;
        }
        parameters = null; status = Status.FINISHED; active.remove(token);
    }
    protected abstract Result doInBackground(Params... params);
    protected void onPreExecute() { }
    protected void onPostExecute(Result result) { }
    protected void onProgressUpdate(Progress... values) { }
    protected void onCancelled(Result result) { onCancelled(); }
    protected void onCancelled() { }
    public final Status getStatus() { return status; }
    public final boolean isCancelled() { return canceled.get(); }
    public final synchronized boolean cancel(boolean mayInterruptIfRunning) { canceled.set(true); return future.cancel(mayInterruptIfRunning); }
    public final Result get() throws InterruptedException, ExecutionException { return future.get(); }
    public final Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return future.get(timeout, unit); }
    public static void setDefaultExecutor(Executor executor) { if (executor == null) throw new NullPointerException("executor"); defaultExecutor = executor; }
    @SafeVarargs public final AsyncTask<Params, Progress, Result> execute(Params... params) { return executeOnExecutor(defaultExecutor, params); }
    @SafeVarargs public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor executor, Params... params) {
        if (executor == null) throw new NullPointerException("executor");
        if (closing) throw new RejectedExecutionException("AsyncTask session has ended");
        // Like AOSP, pre-execution runs on the caller; completion still uses the main handler.
        synchronized (this) {
            if (status != Status.PENDING) throw new IllegalStateException("An AsyncTask may execute only once");
            status = Status.RUNNING; parameters = params; active.put(token, this);
        }
        try { onPreExecute(); executor.execute(future); }
        catch (Throwable error) {
            aborted = true; posted.set(true); canceled.set(true); future.cancel(false); parameters = null; status = Status.FINISHED; active.remove(token); throw error;
        }
        return this;
    }
    @SafeVarargs protected final void publishProgress(final Progress... values) {
        if (!closing && !isCancelled() && status == Status.RUNNING) handler.post(new Runnable() { public void run() {
            if (!closing && !isCancelled() && status == Status.RUNNING) onProgressUpdate(values);
        } });
    }
    public static void execute(final Runnable runnable) {
        if (runnable == null) throw new NullPointerException("runnable");
        if (closing) throw new RejectedExecutionException("AsyncTask session has ended");
        final Handler handler = new Handler(Looper.getMainLooper());
        defaultExecutor.execute(new Runnable() { public void run() {
            try { runnable.run(); }
            catch (final Throwable error) { if (!closing) handler.post(new Runnable() { public void run() { if (!closing) throw new RuntimeException("AsyncTask runnable failed", error); } }); }
        } });
    }
    /** The standalone app process owns these pools; custom executors remain caller-owned. */
    public static void shirazShutdown() {
        closing = true;
        for (AsyncTask<?, ?, ?> task : active.values()) task.cancel(true);
        ((ThreadPoolExecutor) SERIAL_EXECUTOR).shutdownNow();
        ((ThreadPoolExecutor) THREAD_POOL_EXECUTOR).shutdownNow();
        boolean interrupted = false;
        for (Executor executor : new Executor[]{SERIAL_EXECUTOR, THREAD_POOL_EXECUTOR}) {
            try { ((ThreadPoolExecutor) executor).awaitTermination(250, TimeUnit.MILLISECONDS); }
            catch (InterruptedException error) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        active.clear();
    }
}
