// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Length;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ExerciseRoute implements Parcelable {
    private final List<Location> mRouteLocations;

    public ExerciseRoute(List<Location> routeLocations) {
        Objects.requireNonNull(routeLocations, "routeLocations must not be null");
        mRouteLocations = Collections.unmodifiableList(new ArrayList<Location>(routeLocations));
    }

    public List<Location> getRouteLocations() {
        return mRouteLocations;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ExerciseRoute)) return false;
        return Objects.equals(mRouteLocations, ((ExerciseRoute) object).mRouteLocations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mRouteLocations);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRouteLocations.size());
        for (int i = 0; i < mRouteLocations.size(); i++) {
            mRouteLocations.get(i).writeToParcel(dest, flags);
        }
    }

    public static final Creator<ExerciseRoute> CREATOR = new Creator<ExerciseRoute>() {
        @Override
        public ExerciseRoute createFromParcel(Parcel in) {
            int n = in.readInt();
            List<Location> list = new ArrayList<Location>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                list.add(Location.CREATOR.createFromParcel(in));
            }
            return new ExerciseRoute(list);
        }

        @Override
        public ExerciseRoute[] newArray(int size) {
            return new ExerciseRoute[size];
        }
    };

    public static final class Location implements Parcelable {
        private final Instant mTime;
        private final double mLatitude;
        private final double mLongitude;
        private final Length mHorizontalAccuracy;
        private final Length mVerticalAccuracy;
        private final Length mAltitude;

        Location(Instant time, double latitude, double longitude,
                Length horizontalAccuracy, Length verticalAccuracy, Length altitude) {
            Objects.requireNonNull(time, "time must not be null");
            if (latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException("latitude out of [-90, 90]: " + latitude);
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("longitude out of [-180, 180]: " + longitude);
            }
            mTime = time;
            mLatitude = latitude;
            mLongitude = longitude;
            mHorizontalAccuracy = horizontalAccuracy;
            mVerticalAccuracy = verticalAccuracy;
            mAltitude = altitude;
        }

        public Instant getTime() {
            return mTime;
        }

        public double getLongitude() {
            return mLongitude;
        }

        public double getLatitude() {
            return mLatitude;
        }

        public Length getHorizontalAccuracy() {
            return mHorizontalAccuracy;
        }

        public Length getVerticalAccuracy() {
            return mVerticalAccuracy;
        }

        public Length getAltitude() {
            return mAltitude;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Location)) return false;
            Location that = (Location) object;
            return Double.compare(mLatitude, that.mLatitude) == 0
                    && Double.compare(mLongitude, that.mLongitude) == 0
                    && Objects.equals(mTime, that.mTime)
                    && Objects.equals(mHorizontalAccuracy, that.mHorizontalAccuracy)
                    && Objects.equals(mVerticalAccuracy, that.mVerticalAccuracy)
                    && Objects.equals(mAltitude, that.mAltitude);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTime, mLatitude, mLongitude,
                    mHorizontalAccuracy, mVerticalAccuracy, mAltitude);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mTime.getEpochSecond());
            dest.writeInt(mTime.getNano());
            dest.writeDouble(mLatitude);
            dest.writeDouble(mLongitude);
            dest.writeBoolean(mHorizontalAccuracy != null);
            if (mHorizontalAccuracy != null) dest.writeDouble(mHorizontalAccuracy.getInMeters());
            dest.writeBoolean(mVerticalAccuracy != null);
            if (mVerticalAccuracy != null) dest.writeDouble(mVerticalAccuracy.getInMeters());
            dest.writeBoolean(mAltitude != null);
            if (mAltitude != null) dest.writeDouble(mAltitude.getInMeters());
        }

        public static final Creator<Location> CREATOR = new Creator<Location>() {
            @Override
            public Location createFromParcel(Parcel in) {
                Instant time = Instant.ofEpochSecond(in.readLong(), in.readInt());
                double lat = in.readDouble();
                double lon = in.readDouble();
                Length hAcc = in.readBoolean() ? Length.fromMeters(in.readDouble()) : null;
                Length vAcc = in.readBoolean() ? Length.fromMeters(in.readDouble()) : null;
                Length alt = in.readBoolean() ? Length.fromMeters(in.readDouble()) : null;
                return new Location(time, lat, lon, hAcc, vAcc, alt);
            }

            @Override
            public Location[] newArray(int size) {
                return new Location[size];
            }
        };

        public static final class Builder {
            private final Instant mTime;
            private final double mLatitude;
            private final double mLongitude;
            private Length mHorizontalAccuracy;
            private Length mVerticalAccuracy;
            private Length mAltitude;

            public Builder(Instant time, double latitude, double longitude) {
                mTime = Objects.requireNonNull(time, "time must not be null");
                mLatitude = latitude;
                mLongitude = longitude;
            }

            public Builder setHorizontalAccuracy(Length horizontalAccuracy) {
                mHorizontalAccuracy = horizontalAccuracy;
                return this;
            }

            public Builder setVerticalAccuracy(Length verticalAccuracy) {
                mVerticalAccuracy = verticalAccuracy;
                return this;
            }

            public Builder setAltitude(Length altitude) {
                mAltitude = altitude;
                return this;
            }

            public Location build() {
                return new Location(mTime, mLatitude, mLongitude,
                        mHorizontalAccuracy, mVerticalAccuracy, mAltitude);
            }
        }
    }
}
