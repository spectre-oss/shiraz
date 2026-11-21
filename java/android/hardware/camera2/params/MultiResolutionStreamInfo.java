// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import java.util.Objects;

public class MultiResolutionStreamInfo {
    private final int mStreamWidth;
    private final int mStreamHeight;
    private final String mPhysicalCameraId;

    public MultiResolutionStreamInfo(int streamWidth, int streamHeight, String physicalCameraId) {
        if (streamWidth <= 0) {
            throw new IllegalArgumentException("Invalid stream width " + streamWidth);
        }
        if (streamHeight <= 0) {
            throw new IllegalArgumentException("Invalid stream height " + streamHeight);
        }
        Objects.requireNonNull(physicalCameraId, "physicalCameraId");
        mStreamWidth = streamWidth;
        mStreamHeight = streamHeight;
        mPhysicalCameraId = physicalCameraId;
    }

    public int getWidth() {
        return mStreamWidth;
    }

    public int getHeight() {
        return mStreamHeight;
    }

    public String getPhysicalCameraId() {
        return mPhysicalCameraId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof MultiResolutionStreamInfo) {
            MultiResolutionStreamInfo other = (MultiResolutionStreamInfo) obj;
            return mStreamWidth == other.mStreamWidth
                    && mStreamHeight == other.mStreamHeight
                    && mPhysicalCameraId.equals(other.mPhysicalCameraId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mStreamWidth),
                Integer.valueOf(mStreamHeight),
                mPhysicalCameraId);
    }
}
