// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class LocationRequest implements Parcelable {
    public static final long PASSIVE_INTERVAL = Long.MAX_VALUE;

    public static final int QUALITY_HIGH_ACCURACY = 100;
    public static final int QUALITY_BALANCED_POWER_ACCURACY = 102;
    public static final int QUALITY_LOW_POWER = 104;

    private static final long IMPLICIT_MIN_UPDATE_INTERVAL = -1L;

    private final long mIntervalMillis;
    private final int mQuality;
    private final long mDurationMillis;
    private final int mMaxUpdates;
    private final long mMinUpdateIntervalMillis;
    private final float mMinUpdateDistanceMeters;
    private final long mMaxUpdateDelayMillis;

    private LocationRequest(
            long intervalMillis,
            int quality,
            long durationMillis,
            int maxUpdates,
            long minUpdateIntervalMillis,
            float minUpdateDistanceMeters,
            long maxUpdateDelayMillis) {
        mIntervalMillis = intervalMillis;
        mQuality = quality;
        mDurationMillis = durationMillis;
        mMaxUpdates = maxUpdates;
        mMinUpdateIntervalMillis = minUpdateIntervalMillis;
        mMinUpdateDistanceMeters = minUpdateDistanceMeters;
        mMaxUpdateDelayMillis = maxUpdateDelayMillis;
    }

    public int getQuality() {
        return mQuality;
    }

    public long getIntervalMillis() {
        return mIntervalMillis;
    }

    public long getDurationMillis() {
        return mDurationMillis;
    }

    public int getMaxUpdates() {
        return mMaxUpdates;
    }

    public long getMinUpdateIntervalMillis() {
        if (mMinUpdateIntervalMillis == IMPLICIT_MIN_UPDATE_INTERVAL) {
            return mIntervalMillis == PASSIVE_INTERVAL ? 0L : mIntervalMillis;
        }
        return Math.min(mMinUpdateIntervalMillis, mIntervalMillis);
    }

    public float getMinUpdateDistanceMeters() {
        return mMinUpdateDistanceMeters;
    }

    public long getMaxUpdateDelayMillis() {
        return mMaxUpdateDelayMillis;
    }

    public static final Parcelable.Creator<LocationRequest> CREATOR =
            new Parcelable.Creator<LocationRequest>() {
                @Override
                public LocationRequest createFromParcel(Parcel in) {
                    return new LocationRequest(
                            in.readLong(),
                            in.readInt(),
                            in.readLong(),
                            in.readInt(),
                            in.readLong(),
                            in.readFloat(),
                            in.readLong());
                }

                @Override
                public LocationRequest[] newArray(int size) {
                    return new LocationRequest[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mIntervalMillis);
        parcel.writeInt(mQuality);
        parcel.writeLong(mDurationMillis);
        parcel.writeInt(mMaxUpdates);
        parcel.writeLong(mMinUpdateIntervalMillis);
        parcel.writeFloat(mMinUpdateDistanceMeters);
        parcel.writeLong(mMaxUpdateDelayMillis);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationRequest)) {
            return false;
        }
        LocationRequest that = (LocationRequest) o;
        return mIntervalMillis == that.mIntervalMillis
                && mQuality == that.mQuality
                && mDurationMillis == that.mDurationMillis
                && mMaxUpdates == that.mMaxUpdates
                && mMinUpdateIntervalMillis == that.mMinUpdateIntervalMillis
                && Float.compare(that.mMinUpdateDistanceMeters, mMinUpdateDistanceMeters) == 0
                && mMaxUpdateDelayMillis == that.mMaxUpdateDelayMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIntervalMillis, mQuality, mDurationMillis, mMaxUpdates,
                mMinUpdateIntervalMillis, mMinUpdateDistanceMeters, mMaxUpdateDelayMillis);
    }

    @Override
    public String toString() {
        return "LocationRequest[interval=" + mIntervalMillis
                + ", quality=" + mQuality
                + ", duration=" + mDurationMillis
                + ", maxUpdates=" + mMaxUpdates
                + ", minDistance=" + mMinUpdateDistanceMeters + "]";
    }

    public static final class Builder {
        private long mIntervalMillis;
        private int mQuality = QUALITY_BALANCED_POWER_ACCURACY;
        private long mDurationMillis = Long.MAX_VALUE;
        private int mMaxUpdates = Integer.MAX_VALUE;
        private long mMinUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
        private float mMinUpdateDistanceMeters = 0.0f;
        private long mMaxUpdateDelayMillis = 0L;

        public Builder(long intervalMillis) {
            setIntervalMillis(intervalMillis);
        }

        public Builder(LocationRequest locationRequest) {
            Objects.requireNonNull(locationRequest, "locationRequest must not be null");
            mIntervalMillis = locationRequest.mIntervalMillis;
            mQuality = locationRequest.mQuality;
            mDurationMillis = locationRequest.mDurationMillis;
            mMaxUpdates = locationRequest.mMaxUpdates;
            mMinUpdateIntervalMillis = locationRequest.mMinUpdateIntervalMillis;
            mMinUpdateDistanceMeters = locationRequest.mMinUpdateDistanceMeters;
            mMaxUpdateDelayMillis = locationRequest.mMaxUpdateDelayMillis;
        }

        public Builder setIntervalMillis(long intervalMillis) {
            if (intervalMillis < 0L) {
                throw new IllegalArgumentException("intervalMillis must be >= 0");
            }
            mIntervalMillis = intervalMillis;
            return this;
        }

        public Builder setQuality(int quality) {
            if (quality != QUALITY_LOW_POWER
                    && quality != QUALITY_BALANCED_POWER_ACCURACY
                    && quality != QUALITY_HIGH_ACCURACY) {
                throw new IllegalArgumentException("Invalid quality: " + quality);
            }
            mQuality = quality;
            return this;
        }

        public Builder setDurationMillis(long durationMillis) {
            if (durationMillis < 1L) {
                throw new IllegalArgumentException("durationMillis must be >= 1");
            }
            mDurationMillis = durationMillis;
            return this;
        }

        public Builder setMaxUpdates(int maxUpdates) {
            if (maxUpdates < 1) {
                throw new IllegalArgumentException("maxUpdates must be >= 1");
            }
            mMaxUpdates = maxUpdates;
            return this;
        }

        public Builder setMinUpdateIntervalMillis(long minUpdateIntervalMillis) {
            if (minUpdateIntervalMillis < 0L) {
                throw new IllegalArgumentException("minUpdateIntervalMillis must be >= 0");
            }
            mMinUpdateIntervalMillis = minUpdateIntervalMillis;
            return this;
        }

        public Builder clearMinUpdateIntervalMillis() {
            mMinUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
            return this;
        }

        public Builder setMinUpdateDistanceMeters(float minUpdateDistanceMeters) {
            if (!Float.isFinite(minUpdateDistanceMeters) || minUpdateDistanceMeters < 0.0f) {
                throw new IllegalArgumentException("minUpdateDistanceMeters must be >= 0");
            }
            mMinUpdateDistanceMeters = minUpdateDistanceMeters;
            return this;
        }

        public Builder setMaxUpdateDelayMillis(long maxUpdateDelayMillis) {
            if (maxUpdateDelayMillis < 0L) {
                throw new IllegalArgumentException("maxUpdateDelayMillis must be >= 0");
            }
            mMaxUpdateDelayMillis = maxUpdateDelayMillis;
            return this;
        }

        public LocationRequest build() {
            return new LocationRequest(
                    mIntervalMillis,
                    mQuality,
                    mDurationMillis,
                    mMaxUpdates,
                    mMinUpdateIntervalMillis,
                    mMinUpdateDistanceMeters,
                    mMaxUpdateDelayMillis);
        }
    }
}
