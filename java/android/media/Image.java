// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Rect;
import java.nio.ByteBuffer;

/** Frame metadata interface for image producers that Shiraz has not implemented. */
public abstract class Image implements AutoCloseable {
    protected Image() { }
    public abstract int getFormat();
    public abstract int getWidth();
    public abstract int getHeight();
    public abstract long getTimestamp();
    public abstract Plane[] getPlanes();
    public abstract void close();
    public Rect getCropRect() { throw new UnsupportedOperationException("Image crop is unavailable"); }
    public void setCropRect(Rect crop) { throw new UnsupportedOperationException("Image crop is unavailable"); }
    public abstract static class Plane {
        protected Plane() { }
        public abstract ByteBuffer getBuffer();
        public abstract int getPixelStride();
        public abstract int getRowStride();
    }
}
