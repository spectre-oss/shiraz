// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class MediaTimestamp {
    public static final MediaTimestamp TIMESTAMP_UNKNOWN = new MediaTimestamp(-1L, -1L, 0.0f);

    private final long mMediaTimeUs;
    private final long mNanoTime;
    private final float mClockRate;

    public MediaTimestamp(long mediaTimeUs, long nanoTimeNs, float clockRate) {
        mMediaTimeUs = mediaTimeUs;
        mNanoTime = nanoTimeNs;
        mClockRate = clockRate;
    }

    public long getAnchorMediaTimeUs() {
        return mMediaTimeUs;
    }

    @Deprecated
    public long getAnchorSytemNanoTime() {
        return getAnchorSystemNanoTime();
    }

    public long getAnchorSystemNanoTime() {
        return mNanoTime;
    }

    public float getMediaClockRate() {
        return mClockRate;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaTimestamp)) return false;
        MediaTimestamp that = (MediaTimestamp) obj;
        return mMediaTimeUs == that.mMediaTimeUs
                && mNanoTime == that.mNanoTime
                && Float.compare(mClockRate, that.mClockRate) == 0;
    }

    @Override
    public String toString() {
        return getClass().getName()
                + "{AnchorMediaTimeUs=" + mMediaTimeUs
                + " AnchorSystemNanoTime=" + mNanoTime
                + " ClockRate=" + mClockRate + "}";
    }
}
