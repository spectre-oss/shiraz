// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Power;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BasalMetabolicRateRecord extends InstantRecord {
    public static final AggregationType<Energy> BASAL_CALORIES_TOTAL =
            new AggregationType<Energy>(BasalMetabolicRateRecord.class, "basal_calories",
                    AggregationType.OP_SUM, AggregationType.KIND_ENERGY);

    private final Power mBasalMetabolicRate;

    BasalMetabolicRateRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            Power basalMetabolicRate) {
        super(metadata, time, zoneOffset);
        mBasalMetabolicRate = Objects.requireNonNull(basalMetabolicRate, "basalMetabolicRate must not be null");
    }

    public Power getBasalMetabolicRate() {
        return mBasalMetabolicRate;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("basal_calories".equals(metric)) {
            // 1 watt * 1 day (86400s) / 4.184 J/cal
            accumulator.addDouble(mBasalMetabolicRate.getInWatts() * 86400.0 / 4.184);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mBasalMetabolicRate, ((BasalMetabolicRateRecord) object).mBasalMetabolicRate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mBasalMetabolicRate);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Power mBasalMetabolicRate;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Power basalMetabolicRate) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mBasalMetabolicRate = Objects.requireNonNull(basalMetabolicRate, "basalMetabolicRate must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BasalMetabolicRateRecord build() {
            return new BasalMetabolicRateRecord(mMetadata, mTime, mZoneOffset, mBasalMetabolicRate);
        }
    }
}
