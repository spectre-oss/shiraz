// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.util.ArrayList;

/**
 * Utility class to help log elapsed split timings within a method call when {@code VERBOSE}
 * logging is enabled for the tag.
 */
@Deprecated
public class TimingLogger {
    private String mTag;
    private String mLabel;
    private boolean mDisabled;
    private ArrayList<Long> mSplits;
    private ArrayList<String> mSplitLabels;

    public TimingLogger(String tag, String label) {
        reset(tag, label);
    }

    public void reset(String tag, String label) {
        mTag = tag;
        mLabel = label;
        reset();
    }

    public void reset() {
        mDisabled = !Log.isLoggable(mTag, Log.VERBOSE);
        if (mDisabled) {
            return;
        }
        if (mSplits == null) {
            mSplits = new ArrayList<>();
            mSplitLabels = new ArrayList<>();
        } else {
            mSplits.clear();
            mSplitLabels.clear();
        }
        addSplit(null);
    }

    public void addSplit(String splitLabel) {
        if (mDisabled) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        mSplits.add(Long.valueOf(now));
        mSplitLabels.add(splitLabel);
    }

    public void dumpToLog() {
        if (mDisabled || mSplits == null || mSplits.isEmpty()) {
            return;
        }
        Log.d(mTag, mLabel + ": begin");
        final long first = mSplits.get(0).longValue();
        long now = first;
        for (int i = 1; i < mSplits.size(); i++) {
            now = mSplits.get(i).longValue();
            final String splitLabel = mSplitLabels.get(i);
            final long prev = mSplits.get(i - 1).longValue();
            Log.d(mTag, mLabel + ":      " + (now - prev) + " ms, " + splitLabel);
        }
        Log.d(mTag, mLabel + ": end, " + (now - first) + " ms");
    }
}
