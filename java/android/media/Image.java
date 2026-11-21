// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Rect;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class Image implements AutoCloseable {
    private Rect mCropRect;
    private int mDataSpace = DataSpace.DATASPACE_UNKNOWN;

    protected Image() {
    }

    public abstract int getFormat();

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract long getTimestamp();

    public SyncFence getFence() throws IOException {
        return SyncFence.createInvalid();
    }

    public HardwareBuffer getHardwareBuffer() {
        return null;
    }

    public void setTimestamp(long timestamp) {
    }

    public void setFence(SyncFence fence) throws IOException {
    }

    public int getDataSpace() {
        return mDataSpace;
    }

    public void setDataSpace(int dataSpace) {
        mDataSpace = dataSpace;
    }

    public Rect getCropRect() {
        if (mCropRect == null) {
            return new Rect(0, 0, getWidth(), getHeight());
        }
        return new Rect(mCropRect);
    }

    public void setCropRect(Rect cropRect) {
        if (cropRect != null) {
            Rect copy = new Rect(cropRect);
            if (!copy.intersect(0, 0, getWidth(), getHeight())) {
                copy.setEmpty();
            }
            mCropRect = copy;
        } else {
            mCropRect = null;
        }
    }

    public abstract Plane[] getPlanes();

    @Override
    public abstract void close();

    public abstract static class Plane {
        protected Plane() {
        }

        public abstract int getRowStride();

        public abstract int getPixelStride();

        public abstract ByteBuffer getBuffer();
    }
}
