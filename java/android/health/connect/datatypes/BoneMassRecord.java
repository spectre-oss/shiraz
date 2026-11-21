// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Mass;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BoneMassRecord extends InstantRecord {
    private final Mass mMass;

    BoneMassRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, Mass mass) {
        super(metadata, time, zoneOffset);
        mMass = Objects.requireNonNull(mass, "mass must not be null");
    }

    public Mass getMass() {
        return mMass;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return Objects.equals(mMass, ((BoneMassRecord) object).mMass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMass);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final Mass mMass;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, Mass mass) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mMass = Objects.requireNonNull(mass, "mass must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BoneMassRecord build() {
            return new BoneMassRecord(mMetadata, mTime, mZoneOffset, mMass);
        }
    }
}
