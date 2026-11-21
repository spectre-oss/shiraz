// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class CervicalMucusRecord extends InstantRecord {
    private final int mSensation;
    private final int mAppearance;

    CervicalMucusRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int sensation, int appearance) {
        super(metadata, time, zoneOffset);
        mSensation = sensation;
        mAppearance = appearance;
    }

    public int getSensation() {
        return mSensation;
    }

    public int getAppearance() {
        return mAppearance;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        CervicalMucusRecord that = (CervicalMucusRecord) object;
        return mSensation == that.mSensation && mAppearance == that.mAppearance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSensation, mAppearance);
    }

    public static final class CervicalMucusAppearance {
        public static final int APPEARANCE_UNKNOWN = 0;
        public static final int APPEARANCE_DRY = 1;
        public static final int APPEARANCE_STICKY = 2;
        public static final int APPEARANCE_CREAMY = 3;
        public static final int APPEARANCE_WATERY = 4;
        public static final int APPEARANCE_EGG_WHITE = 5;
        public static final int APPEARANCE_UNUSUAL = 6;

        private CervicalMucusAppearance() {
        }
    }

    public static final class CervicalMucusSensation {
        public static final int SENSATION_UNKNOWN = 0;
        public static final int SENSATION_LIGHT = 1;
        public static final int SENSATION_MEDIUM = 2;
        public static final int SENSATION_HEAVY = 3;

        private CervicalMucusSensation() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mSensation;
        private final int mAppearance;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int sensation, int appearance) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mSensation = sensation;
            mAppearance = appearance;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public CervicalMucusRecord build() {
            return new CervicalMucusRecord(mMetadata, mTime, mZoneOffset, mSensation, mAppearance);
        }
    }
}
