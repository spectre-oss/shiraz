// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Length;
import java.time.Instant;
import java.util.Objects;

public final class ExerciseLap {
    private final Instant mStartTime;
    private final Instant mEndTime;
    private final Length mLength;

    ExerciseLap(Instant startTime, Instant endTime, Length length) {
        mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
        mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        mLength = length;
    }

    public Length getLength() {
        return mLength;
    }

    public Instant getStartTime() {
        return mStartTime;
    }

    public Instant getEndTime() {
        return mEndTime;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ExerciseLap)) return false;
        ExerciseLap that = (ExerciseLap) object;
        return Objects.equals(mStartTime, that.mStartTime)
                && Objects.equals(mEndTime, that.mEndTime)
                && Objects.equals(mLength, that.mLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartTime, mEndTime, mLength);
    }

    public static final class Builder {
        private final Instant mStartTime;
        private final Instant mEndTime;
        private Length mLength;

        public Builder(Instant startTime, Instant endTime) {
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        }

        public Builder setLength(Length length) {
            mLength = length;
            return this;
        }

        public ExerciseLap build() {
            return new ExerciseLap(mStartTime, mEndTime, mLength);
        }
    }
}
