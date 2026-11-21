// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class OvulationTestRecord extends InstantRecord {
    private final int mResult;

    OvulationTestRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, int result) {
        super(metadata, time, zoneOffset);
        mResult = result;
    }

    public int getResult() {
        return mResult;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return mResult == ((OvulationTestRecord) object).mResult;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mResult);
    }

    public static final class OvulationTestResult {
        public static final int RESULT_INCONCLUSIVE = 0;
        public static final int RESULT_POSITIVE = 1;
        public static final int RESULT_HIGH = 2;
        public static final int RESULT_NEGATIVE = 3;

        private OvulationTestResult() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mResult;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int result) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mResult = result;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public OvulationTestRecord build() {
            return new OvulationTestRecord(mMetadata, mTime, mZoneOffset, mResult);
        }
    }
}
