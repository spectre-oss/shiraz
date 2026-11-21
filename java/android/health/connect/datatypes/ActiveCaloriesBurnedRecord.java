// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Energy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class ActiveCaloriesBurnedRecord extends IntervalRecord {
    public static final AggregationType<Energy> ACTIVE_CALORIES_TOTAL =
            new AggregationType<Energy>(ActiveCaloriesBurnedRecord.class, "energy",
                    AggregationType.OP_SUM, AggregationType.KIND_ENERGY);

    private final Energy mEnergy;

    ActiveCaloriesBurnedRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, Energy energy) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mEnergy = Objects.requireNonNull(energy, "energy must not be null");
    }

    public Energy getEnergy() {
        return mEnergy;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("energy".equals(metric)) {
            accumulator.addDouble(mEnergy.getInCalories());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mEnergy, ((ActiveCaloriesBurnedRecord) object).mEnergy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mEnergy);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final Energy mEnergy;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, Energy energy) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mEnergy = Objects.requireNonNull(energy, "energy must not be null");
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

        public ActiveCaloriesBurnedRecord build() {
            return new ActiveCaloriesBurnedRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mEnergy);
        }
    }
}
