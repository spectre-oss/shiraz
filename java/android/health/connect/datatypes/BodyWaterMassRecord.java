// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Mass;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BodyWaterMassRecord extends InstantRecord {
    private final Mass mBodyWaterMass;

    BodyWaterMassRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, Mass bodyWaterMass) {
        super(metadata, time, zoneOffset);
        mBodyWaterMass = Objects.requireNonNull(bodyWaterMass, "bodyWaterMass must not be null");
    }

    public Mass getBodyWaterMass() {
        return mBodyWaterMass;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mBodyWaterMass, ((BodyWaterMassRecord) object).mBodyWaterMass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mBodyWaterMass);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Mass mBodyWaterMass;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Mass bodyWaterMass) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mBodyWaterMass = Objects.requireNonNull(bodyWaterMass, "bodyWaterMass must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BodyWaterMassRecord build() {
            return new BodyWaterMassRecord(mMetadata, mTime, mZoneOffset, mBodyWaterMass);
        }
    }
}
