// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Length;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class DistanceRecord extends IntervalRecord {
    public static final AggregationType<Length> DISTANCE_TOTAL =
            new AggregationType<Length>(DistanceRecord.class, "distance",
                    AggregationType.OP_SUM, AggregationType.KIND_LENGTH);

    private final Length mDistance;

    DistanceRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, Length distance) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mDistance = Objects.requireNonNull(distance, "distance must not be null");
    }

    public Length getDistance() {
        return mDistance;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("distance".equals(metric)) {
            accumulator.addDouble(mDistance.getInMeters());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mDistance, ((DistanceRecord) object).mDistance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mDistance);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final Length mDistance;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, Length distance) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mDistance = Objects.requireNonNull(distance, "distance must not be null");
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

        public DistanceRecord build() {
            return new DistanceRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mDistance);
        }
    }
}
