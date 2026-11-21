// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.nio.ByteBuffer;

public class ImageWriter implements AutoCloseable {
    public interface OnImageReleasedListener {
        void onImageReleased(ImageWriter writer);
    }

    public static final class Builder {
        private final Surface mSurface;
        private int mWidth = -1;
        private int mHeight = -1;
        private int mMaxImages = 1;
        private int mImageFormat = ImageFormat.UNKNOWN;
        private int mHardwareBufferFormat = HardwareBuffer.RGBA_8888;
        private int mDataSpace = 0;
        private long mUsage = HardwareBuffer.USAGE_CPU_WRITE_OFTEN;
        private boolean mUseSurfaceImageFormatInfo = true;

        public Builder(Surface surface) {
            if (surface == null) {
                throw new NullPointerException("surface must not be null");
            }
            mSurface = surface;
        }

        public Builder setWidthAndHeight(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width and height must be positive");
            }
            mWidth = width;
            mHeight = height;
            return this;
        }

        public Builder setMaxImages(int maxImages) {
            if (maxImages < 1) {
                throw new IllegalArgumentException("maxImages must be at least 1");
            }
            mMaxImages = maxImages;
            return this;
        }

        public Builder setImageFormat(int imageFormat) {
            mImageFormat = imageFormat;
            mUseSurfaceImageFormatInfo = false;
            return this;
        }

        public Builder setHardwareBufferFormat(int hardwareBufferFormat) {
            mHardwareBufferFormat = hardwareBufferFormat;
            mUseSurfaceImageFormatInfo = false;
            return this;
        }

        public Builder setDataSpace(int dataSpace) {
            mDataSpace = dataSpace;
            return this;
        }

        public Builder setUsage(long usage) {
            mUsage = usage;
            return this;
        }

