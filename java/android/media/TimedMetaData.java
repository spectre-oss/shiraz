// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class TimedMetaData {
    private final long mTimestampUs;
    private final byte[] mMetaData;

    public TimedMetaData(long timestampUs, byte[] metaData) {
        if (metaData == null) {
            throw new IllegalArgumentException("metaData must not be null");
        }
        mTimestampUs = timestampUs;
        mMetaData = metaData.clone();
    }

    public long getTimestamp() {
        return mTimestampUs;
    }

    public byte[] getMetaData() {
        return mMetaData.clone();
    }
}
