// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class AsyncFixture {
    private final ArrayList<AsyncTask<?, ?, ?>> tasks = new ArrayList<AsyncTask<?, ?, ?>>();
    private final AtomicInteger callbacks = new AtomicInteger();
    private int progress;
    private void ui() { if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("AsyncTask callback thread"); }
    private void background() {
        if (Looper.myLooper() == Looper.getMainLooper() || StrictMode.getThreadPolicy() != StrictMode.ThreadPolicy.LAX ||
            Process.getThreadPriority(Process.myTid()) < Process.THREAD_PRIORITY_BACKGROUND) throw new AssertionError("AsyncTask worker identity/policy/priority");
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("AsyncTask worker timeout"); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }
    private class Task extends AsyncTask<Integer, Integer, Integer> {
        boolean pre;
        final void checkBackground() { AsyncFixture.this.background(); if (!pre) throw new AssertionError("Work ran before onPreExecute"); }
        @Override protected void onPreExecute() { ui(); if (getStatus() != Status.RUNNING) throw new AssertionError("Pre-execute status"); pre = true; }
        @Override protected Integer doInBackground(Integer... params) { checkBackground(); if (!pre) throw new AssertionError("Pre-execute ordering"); return params[0]; }
        @Override protected void onPostExecute(Integer result) { ui(); callbacks.incrementAndGet(); }
        @Override protected void onCancelled(Integer result) { ui(); callbacks.incrementAndGet(); }
    }
    void start() throws Exception {
        ui();
        if (System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") != null) {
            if ("runnable".equals(System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL"))) {
                AsyncTask.execute(new Runnable() { public void run() { throw new IllegalArgumentException("SHIRAZ_FIXTURE_EXPECTED_ASYNC_FAILURE"); } });
                return;
            }
            AsyncTask<Void, Void, Void> failure = new AsyncTask<Void, Void, Void>() {
                @Override protected Void doInBackground(Void... params) { throw new IllegalArgumentException("SHIRAZ_FIXTURE_EXPECTED_ASYNC_FAILURE"); }
                @Override protected void onPostExecute(Void result) { throw new AssertionError("Failed task completed successfully"); }
            };
            failure.execute();
            try { failure.get(3, TimeUnit.SECONDS); throw new AssertionError("Worker failure hidden from get"); }
            catch (ExecutionException expected) { if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected; }
            return;
        }
        final java.util.concurrent.atomic.AtomicReference<Throwable> submissionFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        final Task[] submitted=new Task[1];
        Thread submitter=new Thread(new Runnable(){public void run(){try{
            final Thread caller=Thread.currentThread();
            Task task=new Task(){@Override protected void onPreExecute(){if(Thread.currentThread()!=caller||Looper.myLooper()==Looper.getMainLooper())throw new AssertionError("Pre-execution must stay on submitter");pre=true;}};
            submitted[0]=task;task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,73);
            if(task.get(3,TimeUnit.SECONDS)!=73)throw new AssertionError("Worker-submitted task result");
        }catch(Throwable failure){submissionFailure.set(failure);}}},"fixture-task-submitter");
        submitter.start();submitter.join(4000);if(submitter.isAlive())throw new AssertionError("Worker submission stalled");
        if(submissionFailure.get()!=null)throw new AssertionError("Worker task submission failed",submissionFailure.get());
        tasks.add(submitted[0]);
        final StringBuilder order = new StringBuilder();
        final CountDownLatch serialStarted = new CountDownLatch(1), serialRelease = new CountDownLatch(1);
        Task first = new Task() { @Override protected Integer doInBackground(Integer... params) { checkBackground(); serialStarted.countDown(); await(serialRelease); order.append('A'); return 1; } };
        Task second = new Task() { @Override protected Integer doInBackground(Integer... params) { checkBackground(); order.append('B'); return 2; } };
        tasks.add(first); tasks.add(second);
        try {
            if (first.getStatus() != AsyncTask.Status.PENDING) throw new AssertionError("Initial status");
            first.execute(1); await(serialStarted); second.execute(2);
            if (order.length() != 0) throw new AssertionError("Default executor ran serial tasks concurrently");
        } finally { serialRelease.countDown(); }
        if (first.get(3, TimeUnit.SECONDS) != 1 || second.get(3, TimeUnit.SECONDS) != 2 || !order.toString().equals("AB") || first.getStatus() != AsyncTask.Status.RUNNING)
            throw new AssertionError("Serial work/result or callback ran inline");
        try { first.execute(1); throw new AssertionError("Task executed twice"); } catch (IllegalStateException expected) { }
        final CountDownLatch parallelStarted = new CountDownLatch(2), parallelRelease = new CountDownLatch(1);
        Task a = new Task() { @Override protected Integer doInBackground(Integer... params) { checkBackground(); parallelStarted.countDown(); await(parallelRelease); return 3; } };
        Task b = new Task() { @Override protected Integer doInBackground(Integer... params) { checkBackground(); parallelStarted.countDown(); await(parallelRelease); return 4; } };
        tasks.add(a); tasks.add(b);
        try { a.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 3); b.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 4); await(parallelStarted); }
        finally { parallelRelease.countDown(); }
        if (a.get(3, TimeUnit.SECONDS) != 3 || b.get(3, TimeUnit.SECONDS) != 4) throw new AssertionError("Parallel results");
        Task reporting = new Task() {
            @Override protected Integer doInBackground(Integer... params) { checkBackground(); publishProgress(7, 9); return 5; }
            @Override protected void onProgressUpdate(Integer... values) { ui(); if (values.length != 2 || values[0] != 7 || values[1] != 9) throw new AssertionError("Progress payload"); ++progress; }
        };
        tasks.add(reporting); reporting.execute(5); reporting.get(3, TimeUnit.SECONDS);
        final Runnable[] held = new Runnable[1];
        final AtomicInteger pendingRuns = new AtomicInteger();
        Task pending = new Task() {
            @Override protected Integer doInBackground(Integer... params) { pendingRuns.incrementAndGet(); throw new AssertionError("Canceled pending task ran"); }
            @Override protected void onCancelled(Integer result) { ui(); if (result != null) throw new AssertionError("Pending cancel result"); callbacks.incrementAndGet(); }
            @Override protected void onPostExecute(Integer result) { throw new AssertionError("Canceled task called onPostExecute"); }
        };
        tasks.add(pending); pending.executeOnExecutor(new Executor() { public void execute(Runnable runnable) { held[0] = runnable; } }, 6);
        if (!pending.cancel(false) || pending.cancel(true) || !pending.isCancelled()) throw new AssertionError("Cancellation return/state");
        held[0].run();
        if (pendingRuns.get() != 0) throw new AssertionError("Pending cancellation allowed background side effects");
        try { pending.get(); throw new AssertionError("Canceled future returned a result"); } catch (CancellationException expected) { }
        final CountDownLatch runningStarted = new CountDownLatch(1), runningRelease = new CountDownLatch(1), runningExited = new CountDownLatch(1);
        Task running = new Task() {
            @Override protected Integer doInBackground(Integer... params) { checkBackground(); runningStarted.countDown(); await(runningRelease); return 42; }
            @Override protected void onCancelled(Integer result) { ui(); if (result == null || result != 42) throw new AssertionError("Running cancel lost the produced result"); callbacks.incrementAndGet(); }
        };
        tasks.add(running);
        running.executeOnExecutor(tracked(runningExited), 42); await(runningStarted); if (!running.cancel(false)) throw new AssertionError("Running cancellation rejected"); runningRelease.countDown(); await(runningExited);
        final CountDownLatch interruptStarted = new CountDownLatch(1), interruptExited = new CountDownLatch(1);
        Task interrupted = new Task() {
            @Override protected Integer doInBackground(Integer... params) {
                checkBackground(); interruptStarted.countDown();
                try { new CountDownLatch(1).await(); throw new AssertionError("Interrupted wait returned normally"); }
                catch (InterruptedException expected) { throw new RuntimeException("Canceled interrupted task", expected); }
            }
            @Override protected void onCancelled(Integer result) { ui(); if (result != null) throw new AssertionError("Interrupted result"); callbacks.incrementAndGet(); }
        };
        tasks.add(interrupted); interrupted.executeOnExecutor(tracked(interruptExited), 8); await(interruptStarted); if (!interrupted.cancel(true)) throw new AssertionError("Interrupt cancellation rejected"); await(interruptExited);
        Task rejected = new Task();
        try { rejected.executeOnExecutor(new Executor() { public void execute(Runnable runnable) { throw new RejectedExecutionException("fixture"); } }, 9); throw new AssertionError("Rejected executor accepted"); }
        catch (RejectedExecutionException expected) { }
        if (rejected.getStatus() != AsyncTask.Status.FINISHED || !rejected.pre) throw new AssertionError("Rejected task status/pre-execute");
        Task badPre = new Task() { @Override protected void onPreExecute() { throw new IllegalStateException("fixture pre"); } };
        try { badPre.execute(0); throw new AssertionError("Pre-execute failure swallowed"); }
        catch (IllegalStateException expected) { if (!"fixture pre".equals(expected.getMessage())) throw expected; }
        if (badPre.getStatus() != AsyncTask.Status.FINISHED) throw new AssertionError("Pre-execute failure status");
        Task checkedPre = new Task() { @Override protected void onPreExecute() { AsyncFixture.<RuntimeException>raise(new java.io.IOException("checked pre")); } };
        try { checkedPre.execute(0); throw new AssertionError("Checked callback failure swallowed"); }
        catch (Throwable expected) { if (!(expected instanceof java.io.IOException)) throw new AssertionError("Callback exception type changed", expected); }
        if (checkedPre.getStatus() != AsyncTask.Status.FINISHED) throw new AssertionError("Checked callback failure left a pending task");
        Task canceledPre = new Task() {
            @Override protected void onPreExecute() { cancel(false); throw new IllegalStateException("canceled pre"); }
            @Override protected void onCancelled(Integer value) { throw new AssertionError("Aborted submission delivered a stale cancellation callback"); }
        };
        try { canceledPre.execute(0); throw new AssertionError("Canceled pre-execute error swallowed"); }
        catch (IllegalStateException expected) { if (!"canceled pre".equals(expected.getMessage())) throw expected; }
        final CountDownLatch runnable = new CountDownLatch(1);
        AsyncTask.execute(new Runnable() { public void run() { if (Looper.myLooper() == Looper.getMainLooper()) throw new AssertionError("Static work ran inline"); runnable.countDown(); } });
        await(runnable);
        final CountDownLatch saturated = new CountDownLatch(1), unblock = new CountDownLatch(1), drained = new CountDownLatch(128);
        AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() { public void run() { saturated.countDown(); await(unblock); } });
        try {
            await(saturated);
            Runnable queued = new Runnable() { public void run() { drained.countDown(); } };
            for (int i = 0; i < 128; ++i) AsyncTask.SERIAL_EXECUTOR.execute(queued);
            try { AsyncTask.SERIAL_EXECUTOR.execute(queued); throw new AssertionError("Serial queue exceeded its capacity"); }
            catch (RejectedExecutionException expected) { }
        } finally { unblock.countDown(); }
        await(drained);
        if (callbacks.get() != 0 || progress != 0) throw new AssertionError("UI callbacks ran while main thread was still in onCreate");
    }
    @SuppressWarnings("unchecked") private static <T extends Throwable> void raise(Throwable error) throws T { throw (T) error; }
    private Executor tracked(final CountDownLatch exited) {
        return new Executor() { public void execute(final Runnable runnable) { AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() { public void run() {
            try { runnable.run(); } finally { exited.countDown(); }
        } }); } };
    }
    void teardown(android.content.Context context) {
        if (!"1".equals(System.getenv("SHIRAZ_FIXTURE_ASYNC_TEARDOWN"))) return;
        final CountDownLatch started = new CountDownLatch(2);
        for (int i = 0; i < 2; ++i) {
            final java.io.File marker = new java.io.File(context.getCacheDir(), "async-teardown-" + i);
            if (marker.exists() && !marker.delete()) throw new AssertionError("Cannot reset teardown marker");
            new AsyncTask<Void, Void, Void>() {
                @Override public boolean equals(Object other) { return other instanceof AsyncTask; }
                @Override public int hashCode() { return 7; }
                @Override protected Void doInBackground(Void... params) {
                    started.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException expected) {
                        if (!isCancelled()) throw new AssertionError("Shutdown interrupted a worker without canceling its task");
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(marker)) { output.write(new byte[]{'o','k','\n'}); }
                        catch (java.io.IOException error) { throw new AssertionError(error); }
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        await(started);
    }
    void verify() {
        if (System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") != null) return;
        ui();
        if (callbacks.get() != tasks.size() || progress != 1) throw new AssertionError("AsyncTask callbacks/progress: " + callbacks.get() + "/" + tasks.size());
        for (AsyncTask<?, ?, ?> task : tasks) if (task.getStatus() != AsyncTask.Status.FINISHED) throw new AssertionError("Task did not reach FINISHED");
        System.out.println("SHIRAZ_FIXTURE_ASYNC_OK");
    }
}
