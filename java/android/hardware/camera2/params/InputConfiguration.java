// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import java.util.Collection;
import java.util.Objects;

public final class InputConfiguration {
    private final int mWidth;
    private final int mHeight;
    private final int mFormat;
    private final boolean mIsMultiResolution;

    public InputConfiguration(int width, int height, int format) {
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mIsMultiResolution = false;
    }

    public InputConfiguration(Collection<MultiResolutionStreamInfo> multiResolutionInputs, int format) {
        if (multiResolutionInputs == null) {
            throw new NullPointerException("multiResolutionInputs must not be null");
        }
        if (multiResolutionInputs.isEmpty()) {
            throw new IllegalArgumentException("multiResolutionInputs must not be empty");
        }
        MultiResolutionStreamInfo first = multiResolutionInputs.iterator().next();
        if (first == null) {
            throw new NullPointerException("MultiResolutionStreamInfo element must not be null");
        }
        mWidth = first.getWidth();
        mHeight = first.getHeight();
        mFormat = format;
        mIsMultiResolution = true;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFormat() {
        return mFormat;
    }

    public boolean isMultiResolution() {
        return mIsMultiResolution;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof InputConfiguration)) return false;
        InputConfiguration other = (InputConfiguration) obj;
        return mWidth == other.mWidth
                && mHeight == other.mHeight
                && mFormat == other.mFormat
                && mIsMultiResolution == other.mIsMultiResolution;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mWidth),
                Integer.valueOf(mHeight),
                Integer.valueOf(mFormat),
                Boolean.valueOf(mIsMultiResolution));
    }

    @Override
    public String toString() {
        return String.format("InputConfiguration(w:%d, h:%d, format:%d, isMultiResolution:%b)",
                mWidth, mHeight, mFormat, mIsMultiResolution);
    }
}
