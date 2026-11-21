// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class RestingHeartRateRecord extends InstantRecord {
    public static final AggregationType<Long> BPM_AVG =
            new AggregationType<Long>(RestingHeartRateRecord.class, "bpm",
                    AggregationType.OP_AVG, AggregationType.KIND_LONG);
    public static final AggregationType<Long> BPM_MAX =
            new AggregationType<Long>(RestingHeartRateRecord.class, "bpm",
                    AggregationType.OP_MAX, AggregationType.KIND_LONG);
    public static final AggregationType<Long> BPM_MIN =
            new AggregationType<Long>(RestingHeartRateRecord.class, "bpm",
                    AggregationType.OP_MIN, AggregationType.KIND_LONG);

    private final long mBeatsPerMinute;

    RestingHeartRateRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            long beatsPerMinute) {
        super(metadata, time, zoneOffset);
        if (beatsPerMinute < 1 || beatsPerMinute > 300) {
            throw new IllegalArgumentException("beatsPerMinute must be in [1, 300]");
        }
        mBeatsPerMinute = beatsPerMinute;
    }

    public long getBeatsPerMinute() {
        return mBeatsPerMinute;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("bpm".equals(metric)) {
            accumulator.addLong(mBeatsPerMinute);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return mBeatsPerMinute == ((RestingHeartRateRecord) object).mBeatsPerMinute;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mBeatsPerMinute);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final long mBeatsPerMinute;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, long beatsPerMinute) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            if (beatsPerMinute < 1 || beatsPerMinute > 300) {
                throw new IllegalArgumentException("beatsPerMinute must be in [1, 300]");
            }
            mBeatsPerMinute = beatsPerMinute;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public RestingHeartRateRecord build() {
            return new RestingHeartRateRecord(mMetadata, mTime, mZoneOffset, mBeatsPerMinute);
        }
    }
}
