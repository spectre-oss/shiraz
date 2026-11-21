// SPDX-License-Identifier: GPL-3.0-only
package android.view;

/**
 * Base class for frame timing statistics.
 */
public abstract class FrameStats {
    public static final long UNDEFINED_TIME_NANO = -1L;

    protected long mRefreshPeriodNano;
    protected long[] mFramesPresentedTimeNano;

    public FrameStats() {
    }

    public final long getRefreshPeriodNano() {
        return mRefreshPeriodNano;
    }

    public final int getFrameCount() {
        return mFramesPresentedTimeNano != null ? mFramesPresentedTimeNano.length : 0;
    }

    public final long getStartTimeNano() {
        if (getFrameCount() <= 0) {
            return UNDEFINED_TIME_NANO;
        }
        return mFramesPresentedTimeNano[0];
    }

    public final long getEndTimeNano() {
        int count = getFrameCount();
        if (count <= 0) {
            return UNDEFINED_TIME_NANO;
        }
        return mFramesPresentedTimeNano[count - 1];
    }

    public final long getFramePresentedTimeNano(int index) {
        if (mFramesPresentedTimeNano == null) {
            throw new IndexOutOfBoundsException();
        }
        return mFramesPresentedTimeNano[index];
    }
}
