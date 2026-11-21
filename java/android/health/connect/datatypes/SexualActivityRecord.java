// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class SexualActivityRecord extends InstantRecord {
    private final int mProtectionUsed;

    SexualActivityRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int protectionUsed) {
        super(metadata, time, zoneOffset);
        mProtectionUsed = protectionUsed;
    }

    public int getProtectionUsed() {
        return mProtectionUsed;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return mProtectionUsed == ((SexualActivityRecord) object).mProtectionUsed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mProtectionUsed);
    }

    public static final class SexualActivityProtectionUsed {
        public static final int PROTECTION_USED_UNKNOWN = 0;
        public static final int PROTECTION_USED_PROTECTED = 1;
        public static final int PROTECTION_USED_UNPROTECTED = 2;

        private SexualActivityProtectionUsed() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mProtectionUsed;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int protectionUsed) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mProtectionUsed = protectionUsed;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public SexualActivityRecord build() {
            return new SexualActivityRecord(mMetadata, mTime, mZoneOffset, mProtectionUsed);
        }
    }
}
