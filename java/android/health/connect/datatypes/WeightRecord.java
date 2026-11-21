// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Mass;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class WeightRecord extends InstantRecord {
    public static final AggregationType<Mass> WEIGHT_AVG =
            new AggregationType<Mass>(WeightRecord.class, "weight",
                    AggregationType.OP_AVG, AggregationType.KIND_MASS);
    public static final AggregationType<Mass> WEIGHT_MAX =
            new AggregationType<Mass>(WeightRecord.class, "weight",
                    AggregationType.OP_MAX, AggregationType.KIND_MASS);
    public static final AggregationType<Mass> WEIGHT_MIN =
            new AggregationType<Mass>(WeightRecord.class, "weight",
                    AggregationType.OP_MIN, AggregationType.KIND_MASS);

    private final Mass mWeight;

    WeightRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, Mass weight) {
        super(metadata, time, zoneOffset);
        mWeight = Objects.requireNonNull(weight, "weight must not be null");
    }

    public Mass getWeight() {
        return mWeight;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("weight".equals(metric)) {
            accumulator.addDouble(mWeight.getInGrams());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mWeight, ((WeightRecord) object).mWeight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mWeight);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Mass mWeight;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Mass weight) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mWeight = Objects.requireNonNull(weight, "weight must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public WeightRecord build() {
            return new WeightRecord(mMetadata, mTime, mZoneOffset, mWeight);
        }
    }
}