        public ImageWriter build() {
            int w = mWidth > 0 ? mWidth : 640;
            int h = mHeight > 0 ? mHeight : 480;
            int fmt = mUseSurfaceImageFormatInfo ? ImageFormat.PRIVATE : mImageFormat;
            return new ImageWriter(mSurface, mMaxImages, fmt, w, h, mHardwareBufferFormat, mDataSpace, mUsage);
        }
    }

    private final Surface mSurface;
    private final int mMaxImages;
    private final int mFormat;
    private final int mWidth;
    private final int mHeight;
    private final int mHardwareBufferFormat;
    private final int mDataSpace;
    private final long mUsage;
    private int mDequeuedCount;
    private boolean mClosed;
    private OnImageReleasedListener mListener;
    private Handler mListenerHandler;

    private ImageWriter(Surface surface, int maxImages, int format, int width, int height,
            int hardwareBufferFormat, int dataSpace, long usage) {
        if (surface == null) {
            throw new IllegalArgumentException("The surface shouldn't be null");
        }
        if (maxImages < 1) {
            throw new IllegalArgumentException("Maximum image count (" + maxImages + ") must be >= 1");
        }
        mSurface = surface;
        mMaxImages = maxImages;
        mFormat = format == ImageFormat.UNKNOWN ? ImageFormat.YUV_420_888 : format;
        mWidth = width;
        mHeight = height;
        mHardwareBufferFormat = hardwareBufferFormat;
        mDataSpace = dataSpace;
        mUsage = usage;
    }

    public static ImageWriter newInstance(Surface surface, int maxImages) {
        return newInstance(surface, maxImages, ImageFormat.UNKNOWN);
    }

    public static ImageWriter newInstance(Surface surface, int maxImages, int format) {
        if (surface == null) {
            throw new IllegalArgumentException("The surface shouldn't be null");
        }
        if (maxImages < 1) {
            throw new IllegalArgumentException("Maximum image count (" + maxImages + ") must be >= 1");
        }
        return new ImageWriter(surface, maxImages, format, 640, 480,
                HardwareBuffer.RGBA_8888, 0, HardwareBuffer.USAGE_CPU_WRITE_OFTEN);
    }

    public int getMaxImages() {
        return mMaxImages;
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

    public long getUsage() {
        return mUsage;
    }

    public int getHardwareBufferFormat() {
        return mHardwareBufferFormat;
    }

    public int getDataSpace() {
        return mDataSpace;
    }

    public synchronized Image dequeueInputImage() {
        if (mClosed) {
            throw new IllegalStateException("ImageWriter is already closed");
        }
        if (mDequeuedCount >= mMaxImages) {
            throw new IllegalStateException("Already dequeued max number of Images (" + mMaxImages + ")");
        }
        mDequeuedCount++;
        return new WriterSurfaceImage(this, mWidth, mHeight, mFormat);
    }

    public void queueInputImage(Image image) {
        if (image == null) {
            throw new IllegalArgumentException("image shouldn't be null");
        }
        final OnImageReleasedListener listener;
        final Handler handler;
        synchronized (this) {
            if (mClosed) {
                throw new IllegalStateException("ImageWriter is already closed");
            }
            if (!(image instanceof WriterSurfaceImage) || ((WriterSurfaceImage) image).mOwner != this) {
                image.close();
                if (mDequeuedCount > 0) {
                    mDequeuedCount--;
                }
            } else {
                image.close();
            }
            listener = mListener;
            handler = mListenerHandler;
        }
        if (listener != null) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onImageReleased(ImageWriter.this);
                    }
                });
            } else {
                listener.onImageReleased(this);
            }
        }
    }

    public synchronized void setOnImageReleasedListener(OnImageReleasedListener listener, Handler handler) {
        mListener = listener;
        if (listener == null) {
            mListenerHandler = null;
        } else if (handler != null) {
            mListenerHandler = handler;
        } else {
            Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
            mListenerHandler = looper != null ? new Handler(looper) : null;
        }
    }

    @Override
    public synchronized void close() {
        mClosed = true;
        mDequeuedCount = 0;
        mListener = null;
        mListenerHandler = null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    synchronized void onImageClosed() {
        if (mDequeuedCount > 0) {
            mDequeuedCount--;
        }
    }

    private static final class WriterSurfaceImage extends Image {
        final ImageWriter mOwner;
        private final int mW;
        private final int mH;
        private final int mFmt;
        private final Plane[] mPlanes;
        private long mTimestamp;
        private Rect mCropRect;
        private boolean mIsImageValid = true;

        WriterSurfaceImage(ImageWriter owner, int width, int height, int format) {
            mOwner = owner;
            mW = width;
            mH = height;
            mFmt = format;
            mCropRect = new Rect(0, 0, width, height);
            final ByteBuffer buf = ByteBuffer.allocateDirect(Math.max(4, width * height * 4));
            final int rowStride = width * 4;
            mPlanes = new Plane[]{
                new Plane() {
                    @Override
                    public int getRowStride() {
                        return rowStride;
                    }

                    @Override
                    public int getPixelStride() {
                        return 4;
                    }

                    @Override
                    public ByteBuffer getBuffer() {
                        if (!mIsImageValid) {
                            throw new IllegalStateException("Image is already closed");
                        }
                        return buf;
                    }
                }
            };
        }

        @Override
        public int getFormat() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return mFmt;
        }

        @Override
        public int getWidth() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return mW;
        }

        @Override
        public int getHeight() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return mH;
        }

        @Override
        public long getTimestamp() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return mTimestamp;
        }

        @Override
        public void setTimestamp(long timestamp) {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            mTimestamp = timestamp;
        }

        @Override
        public Rect getCropRect() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return new Rect(mCropRect);
        }

        @Override
        public void setCropRect(Rect cropRect) {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            mCropRect = cropRect != null ? new Rect(cropRect) : new Rect(0, 0, mW, mH);
        }

        @Override
        public Plane[] getPlanes() {
            if (!mIsImageValid) {
                throw new IllegalStateException("Image is already closed");
            }
            return mPlanes;
        }

        @Override
        public synchronized void close() {
            if (mIsImageValid) {
                mIsImageValid = false;
                mOwner.onImageClosed();
            }
        }
    }
}
