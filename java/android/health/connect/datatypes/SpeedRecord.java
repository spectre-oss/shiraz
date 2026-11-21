// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Velocity;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SpeedRecord extends IntervalRecord {
    public static final AggregationType<Velocity> SPEED_AVG =
            new AggregationType<Velocity>(SpeedRecord.class, "speed",
                    AggregationType.OP_AVG, AggregationType.KIND_VELOCITY);
    public static final AggregationType<Velocity> SPEED_MAX =
            new AggregationType<Velocity>(SpeedRecord.class, "speed",
                    AggregationType.OP_MAX, AggregationType.KIND_VELOCITY);
    public static final AggregationType<Velocity> SPEED_MIN =
            new AggregationType<Velocity>(SpeedRecord.class, "speed",
                    AggregationType.OP_MIN, AggregationType.KIND_VELOCITY);

    private final List<SpeedRecordSample> mSamples;

    SpeedRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, List<SpeedRecordSample> samples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mSamples = Collections.unmodifiableList(new ArrayList<SpeedRecordSample>(samples));
    }

    public List<SpeedRecordSample> getSamples() {
        return mSamples;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("speed".equals(metric)) {
            for (int i = 0; i < mSamples.size(); i++) {
                accumulator.addDouble(mSamples.get(i).getSpeed().getInMetersPerSecond());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mSamples, ((SpeedRecord) object).mSamples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSamples);
    }

    public static final class SpeedRecordSample {
        private final Velocity mSpeed;
        private final Instant mTime;

        public SpeedRecordSample(Velocity speed, Instant time) {
            mSpeed = Objects.requireNonNull(speed, "speed must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public Velocity getSpeed() {
            return mSpeed;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof SpeedRecordSample)) return false;
            SpeedRecordSample that = (SpeedRecordSample) object;
            return Objects.equals(mSpeed, that.mSpeed) && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSpeed, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<SpeedRecordSample> mSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime,
                List<SpeedRecordSample> samples) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mSamples = new ArrayList<SpeedRecordSample>(
                    Objects.requireNonNull(samples, "samples must not be null"));
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

        public SpeedRecord build() {
            return new SpeedRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mSamples);
        }
    }
}
