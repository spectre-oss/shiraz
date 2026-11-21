// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class GnssMeasurementRequest implements Parcelable {
    public static final int PASSIVE_INTERVAL = Integer.MAX_VALUE;

    private final boolean mFullTracking;
    private final int mIntervalMillis;

    private GnssMeasurementRequest(boolean fullTracking, int intervalMillis) {
        mFullTracking = fullTracking;
        mIntervalMillis = intervalMillis;
    }

    public boolean isFullTracking() {
        return mFullTracking;
    }

    public int getIntervalMillis() {
        return mIntervalMillis;
    }

    public static final Parcelable.Creator<GnssMeasurementRequest> CREATOR =
            new Parcelable.Creator<GnssMeasurementRequest>() {
                @Override
                public GnssMeasurementRequest createFromParcel(Parcel parcel) {
                    boolean fullTracking = parcel.readBoolean();
                    int intervalMillis = parcel.readInt();
                    return new GnssMeasurementRequest(fullTracking, intervalMillis);
                }

                @Override
                public GnssMeasurementRequest[] newArray(int size) {
                    return new GnssMeasurementRequest[size];
                }
            };

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(mFullTracking);
        parcel.writeInt(mIntervalMillis);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GnssMeasurementRequest)) return false;
        GnssMeasurementRequest that = (GnssMeasurementRequest) o;
        return mFullTracking == that.mFullTracking && mIntervalMillis == that.mIntervalMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Boolean.valueOf(mFullTracking), Integer.valueOf(mIntervalMillis));
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("GnssMeasurementRequest[");
        if (mIntervalMillis == PASSIVE_INTERVAL) {
            s.append("passive");
        } else {
            s.append("@").append(mIntervalMillis).append("ms");
        }
        if (mFullTracking) {
            s.append(", FullTracking");
        }
        s.append(']');
        return s.toString();
    }

    public static final class Builder {
        private boolean mFullTracking;
        private int mIntervalMillis;

        public Builder() {}

        public Builder(GnssMeasurementRequest request) {
            if (request == null) {
                throw new NullPointerException("request must not be null");
            }
            mFullTracking = request.isFullTracking();
            mIntervalMillis = request.getIntervalMillis();
        }

        public Builder setFullTracking(boolean value) {
            mFullTracking = value;
            return this;
        }

        public Builder setIntervalMillis(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("intervalMillis must be >= 0: " + value);
            }
            mIntervalMillis = value;
            return this;
        }

        public GnssMeasurementRequest build() {
            return new GnssMeasurementRequest(mFullTracking, mIntervalMillis);
        }
    }
}
