// SPDX-License-Identifier: GPL-3.0-only
package android.net;

/** Per-thread accounting tag state. Host socket tagging/accounting is not implemented. */
public final class TrafficStats {
    public static final int UNSUPPORTED = -1;
    private static final ThreadLocal<Integer> TAG = new ThreadLocal<Integer>() {
        @Override protected Integer initialValue() { return -1; }
    };
    private TrafficStats() {}
    public static void setThreadStatsTag(int tag) { TAG.set(tag); }
    public static int getThreadStatsTag() { return TAG.get(); }
    public static int getAndSetThreadStatsTag(int tag) {
        int previous = TAG.get(); TAG.set(tag); return previous;
    }
    public static void clearThreadStatsTag() { TAG.remove(); }
}
