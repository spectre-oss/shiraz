// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.common;

import android.os.Parcel;
import android.os.Parcelable;
import java.time.Duration;
import java.util.Objects;

/** Frequency cap configuration keyed by an ad counter integer key. */
public final class KeyedFrequencyCap implements Parcelable {
    private final int mAdCounterKey;
    private final int mMaxCount;
    private final Duration mInterval;

    private KeyedFrequencyCap(int adCounterKey, int maxCount, Duration interval) {
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive");
        }
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.mAdCounterKey = adCounterKey;
        this.mMaxCount = maxCount;
        this.mInterval = interval;
    }

    public int getAdCounterKey() {
        return mAdCounterKey;
    }

    public int getMaxCount() {
        return mMaxCount;
    }

    public Duration getInterval() {
        return mInterval;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mAdCounterKey);
        dest.writeInt(mMaxCount);
        dest.writeLong(mInterval.getSeconds());
        dest.writeInt(mInterval.getNano());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KeyedFrequencyCap)) {
            return false;
        }
        KeyedFrequencyCap that = (KeyedFrequencyCap) o;
        return mAdCounterKey == that.mAdCounterKey
                && mMaxCount == that.mMaxCount
                && Objects.equals(mInterval, that.mInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mAdCounterKey), Integer.valueOf(mMaxCount), mInterval);
    }

    @Override
    public String toString() {
        return "KeyedFrequencyCap{mAdCounterKey="
                + mAdCounterKey
                + ", mMaxCount="
                + mMaxCount
                + ", mInterval="
                + mInterval
                + "}";
    }

    public static final class Builder {
        private int mAdCounterKey;
        private int mMaxCount;
        private Duration mInterval;

        public Builder(int adCounterKey, int maxCount, Duration interval) {
            this.mAdCounterKey = adCounterKey;
            this.mMaxCount = maxCount;
            this.mInterval = Objects.requireNonNull(interval, "interval");
        }

        public Builder setAdCounterKey(int adCounterKey) {
            this.mAdCounterKey = adCounterKey;
            return this;
        }

        public Builder setMaxCount(int maxCount) {
            this.mMaxCount = maxCount;
            return this;
        }

        public Builder setInterval(Duration interval) {
            this.mInterval = Objects.requireNonNull(interval, "interval");
            return this;
        }

        public KeyedFrequencyCap build() {
            return new KeyedFrequencyCap(mAdCounterKey, mMaxCount, mInterval);
        }
    }

    public static final Parcelable.Creator<KeyedFrequencyCap> CREATOR =
            new Parcelable.Creator<KeyedFrequencyCap>() {
                @Override
                public KeyedFrequencyCap createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    int key = in.readInt();
                    int maxCount = in.readInt();
                    long secs = in.readLong();
                    int nanos = in.readInt();
                    return new KeyedFrequencyCap(key, maxCount, Duration.ofSeconds(secs, nanos));
                }

                @Override
                public KeyedFrequencyCap[] newArray(int size) {
                    return new KeyedFrequencyCap[size];
                }
            };
}
