// SPDX-License-Identifier: GPL-3.0-only
import android.os.Process;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ThreadIdentitySmoke {
    static { System.load(new java.io.File("build/macos/libshiraz-tid-fixture.so").getAbsolutePath()); }
    private static native int nativeTid();
    private static native boolean nativeIsDarwinMain();
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        int main = Process.myTid();
        System.out.println("THREAD_IDENTITY_MAIN darwin_main=" + nativeIsDarwinMain() + " pid_matches_tid=" + (main == Process.myPid()));
        if (nativeIsDarwinMain()) check(main == Process.myPid(), "process leader identity");
        check(main > 0 && main < (1 << 22) && main == nativeTid(), "main Java/native identity");
        check(Process.getThreadPriority(0) == Process.getThreadPriority(main), "current priority");
        final int count = 8;
        final int[] ids = new int[count];
        final Thread[] workers = new Thread[count];
        final CountDownLatch started = new CountDownLatch(count), stop = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        for (int i = 0; i < count; ++i) {
            final int index = i;
            workers[i] = new Thread(new Runnable() { public void run() {
                try {
                    int tid = Process.myTid();
                    ids[index] = tid;
                    check(tid > 0 && tid < (1 << 22) && tid == nativeTid(), "worker Java/native identity");
                    Process.getThreadPriority(tid);
                } catch (Throwable failure) { error.compareAndSet(null, failure); }
                finally { started.countDown(); }
                try { stop.await(); } catch (InterruptedException failure) { error.compareAndSet(null, failure); }
            }});
            workers[i].start();
        }
        started.await();
        try {
            if (error.get() != null) throw new AssertionError(error.get());
            HashSet<Integer> unique = new HashSet<Integer>();
            unique.add(main);
            for (int tid : ids) {
                check(unique.add(tid), "live identities must be distinct");
                Process.getThreadPriority(tid);
            }
        } finally {
            stop.countDown();
            for (Thread worker : workers) worker.join();
        }
        for (int tid : ids) {
            // Java join can complete before the host pthread teardown finishes.
            long deadline = System.nanoTime() + 2_000_000_000L;
            boolean gone = false;
            while (!gone && System.nanoTime() < deadline) {
                try { Process.getThreadPriority(tid); Thread.yield(); }
                catch (IllegalArgumentException expected) { gone = true; }
            }
            check(gone, "terminated host thread still has a priority");
        }
        System.out.println("THREAD_IDENTITY_SMOKE_PASS");
    }
}
