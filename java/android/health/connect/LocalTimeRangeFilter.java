// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

import java.time.LocalDateTime;
import java.util.Objects;

public final class LocalTimeRangeFilter implements TimeRangeFilter {
    private final LocalDateTime mStartTime;
    private final LocalDateTime mEndTime;

    LocalTimeRangeFilter(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null && endTime == null) {
            throw new IllegalArgumentException("At least one of startTime or endTime must be set");
        }
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public LocalDateTime getStartTime() {
        return mStartTime;
    }

    public LocalDateTime getEndTime() {
        return mEndTime;
    }

    public boolean isBounded() {
        return mStartTime != null && mEndTime != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalTimeRangeFilter)) return false;
        LocalTimeRangeFilter that = (LocalTimeRangeFilter) o;
        return Objects.equals(mStartTime, that.mStartTime)
                && Objects.equals(mEndTime, that.mEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartTime, mEndTime);
    }

    public static final class Builder {
        private LocalDateTime mStartTime;
        private LocalDateTime mEndTime;

        public Builder() {
        }

        public Builder setStartTime(LocalDateTime startTime) {
            mStartTime = startTime;
            return this;
        }

        public Builder setEndTime(LocalDateTime endTime) {
            mEndTime = endTime;
            return this;
        }

        public LocalTimeRangeFilter build() {
            return new LocalTimeRangeFilter(mStartTime, mEndTime);
        }
    }
}
