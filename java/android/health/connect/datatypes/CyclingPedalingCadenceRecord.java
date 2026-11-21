// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CyclingPedalingCadenceRecord extends IntervalRecord {
    public static final AggregationType<Double> RPM_AVG =
            new AggregationType<Double>(CyclingPedalingCadenceRecord.class, "rpm",
                    AggregationType.OP_AVG, AggregationType.KIND_DOUBLE);
    public static final AggregationType<Double> RPM_MAX =
            new AggregationType<Double>(CyclingPedalingCadenceRecord.class, "rpm",
                    AggregationType.OP_MAX, AggregationType.KIND_DOUBLE);
    public static final AggregationType<Double> RPM_MIN =
            new AggregationType<Double>(CyclingPedalingCadenceRecord.class, "rpm",
                    AggregationType.OP_MIN, AggregationType.KIND_DOUBLE);

    private final List<CyclingPedalingCadenceRecordSample> mSamples;

    CyclingPedalingCadenceRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset,
            List<CyclingPedalingCadenceRecordSample> samples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mSamples = Collections.unmodifiableList(
                new ArrayList<CyclingPedalingCadenceRecordSample>(samples));
    }

    public List<CyclingPedalingCadenceRecordSample> getSamples() {
        return mSamples;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("rpm".equals(metric)) {
            for (int i = 0; i < mSamples.size(); i++) {
                accumulator.addDouble(mSamples.get(i).getRevolutionsPerMinute());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mSamples, ((CyclingPedalingCadenceRecord) object).mSamples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSamples);
    }

    public static final class CyclingPedalingCadenceRecordSample {
        private final double mRevolutionsPerMinute;
        private final Instant mTime;

        public CyclingPedalingCadenceRecordSample(double revolutionsPerMinute, Instant time) {
            if (revolutionsPerMinute < 0.0) {
                throw new IllegalArgumentException("revolutionsPerMinute must not be negative");
            }
            mRevolutionsPerMinute = revolutionsPerMinute;
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public double getRevolutionsPerMinute() {
            return mRevolutionsPerMinute;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CyclingPedalingCadenceRecordSample)) return false;
            CyclingPedalingCadenceRecordSample that = (CyclingPedalingCadenceRecordSample) object;
            return Double.compare(mRevolutionsPerMinute, that.mRevolutionsPerMinute) == 0
                    && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mRevolutionsPerMinute, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<CyclingPedalingCadenceRecordSample> mSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime,
                List<CyclingPedalingCadenceRecordSample> samples) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mSamples = new ArrayList<CyclingPedalingCadenceRecordSample>(
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

        public CyclingPedalingCadenceRecord build() {
            return new CyclingPedalingCadenceRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mSamples);
        }
    }
}
