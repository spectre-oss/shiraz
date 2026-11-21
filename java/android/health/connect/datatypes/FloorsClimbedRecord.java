// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class FloorsClimbedRecord extends IntervalRecord {
    public static final AggregationType<Double> FLOORS_CLIMBED_TOTAL =
            new AggregationType<Double>(FloorsClimbedRecord.class, "floors",
                    AggregationType.OP_SUM, AggregationType.KIND_DOUBLE);

    private final double mFloors;

    FloorsClimbedRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, double floors) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        if (floors < 0.0) {
            throw new IllegalArgumentException("floors must not be negative");
        }
        mFloors = floors;
    }

    public double getFloors() {
        return mFloors;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("floors".equals(metric)) {
            accumulator.addDouble(mFloors);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Double.compare(mFloors, ((FloorsClimbedRecord) object).mFloors) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mFloors);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final double mFloors;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, double floors) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            if (floors < 0.0) {
                throw new IllegalArgumentException("floors must not be negative");
            }
            mFloors = floors;
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

        public FloorsClimbedRecord build() {
            return new FloorsClimbedRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mFloors);
        }
    }
}
