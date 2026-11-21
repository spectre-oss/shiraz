// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Percentage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class OxygenSaturationRecord extends InstantRecord {
    private final Percentage mPercentage;

    OxygenSaturationRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            Percentage percentage) {
        super(metadata, time, zoneOffset);
        mPercentage = Objects.requireNonNull(percentage, "percentage must not be null");
    }

    public Percentage getPercentage() {
        return mPercentage;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mPercentage, ((OxygenSaturationRecord) object).mPercentage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mPercentage);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Percentage mPercentage;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Percentage percentage) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mPercentage = Objects.requireNonNull(percentage, "percentage must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public OxygenSaturationRecord build() {
            return new OxygenSaturationRecord(mMetadata, mTime, mZoneOffset, mPercentage);
        }
    }
}
