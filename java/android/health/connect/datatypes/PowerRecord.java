// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Power;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PowerRecord extends IntervalRecord {
    public static final AggregationType<Power> POWER_AVG =
            new AggregationType<Power>(PowerRecord.class, "power",
                    AggregationType.OP_AVG, AggregationType.KIND_POWER);
    public static final AggregationType<Power> POWER_MAX =
            new AggregationType<Power>(PowerRecord.class, "power",
                    AggregationType.OP_MAX, AggregationType.KIND_POWER);
    public static final AggregationType<Power> POWER_MIN =
            new AggregationType<Power>(PowerRecord.class, "power",
                    AggregationType.OP_MIN, AggregationType.KIND_POWER);

    private final List<PowerRecordSample> mSamples;

    PowerRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, List<PowerRecordSample> samples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mSamples = Collections.unmodifiableList(new ArrayList<PowerRecordSample>(samples));
    }

    public List<PowerRecordSample> getSamples() {
        return mSamples;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("power".equals(metric)) {
            for (int i = 0; i < mSamples.size(); i++) {
                accumulator.addDouble(mSamples.get(i).getPower().getInWatts());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mSamples, ((PowerRecord) object).mSamples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSamples);
    }

    public static final class PowerRecordSample {
        private final Power mPower;
        private final Instant mTime;

        public PowerRecordSample(Power power, Instant time) {
            mPower = Objects.requireNonNull(power, "power must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public Power getPower() {
            return mPower;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof PowerRecordSample)) return false;
            PowerRecordSample that = (PowerRecordSample) object;
            return Objects.equals(mPower, that.mPower) && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPower, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<PowerRecordSample> mSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime,
                List<PowerRecordSample> samples) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mSamples = new ArrayList<PowerRecordSample>(
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

        public PowerRecord build() {
            return new PowerRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mSamples);
        }
    }
}
