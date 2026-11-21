// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class IntermenstrualBleedingRecord extends InstantRecord {
    IntermenstrualBleedingRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset) {
        super(metadata, time, zoneOffset);
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public IntermenstrualBleedingRecord build() {
            return new IntermenstrualBleedingRecord(mMetadata, mTime, mZoneOffset);
        }
    }
}
