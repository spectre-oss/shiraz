// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public abstract class IntervalRecord extends Record {
    private final Instant mStartTime;
    private final ZoneOffset mStartZoneOffset;
    private final Instant mEndTime;
    private final ZoneOffset mEndZoneOffset;

    IntervalRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset) {
        super(metadata);
        mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
        mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }
        mStartZoneOffset = startZoneOffset != null
                ? startZoneOffset
                : ZoneId.systemDefault().getRules().getOffset(startTime);
        mEndZoneOffset = endZoneOffset != null
                ? endZoneOffset
                : ZoneId.systemDefault().getRules().getOffset(endTime);
    }

    public Instant getStartTime() {
        return mStartTime;
    }

    public ZoneOffset getStartZoneOffset() {
        return mStartZoneOffset;
    }

    public Instant getEndTime() {
        return mEndTime;
    }

    public ZoneOffset getEndZoneOffset() {
        return mEndZoneOffset;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        IntervalRecord that = (IntervalRecord) object;
        return Objects.equals(mStartTime, that.mStartTime)
                && Objects.equals(mStartZoneOffset, that.mStartZoneOffset)
                && Objects.equals(mEndTime, that.mEndTime)
                && Objects.equals(mEndZoneOffset, that.mEndZoneOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mStartTime, mStartZoneOffset, mEndTime, mEndZoneOffset);
    }
}
