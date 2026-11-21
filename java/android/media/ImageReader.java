// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.ImageFormat;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;

public class ImageReader implements AutoCloseable {
    private final Object mLock = new Object();
    private final int mWidth;
    private final int mHeight;
    private final int mFormat;
    private final int mHardwareBufferFormat;
    private final int mDataSpace;
    private final int mMaxImages;
    private final long mUsage;
    private final Surface mSurface;
    private final ArrayDeque<SurfaceImage> mQueuedImages = new ArrayDeque<SurfaceImage>();
    private final ArrayList<SurfaceImage> mAcquiredImages = new ArrayList<SurfaceImage>();
    private OnImageAvailableListener mListener;
    private Handler mListenerHandler;
    private boolean mClosed;

    private ImageReader(
            int width,
            int height,
            int imageFormat,
            int hardwareBufferFormat,
            int dataSpace,
            int maxImages,
            long usage) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException(
                    "The image dimensions must be positive; width=" + width + ", height=" + height);
        }
        if (maxImages < 1) {
            throw new IllegalArgumentException(
                    "Maximum outstanding image count must be at least 1");
        }
        if (imageFormat == ImageFormat.NV21) {
            throw new IllegalArgumentException("NV21 format is not supported");
        }
        mWidth = width;
        mHeight = height;
        mFormat = imageFormat;
        mHardwareBufferFormat = hardwareBufferFormat;
        mDataSpace = dataSpace;
        mMaxImages = maxImages;
        mUsage = usage;
        mSurface = new Surface();
        if (width <= 4096 && height <= 4096) {
            mSurface.shirazConfigure(width, height, 1);
        }
    }

    public static ImageReader newInstance(int width, int height, int format, int maxImages) {
        return newInstance(
                width, height, format, maxImages, HardwareBuffer.USAGE_CPU_READ_OFTEN);
    }

    public static ImageReader newInstance(
            int width, int height, int format, int maxImages, long usage) {
        return new ImageReader(
                width,
                height,
                format,
                mapImageFormatToHardwareBuffer(format),
                DataSpace.DATASPACE_UNKNOWN,
                maxImages,
                usage);
    }

    private static int mapImageFormatToHardwareBuffer(int imageFormat) {
        switch (imageFormat) {
            case 1: return HardwareBuffer.RGBA_8888;
            case 2: return HardwareBuffer.RGBX_8888;
            case 3: return HardwareBuffer.RGB_888;
            case ImageFormat.RGB_565: return HardwareBuffer.RGB_565;
            case ImageFormat.YUV_420_888: return HardwareBuffer.YCBCR_420_888;
            case HardwareBuffer.YCBCR_P010: return HardwareBuffer.YCBCR_P010;
            case ImageFormat.JPEG: return HardwareBuffer.BLOB;
            default: return imageFormat;
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getImageFormat() {
        return mFormat;
    }

    public int getHardwareBufferFormat() {
        return mHardwareBufferFormat;
    }

    public int getDataSpace() {
        return mDataSpace;
    }

    public int getMaxImages() {
        return mMaxImages;
    }

    public long getUsage() {
        return mUsage;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public Image acquireLatestImage() {
        Image img = acquireNextImage();
        if (img == null) {
            return null;
        }
        try {
            for (;;) {
                Image next = acquireNextImageNoThrowISE();
                if (next == null) {
                    Image result = img;
                    img = null;
                    return result;
                }
                img.close();
                img = next;
            }
        } finally {
            if (img != null) {
                img.close();
            }
        }
    }

    private SurfaceImage acquireNextImageNoThrowISE() {
        synchronized (mLock) {
            ensureOpenLocked();
            if (mAcquiredImages.size() >= mMaxImages || mQueuedImages.isEmpty()) {
                return null;
            }
            SurfaceImage img = mQueuedImages.pollFirst();
            mAcquiredImages.add(img);
            return img;
        }
    }

    public Image acquireNextImage() {
        synchronized (mLock) {
            ensureOpenLocked();
            if (mAcquiredImages.size() >= mMaxImages) {
                throw new IllegalStateException(
                        "maxImages (" + mMaxImages + ") has already been acquired, "
                                + "call #close before acquiring more.");
            }
            if (mQueuedImages.isEmpty()) {
                return null;
            }
            SurfaceImage img = mQueuedImages.pollFirst();
            mAcquiredImages.add(img);
            return img;
        }
    }

    public void setOnImageAvailableListener(OnImageAvailableListener listener, Handler handler) {
        synchronized (mLock) {
            if (listener != null) {
                Looper looper = handler != null ? handler.getLooper() : Looper.myLooper();
                if (looper == null) {
                    throw new IllegalArgumentException(
                            "handler is null but the current thread is not a looper");
                }
                mListenerHandler = handler != null ? handler : new Handler(looper);
                mListener = listener;
            } else {
                mListener = null;
                mListenerHandler = null;
            }
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            mListener = null;
            mListenerHandler = null;
            ArrayList<SurfaceImage> toClose =
                    new ArrayList<SurfaceImage>(mAcquiredImages.size() + mQueuedImages.size());
            toClose.addAll(mAcquiredImages);
            toClose.addAll(mQueuedImages);
            mAcquiredImages.clear();
            mQueuedImages.clear();
            for (int i = 0; i < toClose.size(); i++) {
                toClose.get(i).closeInternal();
            }
            mSurface.release();
        }
    }

    public void discardFreeBuffers() {
        synchronized (mLock) {
            ensureOpenLocked();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private void ensureOpenLocked() {
        if (mClosed) {
            throw new IllegalStateException("ImageReader is already closed");
        }
    }

    private void releaseImage(SurfaceImage image) {
        synchronized (mLock) {
            mAcquiredImages.remove(image);
        }
    }

    public interface OnImageAvailableListener {
        void onImageAvailable(ImageReader reader);
    }

    public static final class Builder {
        private final int mWidth;
        private final int mHeight;
        private int mMaxImages = 1;
        private int mImageFormat = ImageFormat.UNKNOWN;
        private int mHardwareBufferFormat = HardwareBuffer.RGBA_8888;
        private int mDataSpace = DataSpace.DATASPACE_UNKNOWN;
        private long mUsage = HardwareBuffer.USAGE_CPU_READ_RARELY;
        private boolean mUseLegacyImageFormat;

        public Builder(int width, int height) {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException(
                        "Width and height must be positive: " + width + "x" + height);
            }
            mWidth = width;
            mHeight = height;
        }

        public Builder setMaxImages(int maxImages) {
            mMaxImages = maxImages;
            return this;
        }

        public Builder setUsage(long usage) {
            mUsage = usage;
            return this;
        }

        public Builder setImageFormat(int imageFormat) {
            mImageFormat = imageFormat;
            mUseLegacyImageFormat = true;
            return this;
        }

        public Builder setDefaultHardwareBufferFormat(int hardwareBufferFormat) {
            mHardwareBufferFormat = hardwareBufferFormat;
            mUseLegacyImageFormat = false;
            return this;
        }

        public Builder setDefaultDataSpace(int dataSpace) {
            mDataSpace = dataSpace;
            mUseLegacyImageFormat = false;
            return this;
        }

        public ImageReader build() {
            if (mUseLegacyImageFormat) {
                return new ImageReader(
                        mWidth,
                        mHeight,
                        mImageFormat,
                        mapImageFormatToHardwareBuffer(mImageFormat),
                        DataSpace.DATASPACE_UNKNOWN,
                        mMaxImages,
                        mUsage);
            }
            return new ImageReader(
                    mWidth,
                    mHeight,
                    mHardwareBufferFormat,
                    mHardwareBufferFormat,
                    mDataSpace,
                    mMaxImages,
                    mUsage);
        }
    }

    private final class SurfaceImage extends Image {
        private final Plane[] mPlanes;
        private long mTimestamp;
        private SyncFence mFence = SyncFence.createInvalid();
        private boolean mImageClosed;

        SurfaceImage(int width, int height, int format, int dataSpace, long timestamp) {
            mTimestamp = timestamp;
            setDataSpace(dataSpace);
            mPlanes = createPlanes(width, height, format);
        }

        private Plane[] createPlanes(int w, int h, int format) {
            if (format == ImageFormat.PRIVATE) {
                return new Plane[0];
            }
            if (format == ImageFormat.YUV_420_888
                    || format == ImageFormat.YV12
                    || format == HardwareBuffer.YCBCR_P010) {
                int bpp = (format == HardwareBuffer.YCBCR_P010) ? 2 : 1;
                int cw = (w + 1) / 2;
                int ch = (h + 1) / 2;
                return new Plane[] {
                    new BufferPlane(w * bpp, bpp, ByteBuffer.allocateDirect(w * h * bpp)),
                    new BufferPlane(cw * bpp, bpp, ByteBuffer.allocateDirect(cw * ch * bpp)),
                    new BufferPlane(cw * bpp, bpp, ByteBuffer.allocateDirect(cw * ch * bpp))
                };
            }
            if (format == ImageFormat.JPEG) {
                int cap = Math.max(1024, w * h);
                return new Plane[] { new BufferPlane(0, 0, ByteBuffer.allocateDirect(cap)) };
            }
            int pixelStride = 4;
            if (format == ImageFormat.RGB_565 || format == ImageFormat.RAW_SENSOR) {
                pixelStride = 2;
            } else if (format == 3) {
                pixelStride = 3;
            }
            return new Plane[] {
                new BufferPlane(
                        w * pixelStride,
                        pixelStride,
                        ByteBuffer.allocateDirect(w * h * pixelStride))
            };
        }

        private void checkValid() {
            if (mImageClosed) {
                throw new IllegalStateException("Image is already closed");
            }
        }

        @Override
        public int getFormat() {
            checkValid();
            return mFormat;
        }

        @Override
        public int getWidth() {
            checkValid();
            return mWidth;
        }

        @Override
        public int getHeight() {
            checkValid();
            return mHeight;
        }

        @Override
        public long getTimestamp() {
            checkValid();
            return mTimestamp;
        }

        @Override
        public void setTimestamp(long timestamp) {
            checkValid();
            mTimestamp = timestamp;
        }

        @Override
        public SyncFence getFence() throws IOException {
            checkValid();
            return mFence;
        }

        @Override
        public void setFence(SyncFence fence) throws IOException {
            checkValid();
            mFence = Objects.requireNonNull(fence, "fence");
        }

        @Override
        public Plane[] getPlanes() {
            checkValid();
            return mPlanes.clone();
        }

        @Override
        public void close() {
            if (!mImageClosed) {
                closeInternal();
                releaseImage(this);
            }
        }

        void closeInternal() {
            mImageClosed = true;
        }
    }

    private static final class BufferPlane extends Image.Plane {
        private final int mRowStride;
        private final int mPixelStride;
        private final ByteBuffer mBuffer;

        BufferPlane(int rowStride, int pixelStride, ByteBuffer buffer) {
            mRowStride = rowStride;
            mPixelStride = pixelStride;
            mBuffer = buffer;
        }

        @Override
        public int getRowStride() {
            return mRowStride;
        }

        @Override
        public int getPixelStride() {
            return mPixelStride;
        }

        @Override
        public ByteBuffer getBuffer() {
            return mBuffer;
        }
    }
}
