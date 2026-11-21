// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Volume;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class HydrationRecord extends IntervalRecord {
    public static final AggregationType<Volume> VOLUME_TOTAL =
            new AggregationType<Volume>(HydrationRecord.class, "volume",
                    AggregationType.OP_SUM, AggregationType.KIND_VOLUME);

    private final Volume mVolume;

    HydrationRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, Volume volume) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mVolume = Objects.requireNonNull(volume, "volume must not be null");
    }

    public Volume getVolume() {
        return mVolume;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("volume".equals(metric)) {
            accumulator.addDouble(mVolume.getInLiters());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mVolume, ((HydrationRecord) object).mVolume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mVolume);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final Volume mVolume;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, Volume volume) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mVolume = Objects.requireNonNull(volume, "volume must not be null");
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

        public HydrationRecord build() {
            return new HydrationRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mVolume);
        }
    }
}
