// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

public final class OisSample {
    private final long mTimestampNanos;
    private final float mXShift;
    private final float mYShift;

    public OisSample(long timestamp, float xShift, float yShift) {
        if (!Float.isFinite(xShift)) {
            throw new IllegalArgumentException("xShift must be finite");
        }
        if (!Float.isFinite(yShift)) {
            throw new IllegalArgumentException("yShift must be finite");
        }
        mTimestampNanos = timestamp;
        mXShift = xShift;
        mYShift = yShift;
    }

    public long getTimestamp() {
        return mTimestampNanos;
    }

    public float getXshift() {
        return mXShift;
    }

    public float getYshift() {
        return mYShift;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OisSample)) return false;
        OisSample other = (OisSample) obj;
        return mTimestampNanos == other.mTimestampNanos
                && mXShift == other.mXShift
                && mYShift == other.mYShift;
    }

    @Override
    public int hashCode() {
        int h = Long.hashCode(mTimestampNanos);
        h = 31 * h + Float.floatToIntBits(mXShift);
        h = 31 * h + Float.floatToIntBits(mYShift);
        return h;
    }

    @Override
    public String toString() {
        return String.format("OisSample{timestamp:%d, shift_x:%f, shift_y:%f}",
                mTimestampNanos, mXShift, mYShift);
    }
}
