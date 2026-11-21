// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class RespiratoryRateRecord extends InstantRecord {
    private final double mRate;

    RespiratoryRateRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, double rate) {
        super(metadata, time, zoneOffset);
        if (rate < 0.0 || rate > 1000.0) {
            throw new IllegalArgumentException("rate must be in [0.0, 1000.0]");
        }
        mRate = rate;
    }

    public double getRate() {
        return mRate;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Double.compare(mRate, ((RespiratoryRateRecord) object).mRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mRate);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final double mRate;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, double rate) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            if (rate < 0.0 || rate > 1000.0) {
                throw new IllegalArgumentException("rate must be in [0.0, 1000.0]");
            }
            mRate = rate;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public RespiratoryRateRecord build() {
            return new RespiratoryRateRecord(mMetadata, mTime, mZoneOffset, mRate);
        }
    }
}
