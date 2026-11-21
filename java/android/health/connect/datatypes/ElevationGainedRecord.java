// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Length;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class ElevationGainedRecord extends IntervalRecord {
    public static final AggregationType<Length> ELEVATION_GAINED_TOTAL =
            new AggregationType<Length>(ElevationGainedRecord.class, "elevation",
                    AggregationType.OP_SUM, AggregationType.KIND_LENGTH);

    private final Length mElevation;

    ElevationGainedRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, Length elevation) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mElevation = Objects.requireNonNull(elevation, "elevation must not be null");
    }

    public Length getElevation() {
        return mElevation;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("elevation".equals(metric)) {
            accumulator.addDouble(mElevation.getInMeters());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mElevation, ((ElevationGainedRecord) object).mElevation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mElevation);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final Length mElevation;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, Length elevation) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mElevation = Objects.requireNonNull(elevation, "elevation must not be null");
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

        public ElevationGainedRecord build() {
            return new ElevationGainedRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mElevation);
        }
    }
}
