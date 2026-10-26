// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

final class QueueFixture {
    static void await(CountDownLatch latch) throws Exception {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Message queue timeout");
    }
    static void check() throws Exception {
        if (Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper() ||
            Looper.getMainLooper().getThread() != Thread.currentThread()) throw new AssertionError("Prepared main Looper");
        try { Looper.getMainLooper().quit(); throw new AssertionError("Main Looper quit accepted"); }
        catch (IllegalStateException expected) { }
        final HandlerThread worker = new HandlerThread("shiraz-queue-fixture");
        worker.start(); final Looper looper = worker.getLooper();
        if (looper == null || looper.getThread() != worker || worker.getThreadId() <= 0) throw new AssertionError("Worker Looper identity");
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        worker.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) { failure.set(error); }
        });
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        final StringBuilder order = new StringBuilder();
        final Handler handler = new Handler(looper, new Handler.Callback() {
            @Override public boolean handleMessage(Message message) {
                if (message.what != 7) return false;
                if (Thread.currentThread() != worker || message.arg1 != 3 || message.arg2 != 4 || !"copy".equals(message.getData().getString("key")))
                    throw new AssertionError("Message payload/dispatch thread");
                order.append("callback,"); return true;
            }
        }) { @Override public void handleMessage(Message message) { if (message.what != 8) throw new AssertionError("Unexpected message"); order.append("fallback,"); } };
        try {
            handler.post(new Runnable() { @Override public void run() {
                started.countDown();
                try { await(release); } catch (Exception error) { throw new AssertionError(error); }
            } });
            await(started);
            final long when = SystemClock.uptimeMillis() + 30;
            for (final String name : new String[]{"A", "B", "C"}) handler.postAtTime(new Runnable() { @Override public void run() {
                if (SystemClock.uptimeMillis() < when) throw new AssertionError("Delayed task ran early");
                order.append(name).append(',');
            } }, when);
            Runnable canceled = new Runnable() { @Override public void run() { throw new AssertionError("Canceled runnable ran"); } };
            handler.postAtTime(canceled, when);
            if (!handler.hasCallbacks(canceled)) throw new AssertionError("Pending callback lookup");
            handler.removeCallbacks(canceled);
            if (handler.hasCallbacks(canceled)) throw new AssertionError("Callback removal");
            Object token = new String("same"), other = new String("same");
            handler.sendMessageAtTime(Message.obtain(handler, 99, token), when);
            handler.sendMessageAtTime(Message.obtain(handler, 99, other), when);
            handler.removeMessages(99, token);
            if (handler.hasMessages(99, token) || !handler.hasMessages(99, other)) throw new AssertionError("Token identity filtering");
            handler.removeMessages(99);
            handler.postAtTime(canceled, token, when); handler.removeCallbacksAndMessages(token);
            Message source = Message.obtain(handler, 7, 3, 4); source.getData().putString("key", "copy");
            Message message = Message.obtain(source); source.getData().putString("key", "mutated"); source.recycle();
            handler.sendMessageAtTime(message, when);
            try { handler.sendMessage(message); throw new AssertionError("Duplicate message accepted"); } catch (IllegalStateException expected) { }
            try { message.recycle(); throw new AssertionError("Queued message recycled"); } catch (IllegalStateException expected) { }
            handler.sendEmptyMessageAtTime(8, when);
            handler.postAtFrontOfQueue(new Runnable() { @Override public void run() { order.append("front,"); } });
            handler.postAtTime(new Runnable() { @Override public void run() {
                handler.post(new Runnable() { @Override public void run() { order.append("safe"); done.countDown(); } });
                handler.postDelayed(new Runnable() { @Override public void run() { throw new AssertionError("Future task survived safe quit"); } }, Long.MAX_VALUE);
                looper.quitSafely();
            } }, when);
            release.countDown(); await(done); worker.join(3000);
            if (worker.isAlive()) throw new AssertionError("Safe quit did not stop worker");
            if (failure.get() != null) throw new AssertionError(failure.get());
            if (!order.toString().equals("front,A,B,C,callback,fallback,safe")) throw new AssertionError("Queue order: " + order);
            if (handler.post(canceled) || worker.getThreadId() != -1) throw new AssertionError("Dead queue accepted work");
        } finally { release.countDown(); looper.quit(); worker.join(3000); }
        final AtomicReference<Throwable> plainFailure = new AtomicReference<Throwable>();
        Thread plain = new Thread(new Runnable() { @Override public void run() {
            try {
                try { new Handler(); throw new AssertionError("Handler accepted an unprepared thread"); } catch (RuntimeException expected) { }
                Looper.prepare();
                try { Looper.prepare(); throw new AssertionError("Duplicate Looper accepted"); } catch (RuntimeException expected) { }
                Looper.myLooper().quit(); Looper.loop();
            } catch (Throwable error) { plainFailure.set(error); }
        } });
        plain.start(); plain.join(3000);
        if (plain.isAlive() || plainFailure.get() != null) throw new AssertionError("Plain thread prepare/quit", plainFailure.get());
        HandlerThread idle = new HandlerThread("shiraz-queue-idle"); idle.start();
        Handler future = new Handler(idle.getLooper());
        try {
            future.postDelayed(new Runnable() { @Override public void run() { throw new AssertionError("Immediate quit retained a future task"); } }, 10000);
            long deadline = SystemClock.uptimeMillis() + 1000;
            while (idle.getState() != Thread.State.TIMED_WAITING && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(1);
            if (idle.getState() != Thread.State.TIMED_WAITING) throw new AssertionError("Queue did not enter a timed wait");
            idle.quit(); idle.join(3000);
            if (idle.isAlive() || future.post(new Runnable() { @Override public void run() { } })) throw new AssertionError("Quit did not wake/reject queue work");
        } finally { idle.quit(); idle.join(3000); }
        long before = SystemClock.uptimeMillis(); SystemClock.sleep(2);
        long after = SystemClock.uptimeMillis();
        if (after < before + 2 || SystemClock.elapsedRealtimeNanos() / 1000000 < after || SystemClock.currentThreadTimeMillis() < 0)
            throw new AssertionError("Host monotonic clocks");
        System.out.println("SHIRAZ_FIXTURE_QUEUE_OK");
    }
}
