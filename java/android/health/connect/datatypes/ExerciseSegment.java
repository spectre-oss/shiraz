// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.util.Objects;

public final class ExerciseSegment {
    private final Instant mStartTime;
    private final Instant mEndTime;
    private final int mSegmentType;
    private final int mRepetitionsCount;

    ExerciseSegment(Instant startTime, Instant endTime, int segmentType, int repetitionsCount) {
        mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
        mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (repetitionsCount < 0) {
            throw new IllegalArgumentException("repetitionsCount must not be negative");
        }
        mSegmentType = segmentType;
        mRepetitionsCount = repetitionsCount;
    }

    public int getSegmentType() {
        return mSegmentType;
    }

    public int getRepetitionsCount() {
        return mRepetitionsCount;
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
        if (!(object instanceof ExerciseSegment)) return false;
        ExerciseSegment that = (ExerciseSegment) object;
        return mSegmentType == that.mSegmentType
                && mRepetitionsCount == that.mRepetitionsCount
                && Objects.equals(mStartTime, that.mStartTime)
                && Objects.equals(mEndTime, that.mEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartTime, mEndTime, mSegmentType, mRepetitionsCount);
    }

    public static final class Builder {
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final int mSegmentType;
        private int mRepetitionsCount;

        public Builder(Instant startTime, Instant endTime, int segmentType) {
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mSegmentType = segmentType;
        }

        public Builder setRepetitionsCount(int repetitionsCount) {
            if (repetitionsCount < 0) {
                throw new IllegalArgumentException("repetitionsCount must not be negative");
            }
            mRepetitionsCount = repetitionsCount;
            return this;
        }

        public ExerciseSegment build() {
            return new ExerciseSegment(mStartTime, mEndTime, mSegmentType, mRepetitionsCount);
        }
    }
}
