// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import java.time.Instant;
import java.util.Objects;

public final class TimeInstantRangeFilter implements TimeRangeFilter {
    private final Instant mStartTime;
    private final Instant mEndTime;

    TimeInstantRangeFilter(Instant startTime, Instant endTime) {
        if (startTime == null && endTime == null) {
            throw new IllegalArgumentException("At least one of startTime or endTime must be set");
        }
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public Instant getStartTime() {
        return mStartTime;
    }

    public Instant getEndTime() {
        return mEndTime;
    }

    public boolean isBounded() {
        return mStartTime != null && mEndTime != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeInstantRangeFilter)) return false;
        TimeInstantRangeFilter that = (TimeInstantRangeFilter) o;
        return Objects.equals(mStartTime, that.mStartTime)
                && Objects.equals(mEndTime, that.mEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartTime, mEndTime);
    }

    public static final class Builder {
        private Instant mStartTime;
        private Instant mEndTime;

        public Builder() {
        }

        public Builder setStartTime(Instant startTime) {
            mStartTime = startTime;
            return this;
        }

        public Builder setEndTime(Instant endTime) {
            mEndTime = endTime;
            return this;
        }

        public TimeInstantRangeFilter build() {
            return new TimeInstantRangeFilter(mStartTime, mEndTime);
        }
    }
}
