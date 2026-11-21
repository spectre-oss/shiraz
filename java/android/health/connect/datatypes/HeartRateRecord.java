// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class HeartRateRecord extends IntervalRecord {
    public static final AggregationType<Long> BPM_AVG =
            new AggregationType<Long>(HeartRateRecord.class, "bpm",
                    AggregationType.OP_AVG, AggregationType.KIND_LONG);
    public static final AggregationType<Long> BPM_MAX =
            new AggregationType<Long>(HeartRateRecord.class, "bpm",
                    AggregationType.OP_MAX, AggregationType.KIND_LONG);
    public static final AggregationType<Long> BPM_MIN =
            new AggregationType<Long>(HeartRateRecord.class, "bpm",
                    AggregationType.OP_MIN, AggregationType.KIND_LONG);
    public static final AggregationType<Long> HEART_MEASUREMENTS_COUNT =
            new AggregationType<Long>(HeartRateRecord.class, "bpm",
                    AggregationType.OP_COUNT, AggregationType.KIND_LONG);

    private final List<HeartRateSample> mSamples;

    HeartRateRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, List<HeartRateSample> samples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mSamples = Collections.unmodifiableList(new ArrayList<HeartRateSample>(samples));
    }

    public List<HeartRateSample> getSamples() {
        return mSamples;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("bpm".equals(metric)) {
            for (int i = 0; i < mSamples.size(); i++) {
                accumulator.addLong(mSamples.get(i).getBeatsPerMinute());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mSamples, ((HeartRateRecord) object).mSamples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSamples);
    }

    public static final class HeartRateSample {
        private final long mBeatsPerMinute;
        private final Instant mTime;

        public HeartRateSample(long beatsPerMinute, Instant time) {
            if (beatsPerMinute < 1 || beatsPerMinute > 300) {
                throw new IllegalArgumentException("beatsPerMinute must be in [1, 300]");
            }
            mBeatsPerMinute = beatsPerMinute;
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public long getBeatsPerMinute() {
            return mBeatsPerMinute;
        }

        public Instant getTime() {
            return mTime;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof HeartRateSample)) return false;
            HeartRateSample that = (HeartRateSample) object;
            return mBeatsPerMinute == that.mBeatsPerMinute && Objects.equals(mTime, that.mTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mBeatsPerMinute, mTime);
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<HeartRateSample> mSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        public Builder(Metadata metadata, Instant startTime, Instant endTime,
                List<HeartRateSample> samples) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            Objects.requireNonNull(samples, "samples must not be null");
            if (samples.isEmpty()) {
                throw new IllegalArgumentException("samples must not be empty");
            }
            mSamples = new ArrayList<HeartRateSample>(samples);
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

        public HeartRateRecord build() {
            return new HeartRateRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mSamples);
        }
    }
}
