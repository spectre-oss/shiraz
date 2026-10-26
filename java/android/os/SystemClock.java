// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public final class SystemClock {
    static { System.loadLibrary("shiraz-host"); }
    private SystemClock() { }
    public static native long uptimeMillis();
    public static native long elapsedRealtime();
    public static native long elapsedRealtimeNanos();
    public static long currentThreadTimeMillis() { return threadCpuNanos() / 1000000; }
    private static native long threadCpuNanos();
    public static void sleep(long millis) {
        if (millis <= 0) return;
        long start = uptimeMillis(), remaining = millis;
        boolean interrupted = false;
        while (remaining > 0) {
            try { Thread.sleep(remaining); } catch (InterruptedException error) { interrupted = true; }
            remaining = millis - (uptimeMillis() - start);
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
