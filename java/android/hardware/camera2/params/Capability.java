// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import android.util.Range;
import android.util.Size;
import java.util.Objects;

public final class Capability {
    private final int mMode;
    private final Size mMaxStreamingSize;
    private final Range<Float> mZoomRatioRange;

    public Capability(int mode, Size maxStreamingSize, Range<Float> zoomRatioRange) {
        mMode = mode;
        mMaxStreamingSize = Objects.requireNonNull(maxStreamingSize, "maxStreamingSize");
        if (maxStreamingSize.getWidth() < 0 || maxStreamingSize.getHeight() < 0) {
            throw new IllegalArgumentException("maxStreamingSize dimensions must be non-negative");
        }
        mZoomRatioRange = Objects.requireNonNull(zoomRatioRange, "zoomRatioRange");
        if (zoomRatioRange.getLower().floatValue() <= 0.0f) {
            throw new IllegalArgumentException("zoomRatioRange lower bound must be positive");
        }
    }

    public int getMode() {
        return mMode;
    }

    public Size getMaxStreamingSize() {
        return mMaxStreamingSize;
    }

    public Range<Float> getZoomRatioRange() {
        return mZoomRatioRange;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Capability)) return false;
        Capability other = (Capability) obj;
        return mMode == other.mMode
                && mMaxStreamingSize.equals(other.mMaxStreamingSize)
                && mZoomRatioRange.equals(other.mZoomRatioRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mMode), mMaxStreamingSize, mZoomRatioRange);
    }

    @Override
    public String toString() {
        return String.format(
                "(mode:%d, maxStreamingSize:%d x %d, zoomRatio: %f-%f)",
                Integer.valueOf(mMode),
                Integer.valueOf(mMaxStreamingSize.getWidth()),
                Integer.valueOf(mMaxStreamingSize.getHeight()),
                mZoomRatioRange.getLower(),
                mZoomRatioRange.getUpper());
    }
}
