// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;
import java.time.Duration;

public final class SyncFence implements AutoCloseable, Parcelable {
    public static final long SIGNAL_TIME_INVALID = -1L;
    public static final long SIGNAL_TIME_PENDING = Long.MAX_VALUE;

    private volatile boolean mValid;
    private volatile long mSignalTimeNanos;

    /** @hide */
    public static SyncFence createSignaled(long signalTimeNanos) {
        return new SyncFence(true, signalTimeNanos >= 0L ? signalTimeNanos : System.nanoTime());
    }

    /** @hide */
    public static SyncFence createInvalid() {
        return new SyncFence(false, SIGNAL_TIME_INVALID);
    }

    private SyncFence(boolean valid, long signalTimeNanos) {
        mValid = valid;
        mSignalTimeNanos = valid ? signalTimeNanos : SIGNAL_TIME_INVALID;
    }

    public SyncFence(SyncFence other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        mValid = other.mValid;
        mSignalTimeNanos = other.mSignalTimeNanos;
    }

    public boolean isValid() {
        return mValid;
    }

    public boolean await(Duration timeout) {
        if (timeout == null) {
            throw new NullPointerException("timeout");
        }
        if (!mValid) {
            return true;
        }
        if (mSignalTimeNanos != SIGNAL_TIME_PENDING) {
            return true;
        }
        if (timeout.isNegative() || timeout.isZero()) {
            return mSignalTimeNanos != SIGNAL_TIME_PENDING;
        }
        long millis = timeout.toMillis();
        synchronized (this) {
            if (mValid && mSignalTimeNanos == SIGNAL_TIME_PENDING && millis > 0L) {
                try {
                    wait(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return !mValid || mSignalTimeNanos != SIGNAL_TIME_PENDING;
    }

    public boolean awaitForever() {
        if (!mValid) {
            return true;
        }
        synchronized (this) {
            while (mValid && mSignalTimeNanos == SIGNAL_TIME_PENDING) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    public long getSignalTime() {
        if (!mValid) {
            return SIGNAL_TIME_INVALID;
        }
        return mSignalTimeNanos;
    }

    @Override
    public void close() {
        synchronized (this) {
            mValid = false;
            mSignalTimeNanos = SIGNAL_TIME_INVALID;
            notifyAll();
        }
    }

    @Override
    public int describeContents() {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(mValid);
        out.writeLong(mSignalTimeNanos);
    }

    public static final Parcelable.Creator<SyncFence> CREATOR =
            new Parcelable.Creator<SyncFence>() {
                @Override
                public SyncFence createFromParcel(Parcel in) {
                    boolean valid = in.readBoolean();
                    long signalTime = in.readLong();
                    return new SyncFence(valid, signalTime);
                }

                @Override
                public SyncFence[] newArray(int size) {
                    return new SyncFence[size];
                }
            };
}
