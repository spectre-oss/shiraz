// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.os.NetworkOnMainThreadException;
import android.os.StrictMode;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.concurrent.atomic.AtomicReference;

final class StrictFixture {
    private static void read(File file) throws IOException { try (FileInputStream input = new FileInputStream(file)) { input.read(); } }
    private static void write(File file) throws IOException { try (FileOutputStream output = new FileOutputStream(file)) { output.write(42); } }
    private static void expectDeath(Runnable operation, String name) {
        try { operation.run(); throw new AssertionError("Policy did not detect " + name); }
        catch (RuntimeException expected) { if (expected.getMessage() == null || !expected.getMessage().contains(name)) throw expected; }
    }
    static void check(Context context) throws Exception {
        File file = new File(context.getCacheDir(), "strict-fixture.txt");
        write(file);
        StrictMode.ThreadPolicy original = StrictMode.getThreadPolicy();
        StrictMode.VmPolicy originalVm = StrictMode.getVmPolicy();
        try {
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); Socket socket = new Socket()) {
                try { socket.connect(listener.getLocalSocketAddress(), 1000); throw new AssertionError("Main-thread network default missing"); }
                catch (NetworkOnMainThreadException expected) { }
            }
            StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyDeath();
            StrictMode.ThreadPolicy reads = builder.build();
            builder.permitAll(); // Mutating a builder must not mutate a built policy.
            StrictMode.setThreadPolicy(reads);
            try { read(file); throw new AssertionError("Disk read allowed"); }
            catch (RuntimeException expected) { if (!expected.getMessage().contains("disk read")) throw expected; }
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(new Runnable() { @Override public void run() {
                try {
                    if (StrictMode.getThreadPolicy() != StrictMode.ThreadPolicy.LAX) throw new AssertionError("Policy inherited across threads");
                    read(file);
                } catch (Throwable error) { failure.set(error); }
            } });
            worker.start(); worker.join();
            if (failure.get() != null) throw new AssertionError(failure.get());
            if (StrictMode.allowThreadDiskReads() != reads) throw new AssertionError("Previous policy identity");
            read(file);
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(reads).detectDiskWrites().build());
            try { write(file); throw new AssertionError("Disk write allowed"); }
            catch (RuntimeException expected) { if (!expected.getMessage().contains("disk write")) throw expected; }
            StrictMode.ThreadPolicy beforeWrites = StrictMode.allowThreadDiskWrites();
            read(file); write(file);
            StrictMode.setThreadPolicy(beforeWrites);
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectCustomSlowCalls().penaltyDeath().build());
            expectDeath(new Runnable() { @Override public void run() { StrictMode.noteSlowCall("fixture"); } }, "slow call");
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyDeath().build());
            android.util.Log.w("ShirazFixture", "SHIRAZ_FIXTURE_LOG_POLICY_OK");
            expectDeath(new Runnable() { @Override public void run() { StrictMode.noteSlowCall("after logger"); } }, "slow call");
            System.setProperty("log.tag.StrictMode", "DEBUG");
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().build());
            read(file); // A default log penalty must permit the operation after reporting.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
            try { new StrictMode.VmPolicy.Builder().detectAll(); throw new AssertionError("Unsupported VM detectors silently accepted"); }
            catch (UnsupportedOperationException expected) { }
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyDeath().build());
            Class<?> guardClass = Class.forName("dalvik.system.CloseGuard");
            Object guard = guardClass.getMethod("get").invoke(null);
            guardClass.getMethod("open", String.class).invoke(guard, "close");
            try {
                try { guardClass.getMethod("warnIfOpen").invoke(guard); throw new AssertionError("CloseGuard leak not detected"); }
                catch (InvocationTargetException expected) {
                    if (!(expected.getCause() instanceof RuntimeException) || !expected.getCause().getMessage().contains("unclosed resource")) throw expected;
                }
            } finally { guardClass.getMethod("close").invoke(guard); }
        } finally {
            StrictMode.setThreadPolicy(original); StrictMode.setVmPolicy(originalVm);
            if (!file.delete()) throw new AssertionError("Fixture cleanup");
        }
        System.out.println("SHIRAZ_FIXTURE_STRICTMODE_OK");
    }
}
