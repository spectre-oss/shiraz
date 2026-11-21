// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Temperature;
import android.health.connect.datatypes.units.TemperatureDelta;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SkinTemperatureRecord extends IntervalRecord {
    public static final int MEASUREMENT_LOCATION_UNKNOWN = 0;
    public static final int MEASUREMENT_LOCATION_FINGER = 1;
    public static final int MEASUREMENT_LOCATION_TOE = 2;
    public static final int MEASUREMENT_LOCATION_WRIST = 3;

    public static final AggregationType<TemperatureDelta> SKIN_TEMPERATURE_DELTA_AVG =
            new AggregationType<TemperatureDelta>(SkinTemperatureRecord.class, "delta",
                    AggregationType.OP_AVG, AggregationType.KIND_TEMP_DELTA);
    public static final AggregationType<TemperatureDelta> SKIN_TEMPERATURE_DELTA_MAX =
            new AggregationType<TemperatureDelta>(SkinTemperatureRecord.class, "delta",
                    AggregationType.OP_MAX, AggregationType.KIND_TEMP_DELTA);
    public static final AggregationType<TemperatureDelta> SKIN_TEMPERATURE_DELTA_MIN =
            new AggregationType<TemperatureDelta>(SkinTemperatureRecord.class, "delta",
                    AggregationType.OP_MIN, AggregationType.KIND_TEMP_DELTA);

    private final Temperature mBaseline;
    private final List<Delta> mDeltas;
    private final int mMeasurementLocation;

    SkinTemperatureRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, Temperature baseline,
            List<Delta> deltas, int measurementLocation) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mBaseline = baseline;
        mDeltas = Collections.unmodifiableList(new ArrayList<Delta>(deltas));
        mMeasurementLocation = measurementLocation;
    }

    public Temperature getBaseline() {
        return mBaseline;
    }

    public List<Delta> getDeltas() {
        return mDeltas;
    }

    public int getMeasurementLocation() {
        return mMeasurementLocation;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("delta".equals(metric)) {
            for (int i = 0; i < mDeltas.size(); i++) {
                accumulator.addDouble(mDeltas.get(i).getDelta().getInCelsius());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        SkinTemperatureRecord that = (SkinTemperatureRecord) object;
        return mMeasurementLocation == that.mMeasurementLocation
                && Objects.equals(mBaseline, that.mBaseline)
                && Objects.equals(mDeltas, that.mDeltas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mBaseline, mDeltas, mMeasurementLocation);
    }

    public static final class Delta {
        private final TemperatureDelta mDelta;
        private final Instant mTime;

        public Delta(TemperatureDelta delta, Instant time) {
            mDelta = Objects.requireNonNull(delta, "delta must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public TemperatureDelta getDelta() {
            return mDelta;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Delta)) return false;
            Delta that = (Delta) object;
            return Objects.equals(mDelta, that.mDelta) && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDelta, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;
        private Temperature mBaseline;
        private List<Delta> mDeltas = new ArrayList<Delta>();
        private int mMeasurementLocation = MEASUREMENT_LOCATION_UNKNOWN;

        public Builder(Metadata metadata, Instant startTime, Instant endTime) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        }

        public Builder setStartZoneOffset(ZoneOffset startZoneOffset) {
            mStartZoneOffset = Objects.requireNonNull(startZoneOffset, "startZoneOffset must not be null");
            return this;
        }

        public Builder setEndZoneOffset(ZoneOffset endZoneOffset) {
            mEndZoneOffset = Objects.requireNonNull(endZoneOffset, "endZoneOffset must not be null");
            return this;
        }

        public Builder clearStartZoneOffset() {
            mStartZoneOffset = null;
            return this;
        }

        public Builder clearEndZoneOffset() {
            mEndZoneOffset = null;
            return this;
        }

        public Builder setBaseline(Temperature baseline) {
            mBaseline = baseline;
            return this;
        }

        public Builder setDeltas(List<Delta> deltas) {
            mDeltas = new ArrayList<Delta>(Objects.requireNonNull(deltas, "deltas must not be null"));
            return this;
        }

        public Builder setMeasurementLocation(int measurementLocation) {
            mMeasurementLocation = measurementLocation;
            return this;
        }

        public SkinTemperatureRecord build() {
            return new SkinTemperatureRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mBaseline, mDeltas, mMeasurementLocation);
        }
    }
}
