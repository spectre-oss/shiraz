// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** {@link Printer} implementation forwarding lines to {@link Log#println(int, String, String)}. */
public class LogPrinter implements Printer {
    private final int mPriority;
    private final String mTag;

    public LogPrinter(int priority, String tag) {
        mPriority = priority;
        mTag = tag;
    }

    @Override
    public void println(String x) {
        Log.println(mPriority, mTag, x != null ? x : "");
    }
}
