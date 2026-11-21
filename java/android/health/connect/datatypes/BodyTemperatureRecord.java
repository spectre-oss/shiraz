// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Temperature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BodyTemperatureRecord extends InstantRecord {
    private final int mMeasurementLocation;
    private final Temperature mTemperature;

    BodyTemperatureRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int measurementLocation, Temperature temperature) {
        super(metadata, time, zoneOffset);
        mMeasurementLocation = measurementLocation;
        mTemperature = Objects.requireNonNull(temperature, "temperature must not be null");
    }

    public int getMeasurementLocation() {
        return mMeasurementLocation;
    }

    public Temperature getTemperature() {
        return mTemperature;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        BodyTemperatureRecord that = (BodyTemperatureRecord) object;
        return mMeasurementLocation == that.mMeasurementLocation
                && Objects.equals(mTemperature, that.mTemperature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMeasurementLocation, mTemperature);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mMeasurementLocation;
        private final Temperature mTemperature;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int measurementLocation,
                Temperature temperature) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mMeasurementLocation = measurementLocation;
            mTemperature = Objects.requireNonNull(temperature, "temperature must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BodyTemperatureRecord build() {
            return new BodyTemperatureRecord(
                    mMetadata, mTime, mZoneOffset, mMeasurementLocation, mTemperature);
        }
    }
}
