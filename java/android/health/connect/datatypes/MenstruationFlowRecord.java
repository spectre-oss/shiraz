// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class MenstruationFlowRecord extends InstantRecord {
    private final int mFlow;

    MenstruationFlowRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset, int flow) {
        super(metadata, time, zoneOffset);
        mFlow = flow;
    }

    public int getFlow() {
        return mFlow;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        return mFlow == ((MenstruationFlowRecord) object).mFlow;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mFlow);
    }

    public static final class MenstruationFlowType {
        public static final int FLOW_UNKNOWN = 0;
        public static final int FLOW_LIGHT = 1;
        public static final int FLOW_MEDIUM = 2;
        public static final int FLOW_HEAVY = 3;

        private MenstruationFlowType() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mFlow;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int flow) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mFlow = flow;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public MenstruationFlowRecord build() {
            return new MenstruationFlowRecord(mMetadata, mTime, mZoneOffset, mFlow);
        }
    }
}
