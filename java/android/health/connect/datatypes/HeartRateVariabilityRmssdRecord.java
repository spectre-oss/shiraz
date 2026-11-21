// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class HeartRateVariabilityRmssdRecord extends InstantRecord {
    private final double mHeartRateVariabilityMillis;

    HeartRateVariabilityRmssdRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            double heartRateVariabilityMillis) {
        super(metadata, time, zoneOffset);
        if (heartRateVariabilityMillis < 1.0 || heartRateVariabilityMillis > 200.0) {
            throw new IllegalArgumentException("heartRateVariabilityMillis must be in [1.0, 200.0]");
        }
        mHeartRateVariabilityMillis = heartRateVariabilityMillis;
    }

    public double getHeartRateVariabilityMillis() {
        return mHeartRateVariabilityMillis;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Double.compare(mHeartRateVariabilityMillis,
                ((HeartRateVariabilityRmssdRecord) object).mHeartRateVariabilityMillis) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mHeartRateVariabilityMillis);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final double mHeartRateVariabilityMillis;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, double heartRateVariabilityMillis) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            if (heartRateVariabilityMillis < 1.0 || heartRateVariabilityMillis > 200.0) {
                throw new IllegalArgumentException("heartRateVariabilityMillis must be in [1.0, 200.0]");
            }
            mHeartRateVariabilityMillis = heartRateVariabilityMillis;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public HeartRateVariabilityRmssdRecord build() {
            return new HeartRateVariabilityRmssdRecord(
                    mMetadata, mTime, mZoneOffset, mHeartRateVariabilityMillis);
        }
    }
}
