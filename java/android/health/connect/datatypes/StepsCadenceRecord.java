// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StepsCadenceRecord extends IntervalRecord {
    public static final AggregationType<Double> STEPS_CADENCE_RATE_AVG =
            new AggregationType<Double>(StepsCadenceRecord.class, "rate",
                    AggregationType.OP_AVG, AggregationType.KIND_DOUBLE);
    public static final AggregationType<Double> STEPS_CADENCE_RATE_MAX =
            new AggregationType<Double>(StepsCadenceRecord.class, "rate",
                    AggregationType.OP_MAX, AggregationType.KIND_DOUBLE);
    public static final AggregationType<Double> STEPS_CADENCE_RATE_MIN =
            new AggregationType<Double>(StepsCadenceRecord.class, "rate",
                    AggregationType.OP_MIN, AggregationType.KIND_DOUBLE);

    private final List<StepsCadenceRecordSample> mSamples;

    StepsCadenceRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, List<StepsCadenceRecordSample> samples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mSamples = Collections.unmodifiableList(new ArrayList<StepsCadenceRecordSample>(samples));
    }

    public List<StepsCadenceRecordSample> getSamples() {
        return mSamples;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("rate".equals(metric)) {
            for (int i = 0; i < mSamples.size(); i++) {
                accumulator.addDouble(mSamples.get(i).getRate());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mSamples, ((StepsCadenceRecord) object).mSamples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSamples);
    }

    public static final class StepsCadenceRecordSample {
        private final double mRate;
        private final Instant mTime;

        public StepsCadenceRecordSample(double rate, Instant time) {
            if (rate < 0.0 || rate > 10000.0) {
                throw new IllegalArgumentException("rate must be in [0.0, 10000.0]");
            }
            mRate = rate;
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public double getRate() {
            return mRate;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof StepsCadenceRecordSample)) return false;
            StepsCadenceRecordSample that = (StepsCadenceRecordSample) object;
            return Double.compare(mRate, that.mRate) == 0 && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mRate, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<StepsCadenceRecordSample> mSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime,
                List<StepsCadenceRecordSample> samples) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mSamples = new ArrayList<StepsCadenceRecordSample>(
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

        public StepsCadenceRecord build() {
            return new StepsCadenceRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mSamples);
        }
    }
}
