// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class StepsRecord extends IntervalRecord {
    public static final AggregationType<Long> STEPS_COUNT_TOTAL =
            new AggregationType<Long>(StepsRecord.class, "count",
                    AggregationType.OP_SUM, AggregationType.KIND_LONG);

    private final long mCount;

    StepsRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, long count) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        if (count < 0 || count > 1000000L) {
            throw new IllegalArgumentException("count must be in [0, 1000000]");
        }
        mCount = count;
    }

    public long getCount() {
        return mCount;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("count".equals(metric)) {
            accumulator.addLong(mCount);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return mCount == ((StepsRecord) object).mCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mCount);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final long mCount;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, long count) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            if (count < 0 || count > 1000000L) {
                throw new IllegalArgumentException("count must be in [0, 1000000]");
            }
            mCount = count;
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

        public StepsRecord build() {
            return new StepsRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mCount);
        }
    }
}
