// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.Objects;

public final class SubtitleData {
    private final int mTrackIndex;
    private final long mStartTimeUs;
    private final long mDurationUs;
    private final byte[] mData;

    public SubtitleData(int trackIndex, long startTimeUs, long durationUs, byte[] data) {
        Objects.requireNonNull(data, "data");
        mTrackIndex = trackIndex;
        mStartTimeUs = startTimeUs;
        mDurationUs = durationUs;
        mData = data.clone();
    }

    public int getTrackIndex() {
        return mTrackIndex;
    }

    public long getStartTimeUs() {
        return mStartTimeUs;
    }

    public long getDurationUs() {
        return mDurationUs;
    }

    public byte[] getData() {
        return mData.clone();
    }
}
