// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class SurfaceControl implements Parcelable {
    public static final int BUFFER_TRANSFORM_IDENTITY = 0;
    public static final int BUFFER_TRANSFORM_MIRROR_HORIZONTAL = 1;
    public static final int BUFFER_TRANSFORM_MIRROR_VERTICAL = 2;
    public static final int BUFFER_TRANSFORM_ROTATE_180 = 3;
    public static final int BUFFER_TRANSFORM_ROTATE_90 = 4;
    public static final int BUFFER_TRANSFORM_ROTATE_270 = 7;

    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1L);

    private long mHandle;
    private String mName;
    private int mWidth;
    private int mHeight;
    private int mFormat;
    private boolean mOpaque;
    private boolean mVisible;
    private float mX;
    private float mY;
    private float mScaleX = 1.0f;
    private float mScaleY = 1.0f;
    private int mLayer;
    private float mAlpha = 1.0f;
    private Rect mCrop;
    private SurfaceControl mParent;
    private HardwareBuffer mBuffer;
    private int mBufferTransform = BUFFER_TRANSFORM_IDENTITY;
    private int mDataSpace;
    private float mFrameRate;
    private boolean mValid;

    SurfaceControl(
            long handle,
            String name,
            int width,
            int height,
            int format,
            boolean opaque,
            boolean hidden,
            SurfaceControl parent) {
        mHandle = handle;
        mName = name != null ? name : "SurfaceControl";
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mOpaque = opaque;
        mVisible = !hidden;
        mParent = parent;
        mValid = handle != 0L;
    }

    /** Copy constructor helper for SurfacePackage. */
    SurfaceControl(SurfaceControl other) {
        if (other != null && other.mValid) {
            mHandle = other.mHandle;
            mName = other.mName;
            mWidth = other.mWidth;
            mHeight = other.mHeight;
            mFormat = other.mFormat;
            mOpaque = other.mOpaque;
            mVisible = other.mVisible;
            mX = other.mX;
            mY = other.mY;
            mScaleX = other.mScaleX;
            mScaleY = other.mScaleY;
            mLayer = other.mLayer;
            mAlpha = other.mAlpha;
            mCrop = other.mCrop != null ? new Rect(other.mCrop) : null;
            mParent = other.mParent;
            mValid = true;
        }
    }

    public void readFromParcel(Parcel in) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        mName = in.readString();
        mHandle = in.readLong();
        mWidth = in.readInt();
        mHeight = in.readInt();
        mFormat = in.readInt();
        mOpaque = in.readBoolean();
        mVisible = in.readBoolean();
        mValid = mHandle != 0L;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeLong(mValid ? mHandle : 0L);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mFormat);
        dest.writeBoolean(mOpaque);
        dest.writeBoolean(mVisible);
        if ((flags & Parcelable.PARCELABLE_WRITE_RETURN_VALUE) != 0) {
            release();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

    public void release() {
        mValid = false;
        mHandle = 0L;
        mBuffer = null;
        mParent = null;
    }

    public boolean isValid() {
        return mValid && mHandle != 0L;
    }

    @Override
    public String toString() {
        return "Surface(name=" + mName + ")/@0x" + Long.toHexString(mHandle);
    }

    public static final Parcelable.Creator<SurfaceControl> CREATOR =
            new Parcelable.Creator<SurfaceControl>() {
                @Override
                public SurfaceControl createFromParcel(Parcel in) {
                    SurfaceControl sc =
                            new SurfaceControl(0L, "", 0, 0, 0, false, true, null);
                    sc.readFromParcel(in);
                    return sc;
                }

                @Override
                public SurfaceControl[] newArray(int size) {
                    return new SurfaceControl[size];
                }
            };

    public static class Builder {
        private String mName;
        private int mWidth;
        private int mHeight;
        private int mFormat;
        private boolean mOpaque;
        private boolean mHidden = true;
        private SurfaceControl mParent;

        public Builder() {
        }

        public SurfaceControl build() {
            if (mName == null || mName.isEmpty()) {
                throw new IllegalStateException("SurfaceControl name must be set");
            }
            if (mWidth < 0 || mHeight < 0) {
                throw new IllegalArgumentException(
                        "Invalid buffer size " + mWidth + "x" + mHeight);
            }
            return new SurfaceControl(
                    NEXT_HANDLE.getAndIncrement(),
                    mName,
                    mWidth,
                    mHeight,
                    mFormat,
                    mOpaque,
                    mHidden,
                    mParent);
        }

        public Builder setName(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            mName = name;
            return this;
        }

        public Builder setBufferSize(int width, int height) {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("width and height must be >= 0");
            }
            mWidth = width;
            mHeight = height;
            return this;
        }

        public Builder setFormat(int format) {
            mFormat = format;
            return this;
        }

        public Builder setOpaque(boolean opaque) {
            mOpaque = opaque;
            return this;
        }

        public Builder setHidden(boolean hidden) {
            mHidden = hidden;
            return this;
        }

        public Builder setParent(SurfaceControl parent) {
            mParent = parent;
            return this;
        }
    }

    public interface TransactionCommittedListener {
        void onTransactionCommitted();
    }

    public static final class TransactionStats {
        private final long mLatchTimeNanos;
        private final SyncFence mPresentFence;

        TransactionStats(long latchTimeNanos, SyncFence presentFence) {
            mLatchTimeNanos = latchTimeNanos;
            mPresentFence = presentFence;
        }

        public long getLatchTimeNanos() {
            return mLatchTimeNanos;
        }

        public SyncFence getPresentFence() {
            return new SyncFence(mPresentFence);
        }
    }

    @Deprecated
    public static final class TrustedPresentationThresholds {
        final float mMinAlpha;
        final float mMinFractionRendered;
        final int mStabilityRequirementMs;

        public TrustedPresentationThresholds(
                float minAlpha, float minFractionRendered, int stabilityRequirementMs) {
            if (Float.isNaN(minAlpha) || minAlpha <= 0f || minAlpha > 1f) {
                throw new IllegalArgumentException("minAlpha must be in (0, 1]");
            }
            if (Float.isNaN(minFractionRendered)
                    || minFractionRendered <= 0f
                    || minFractionRendered > 1f) {
                throw new IllegalArgumentException("minFractionRendered must be in (0, 1]");
            }
            if (stabilityRequirementMs <= 0) {
                throw new IllegalArgumentException("stabilityRequirementMs must be > 0");
            }
            mMinAlpha = minAlpha;
            mMinFractionRendered = minFractionRendered;
            mStabilityRequirementMs = stabilityRequirementMs;
        }
    }

    public static class Transaction implements Closeable, Parcelable {
        private static final class CommittedCallback {
            final Executor executor;
            final TransactionCommittedListener listener;

            CommittedCallback(Executor executor, TransactionCommittedListener listener) {
                this.executor = executor;
                this.listener = listener;
            }
        }

        private static final class CompletedCallback {
            final Executor executor;
            final Consumer<TransactionStats> listener;

            CompletedCallback(Executor executor, Consumer<TransactionStats> listener) {
                this.executor = executor;
                this.listener = listener;
            }
        }

        private final ArrayList<Runnable> mOps = new ArrayList<Runnable>();
        private final ArrayList<CommittedCallback> mCommittedCallbacks =
                new ArrayList<CommittedCallback>();
        private final ArrayList<CompletedCallback> mCompletedCallbacks =
                new ArrayList<CompletedCallback>();
        private long mDesiredPresentTimeNanos = -1L;
        private long mFrameTimelineVsyncId = -1L;

        public Transaction() {
        }

        private static void checkSurface(SurfaceControl sc) {
            if (sc == null) {
                throw new NullPointerException("sc");
            }
        }

        public void apply() {
            ArrayList<Runnable> ops;
            ArrayList<CommittedCallback> committed;
            ArrayList<CompletedCallback> completed;
            synchronized (this) {
                ops = new ArrayList<Runnable>(mOps);
                committed = new ArrayList<CommittedCallback>(mCommittedCallbacks);
                completed = new ArrayList<CompletedCallback>(mCompletedCallbacks);
                mOps.clear();
                mCommittedCallbacks.clear();
                mCompletedCallbacks.clear();
            }
            for (int i = 0; i < ops.size(); i++) {
                ops.get(i).run();
            }
            final long now = System.nanoTime();
            for (int i = 0; i < committed.size(); i++) {
                final CommittedCallback cb = committed.get(i);
                cb.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        cb.listener.onTransactionCommitted();
                    }
                });
            }
            if (!completed.isEmpty()) {
                final TransactionStats stats =
                        new TransactionStats(now, SyncFence.createSignaled(now));
                for (int i = 0; i < completed.size(); i++) {
                    final CompletedCallback cb = completed.get(i);
                    cb.executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            cb.listener.accept(stats);
                        }
                    });
                }
            }
        }

        @Override
        public synchronized void close() {
            mOps.clear();
            mCommittedCallbacks.clear();
            mCompletedCallbacks.clear();
        }

        public synchronized Transaction setVisibility(
                final SurfaceControl sc, final boolean visible) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mVisible = visible;
                }
            });
            return this;
        }

        public synchronized Transaction setPosition(
                final SurfaceControl sc, final float x, final float y) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mX = x;
                    sc.mY = y;
                }
            });
            return this;
        }

        public synchronized Transaction setScale(
                final SurfaceControl sc, final float scaleX, final float scaleY) {
            checkSurface(sc);
            if (scaleX < 0f || scaleY < 0f) {
                throw new IllegalArgumentException("Scale must be non-negative");
            }
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mScaleX = scaleX;
                    sc.mScaleY = scaleY;
                }
            });
            return this;
        }

        public synchronized Transaction setBufferSize(
                final SurfaceControl sc, final int w, final int h) {
            checkSurface(sc);
            if (w < 0 || h < 0) {
                throw new IllegalArgumentException("width and height must be >= 0");
            }
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mWidth = w;
                    sc.mHeight = h;
                }
            });
            return this;
        }

        public synchronized Transaction setLayer(final SurfaceControl sc, final int z) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mLayer = z;
                }
            });
            return this;
        }

        public synchronized Transaction setAlpha(final SurfaceControl sc, final float alpha) {
            checkSurface(sc);
            if (Float.isNaN(alpha) || alpha < 0f || alpha > 1f) {
                throw new IllegalArgumentException("alpha must be in [0, 1]");
            }
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mAlpha = alpha;
                }
            });
            return this;
        }

        public synchronized Transaction setGeometry(
                final SurfaceControl sc,
                final Rect sourceCrop,
                final Rect destFrame,
                final int orientation) {
            checkSurface(sc);
            final Rect cropCopy = sourceCrop != null ? new Rect(sourceCrop) : null;
            final Rect dstCopy = destFrame != null ? new Rect(destFrame) : null;
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mCrop = cropCopy;
                    if (dstCopy != null) {
                        sc.mX = dstCopy.left;
                        sc.mY = dstCopy.top;
                    }
                    sc.mBufferTransform = orientation;
                }
            });
            return this;
        }

        public synchronized Transaction setCrop(final SurfaceControl sc, final Rect crop) {
            checkSurface(sc);
            if (crop != null && (crop.width() < 0 || crop.height() < 0)) {
                throw new IllegalArgumentException("Crop width and height must be >= 0");
            }
            final Rect copy = crop != null ? new Rect(crop) : null;
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mCrop = copy;
                }
            });
            return this;
        }

        public synchronized Transaction reparent(
                final SurfaceControl sc, final SurfaceControl newParent) {
            checkSurface(sc);
            if (sc == newParent) {
                throw new IllegalArgumentException("SurfaceControl cannot be reparented to itself");
            }
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mParent = newParent;
                }
            });
            return this;
        }

        public synchronized Transaction setOpaque(
                final SurfaceControl sc, final boolean isOpaque) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mOpaque = isOpaque;
                }
            });
            return this;
        }

        public synchronized Transaction setFrameRate(
                SurfaceControl sc, float frameRate, int compatibility) {
            return setFrameRate(sc, frameRate, compatibility, 0);
        }

        public synchronized Transaction setFrameRate(
                final SurfaceControl sc,
                final float frameRate,
                int compatibility,
                int changeFrameRateStrategy) {
            checkSurface(sc);
            if (Float.isNaN(frameRate) || frameRate < 0f) {
                throw new IllegalArgumentException("frameRate must be >= 0");
            }
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mFrameRate = frameRate;
                }
            });
            return this;
        }

        public synchronized Transaction clearFrameRate(final SurfaceControl sc) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mFrameRate = 0f;
                }
            });
            return this;
        }

        public synchronized Transaction setBuffer(SurfaceControl sc, HardwareBuffer buffer) {
            return setBuffer(sc, buffer, null, null);
        }

        public synchronized Transaction setBuffer(
                SurfaceControl sc, HardwareBuffer buffer, SyncFence fence) {
            return setBuffer(sc, buffer, fence, null);
        }

        public synchronized Transaction setBuffer(
                final SurfaceControl sc,
                final HardwareBuffer buffer,
                final SyncFence fence,
                final Consumer<SyncFence> releaseCallback) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    if (fence != null) {
                        fence.awaitForever();
                    }
                    HardwareBuffer prev = sc.mBuffer;
                    sc.mBuffer = buffer;
                    if (prev != null && releaseCallback != null) {
                        releaseCallback.accept(SyncFence.createSignaled(System.nanoTime()));
                    }
                }
            });
            return this;
        }

        public synchronized Transaction setBufferTransform(
                final SurfaceControl sc, final int transform) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mBufferTransform = transform;
                }
            });
            return this;
        }

        public synchronized Transaction setDamageRegion(
                final SurfaceControl sc, final Region region) {
            checkSurface(sc);
            return this;
        }

        public synchronized Transaction setDataSpace(
                final SurfaceControl sc, final int dataspace) {
            checkSurface(sc);
            mOps.add(new Runnable() {
                @Override
                public void run() {
                    sc.mDataSpace = dataspace;
                }
            });
            return this;
        }

        public synchronized Transaction setExtendedRangeBrightness(
                SurfaceControl sc, float currentBufferRatio, float desiredRatio) {
            checkSurface(sc);
            if (Float.isNaN(currentBufferRatio) || currentBufferRatio < 1.0f) {
                throw new IllegalArgumentException("currentBufferRatio must be >= 1.0f");
            }
            if (Float.isNaN(desiredRatio) || desiredRatio < 1.0f) {
                throw new IllegalArgumentException("desiredRatio must be >= 1.0f");
            }
            return this;
        }

        public synchronized Transaction setDesiredHdrHeadroom(
                SurfaceControl sc, float desiredRatio) {
            checkSurface(sc);
            if (Float.isNaN(desiredRatio)
                    || (desiredRatio != 0.0f && desiredRatio < 1.0f)) {
                throw new IllegalArgumentException(
                        "desiredRatio must be 0.0f or >= 1.0f");
            }
            return this;
        }

        public synchronized Transaction merge(Transaction other) {
            if (other == null || other == this) {
                return this;
            }
            synchronized (other) {
                mOps.addAll(other.mOps);
                mCommittedCallbacks.addAll(other.mCommittedCallbacks);
                mCompletedCallbacks.addAll(other.mCompletedCallbacks);
                other.mOps.clear();
                other.mCommittedCallbacks.clear();
                other.mCompletedCallbacks.clear();
            }
            return this;
        }

        public synchronized Transaction setFrameTimeline(long vsyncId) {
            mFrameTimelineVsyncId = vsyncId;
            return this;
        }

        public synchronized Transaction addTransactionCommittedListener(
                Executor executor, TransactionCommittedListener listener) {
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            if (listener == null) {
                throw new NullPointerException("listener");
            }
            mCommittedCallbacks.add(new CommittedCallback(executor, listener));
            return this;
        }

        public synchronized Transaction addTransactionCompletedListener(
                Executor executor, Consumer<TransactionStats> listener) {
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            if (listener == null) {
                throw new NullPointerException("listener");
            }
            mCompletedCallbacks.add(new CompletedCallback(executor, listener));
            return this;
        }

        @Deprecated
        public synchronized Transaction setTrustedPresentationCallback(
                SurfaceControl sc,
                TrustedPresentationThresholds thresholds,
                Executor executor,
                Consumer<Boolean> listener) {
            checkSurface(sc);
            if (thresholds == null) {
                throw new NullPointerException("thresholds");
            }
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            if (listener == null) {
                throw new NullPointerException("listener");
            }
            return this;
        }

        public synchronized Transaction clearTrustedPresentationCallback(SurfaceControl sc) {
            checkSurface(sc);
            return this;
        }

        public synchronized Transaction setDesiredPresentTimeNanos(long desiredPresentTimeNanos) {
            mDesiredPresentTimeNanos = desiredPresentTimeNanos;
            return this;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mDesiredPresentTimeNanos);
            dest.writeLong(mFrameTimelineVsyncId);
            if ((flags & Parcelable.PARCELABLE_WRITE_RETURN_VALUE) != 0) {
                close();
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Transaction> CREATOR =
                new Parcelable.Creator<Transaction>() {
                    @Override
                    public Transaction createFromParcel(Parcel in) {
                        Transaction t = new Transaction();
                        t.mDesiredPresentTimeNanos = in.readLong();
                        t.mFrameTimelineVsyncId = in.readLong();
                        return t;
                    }

                    @Override
                    public Transaction[] newArray(int size) {
                        return new Transaction[size];
                    }
                };
    }
}
