// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import java.util.Arrays;

public final class LensIntrinsicsSample {
    private final long mTimestampNanos;
    private final float[] mLensIntrinsics;

    public LensIntrinsicsSample(long timestampNanos, float[] lensIntrinsics) {
        if (lensIntrinsics == null) {
            throw new NullPointerException("lensIntrinsics must not be null");
        }
        if (lensIntrinsics.length != 5) {
            throw new IllegalArgumentException("lensIntrinsics length must be 5");
        }
        mTimestampNanos = timestampNanos;
        mLensIntrinsics = Arrays.copyOf(lensIntrinsics, lensIntrinsics.length);
    }

    public long getTimestampNanos() {
        return mTimestampNanos;
    }

    public float[] getLensIntrinsics() {
        return Arrays.copyOf(mLensIntrinsics, mLensIntrinsics.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LensIntrinsicsSample)) return false;
        LensIntrinsicsSample other = (LensIntrinsicsSample) obj;
        return mTimestampNanos == other.mTimestampNanos
                && Arrays.equals(mLensIntrinsics, other.mLensIntrinsics);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(mTimestampNanos) + Arrays.hashCode(mLensIntrinsics);
    }

    @Override
    public String toString() {
        return "LensIntrinsicsSample{timestamp:" + mTimestampNanos
                + ", sample:" + Arrays.toString(mLensIntrinsics) + "}";
    }
}
