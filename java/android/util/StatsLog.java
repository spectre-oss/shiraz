// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Application breadcrumb logging API writing structured stats events to {@link EventLog}. */
public final class StatsLog {
    private static final int STATS_BREADCRUMB_TAG = 1937006964;
    private static final int STATE_START = 3;
    private static final int STATE_STOP = 2;
    private static final int STATE_UNSPECIFIED = 1;

    StatsLog() {}

    public static boolean logStart(int label) {
        EventLog.writeEvent(STATS_BREADCRUMB_TAG, Integer.valueOf(label), Integer.valueOf(STATE_START));
        return true;
    }

    public static boolean logStop(int label) {
        EventLog.writeEvent(STATS_BREADCRUMB_TAG, Integer.valueOf(label), Integer.valueOf(STATE_STOP));
        return true;
    }

    public static boolean logEvent(int label) {
        EventLog.writeEvent(STATS_BREADCRUMB_TAG, Integer.valueOf(label), Integer.valueOf(STATE_UNSPECIFIED));
        return true;
    }

    public static boolean logBinaryPushStateChanged(
            String trainName,
            long trainVersionCode,
            int options,
            int state,
            long[] experimentIds) {
        if (trainName == null) {
            return false;
        }
        int experimentCount = experimentIds != null ? experimentIds.length : 0;
        EventLog.writeEvent(
                STATS_BREADCRUMB_TAG,
                trainName,
                Long.valueOf(trainVersionCode),
                Integer.valueOf(options),
                Integer.valueOf(state),
                Integer.valueOf(experimentCount));
        return true;
    }
}
