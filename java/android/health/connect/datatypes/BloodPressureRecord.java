// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Pressure;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BloodPressureRecord extends InstantRecord {
    public static final AggregationType<Pressure> DIASTOLIC_AVG =
            new AggregationType<Pressure>(BloodPressureRecord.class, "diastolic",
                    AggregationType.OP_AVG, AggregationType.KIND_PRESSURE);
    public static final AggregationType<Pressure> DIASTOLIC_MAX =
            new AggregationType<Pressure>(BloodPressureRecord.class, "diastolic",
                    AggregationType.OP_MAX, AggregationType.KIND_PRESSURE);
    public static final AggregationType<Pressure> DIASTOLIC_MIN =
            new AggregationType<Pressure>(BloodPressureRecord.class, "diastolic",
                    AggregationType.OP_MIN, AggregationType.KIND_PRESSURE);
    public static final AggregationType<Pressure> SYSTOLIC_AVG =
            new AggregationType<Pressure>(BloodPressureRecord.class, "systolic",
                    AggregationType.OP_AVG, AggregationType.KIND_PRESSURE);
    public static final AggregationType<Pressure> SYSTOLIC_MAX =
            new AggregationType<Pressure>(BloodPressureRecord.class, "systolic",
                    AggregationType.OP_MAX, AggregationType.KIND_PRESSURE);
    public static final AggregationType<Pressure> SYSTOLIC_MIN =
            new AggregationType<Pressure>(BloodPressureRecord.class, "systolic",
                    AggregationType.OP_MIN, AggregationType.KIND_PRESSURE);

    private final int mMeasurementLocation;
    private final Pressure mSystolic;
    private final Pressure mDiastolic;
    private final int mBodyPosition;

    BloodPressureRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int measurementLocation, Pressure systolic, Pressure diastolic, int bodyPosition) {
        super(metadata, time, zoneOffset);
        mMeasurementLocation = measurementLocation;
        mSystolic = Objects.requireNonNull(systolic, "systolic must not be null");
        mDiastolic = Objects.requireNonNull(diastolic, "diastolic must not be null");
        mBodyPosition = bodyPosition;
    }

    public int getMeasurementLocation() {
        return mMeasurementLocation;
    }

    public Pressure getSystolic() {
        return mSystolic;
    }

    public Pressure getDiastolic() {
        return mDiastolic;
    }

    public int getBodyPosition() {
        return mBodyPosition;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("systolic".equals(metric)) {
            accumulator.addDouble(mSystolic.getInMillimetersOfMercury());
        } else if ("diastolic".equals(metric)) {
            accumulator.addDouble(mDiastolic.getInMillimetersOfMercury());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        BloodPressureRecord that = (BloodPressureRecord) object;
        return mMeasurementLocation == that.mMeasurementLocation
                && mBodyPosition == that.mBodyPosition
                && Objects.equals(mSystolic, that.mSystolic)
                && Objects.equals(mDiastolic, that.mDiastolic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMeasurementLocation, mSystolic, mDiastolic, mBodyPosition);
    }

    public static final class BloodPressureMeasurementLocation {
        public static final int BLOOD_PRESSURE_MEASUREMENT_LOCATION_UNKNOWN = 0;
        public static final int BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_WRIST = 1;
        public static final int BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST = 2;
        public static final int BLOOD_PRESSURE_MEASUREMENT_LOCATION_LEFT_UPPER_ARM = 3;
        public static final int BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_UPPER_ARM = 4;

        private BloodPressureMeasurementLocation() {
        }
    }

    public static final class BodyPosition {
        public static final int BODY_POSITION_UNKNOWN = 0;
        public static final int BODY_POSITION_STANDING_UP = 1;
        public static final int BODY_POSITION_SITTING_DOWN = 2;
        public static final int BODY_POSITION_LYING_DOWN = 3;
        public static final int BODY_POSITION_RECLINING = 4;

        private BodyPosition() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mMeasurementLocation;
        private final Pressure mSystolic;
        private final Pressure mDiastolic;
        private final int mBodyPosition;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int measurementLocation,
                Pressure systolic, Pressure diastolic, int bodyPosition) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mMeasurementLocation = measurementLocation;
            mSystolic = Objects.requireNonNull(systolic, "systolic must not be null");
            mDiastolic = Objects.requireNonNull(diastolic, "diastolic must not be null");
            mBodyPosition = bodyPosition;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BloodPressureRecord build() {
            return new BloodPressureRecord(mMetadata, mTime, mZoneOffset,
                    mMeasurementLocation, mSystolic, mDiastolic, mBodyPosition);
        }
    }
}
