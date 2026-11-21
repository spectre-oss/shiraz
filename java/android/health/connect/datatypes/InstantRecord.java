// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public abstract class InstantRecord extends Record {
    private final Instant mTime;
    private final ZoneOffset mZoneOffset;

    InstantRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset) {
        super(metadata);
        mTime = Objects.requireNonNull(time, "time must not be null");
        mZoneOffset = zoneOffset != null
                ? zoneOffset
                : ZoneId.systemDefault().getRules().getOffset(time);
    }

    public Instant getTime() {
        return mTime;
    }

    public ZoneOffset getZoneOffset() {
        return mZoneOffset;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        InstantRecord that = (InstantRecord) object;
        return Objects.equals(mTime, that.mTime) && Objects.equals(mZoneOffset, that.mZoneOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mTime, mZoneOffset);
    }
}
