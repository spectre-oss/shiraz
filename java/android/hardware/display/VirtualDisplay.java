// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.display;

import android.content.res.Resources;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

public final class VirtualDisplay {
    private final String mName;
    private final Display mDisplay;
    private Surface mSurface;
    private int mWidth;
    private int mHeight;
    private int mDensityDpi;
    private final Callback mCallback;
    private final Handler mHandler;
    private boolean mReleased;

    private final class VirtualDisplayResources extends Resources {
        VirtualDisplayResources() {
            super(VirtualDisplay.class.getClassLoader());
        }

        private DisplayMetrics snapshotMetrics() {
            if (mReleased) {
                throw new IllegalStateException("VirtualDisplay has been released");
            }
            DisplayMetrics dm = new DisplayMetrics();
            dm.widthPixels = mWidth;
            dm.heightPixels = mHeight;
            dm.densityDpi = mDensityDpi;
            dm.density = mDensityDpi / 160.0f;
            dm.scaledDensity = dm.density;
            dm.xdpi = mDensityDpi;
            dm.ydpi = mDensityDpi;
            return dm;
        }

        @Override
        public synchronized DisplayMetrics getDisplayMetrics() {
            return snapshotMetrics();
        }

        @Override
        public synchronized DisplayMetrics shirazBaseDisplayMetrics() {
            return snapshotMetrics();
        }
    }

    VirtualDisplay(
            String name,
            Display display,
            Surface surface,
            int width,
            int height,
            int densityDpi,
            Callback callback,
            Handler handler) {
        mName = name;
        mSurface = surface;
        mWidth = width;
        mHeight = height;
        mDensityDpi = densityDpi;
        mCallback = callback;
        mHandler = handler;
        mDisplay = Display.shirazForResources(new VirtualDisplayResources());
    }

    public Display getDisplay() {
        return mDisplay;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public void setSurface(Surface surface) {
        if (!mReleased) {
            mSurface = surface;
        }
    }

    public void resize(int width, int height, int densityDpi) {
        if (width <= 0 || height <= 0 || densityDpi <= 0) {
            throw new IllegalArgumentException("VirtualDisplay dimensions must be positive");
        }
        if (!mReleased) {
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }
    }

    public void release() {
        if (!mReleased) {
            mReleased = true;
            if (mCallback != null) {
                final Callback cb = mCallback;
                if (mHandler != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onStopped();
                        }
                    });
                } else {
                    cb.onStopped();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "VirtualDisplay{name=" + mName
                + ", width=" + mWidth
                + ", height=" + mHeight
                + ", densityDpi=" + mDensityDpi
                + ", surface=" + mSurface + "}";
    }

    public abstract static class Callback {
        public Callback() {
        }

        public void onPaused() {
        }

        public void onResumed() {
        }

        public void onStopped() {
        }
    }
}
