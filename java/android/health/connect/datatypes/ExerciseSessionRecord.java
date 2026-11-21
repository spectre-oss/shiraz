// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ExerciseSessionRecord extends IntervalRecord {
    public static final AggregationType<Long> EXERCISE_DURATION_TOTAL =
            new AggregationType<Long>(ExerciseSessionRecord.class, "duration_ms",
                    AggregationType.OP_SUM, AggregationType.KIND_LONG);

    private final int mExerciseType;
    private final CharSequence mNotes;
    private final CharSequence mTitle;
    private final ExerciseRoute mRoute;
    private final List<ExerciseSegment> mSegments;
    private final List<ExerciseLap> mLaps;
    private final boolean mHasRoute;
    private final String mPlannedExerciseSessionId;

    ExerciseSessionRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, int exerciseType,
            CharSequence notes, CharSequence title, ExerciseRoute route,
            List<ExerciseSegment> segments, List<ExerciseLap> laps,
            String plannedExerciseSessionId) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mExerciseType = exerciseType;
        mNotes = notes;
        mTitle = title;
        mRoute = route;
        mHasRoute = route != null;
        mSegments = Collections.unmodifiableList(new ArrayList<ExerciseSegment>(segments));
        mLaps = Collections.unmodifiableList(new ArrayList<ExerciseLap>(laps));
        mPlannedExerciseSessionId = plannedExerciseSessionId;
    }

    public int getExerciseType() {
        return mExerciseType;
    }

    public CharSequence getNotes() {
        return mNotes;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public ExerciseRoute getRoute() {
        return mRoute;
    }

    public List<ExerciseSegment> getSegments() {
        return mSegments;
    }

    public List<ExerciseLap> getLaps() {
        return mLaps;
    }

    public boolean hasRoute() {
        return mHasRoute;
    }

    public String getPlannedExerciseSessionId() {
        return mPlannedExerciseSessionId;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("duration_ms".equals(metric)) {
            accumulator.addLong(Duration.between(getStartTime(), getEndTime()).toMillis());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        ExerciseSessionRecord that = (ExerciseSessionRecord) object;
        return mExerciseType == that.mExerciseType
                && mHasRoute == that.mHasRoute
                && Objects.equals(mNotes != null ? mNotes.toString() : null,
                        that.mNotes != null ? that.mNotes.toString() : null)
                && Objects.equals(mTitle != null ? mTitle.toString() : null,
                        that.mTitle != null ? that.mTitle.toString() : null)
                && Objects.equals(mRoute, that.mRoute)
                && Objects.equals(mSegments, that.mSegments)
                && Objects.equals(mLaps, that.mLaps)
                && Objects.equals(mPlannedExerciseSessionId, that.mPlannedExerciseSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mExerciseType, mHasRoute,
                mNotes != null ? mNotes.toString() : null,
                mTitle != null ? mTitle.toString() : null,
                mRoute, mSegments, mLaps, mPlannedExerciseSessionId);
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final int mExerciseType;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;
        private CharSequence mNotes;
        private CharSequence mTitle;
        private ExerciseRoute mRoute;
        private List<ExerciseLap> mLaps = Collections.emptyList();
        private List<ExerciseSegment> mSegments = Collections.emptyList();
        private String mPlannedExerciseSessionId;

        public Builder(Metadata metadata, Instant startTime, Instant endTime, int exerciseType) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mExerciseType = exerciseType;
        }

        public Builder setStartZoneOffset(ZoneOffset startZoneOffset) {
            mStartZoneOffset = Objects.requireNonNull(startZoneOffset, "startZoneOffset must not be null");
            return this;
        }

        public Builder setEndZoneOffset(ZoneOffset endZoneOffset) {
            mEndZoneOffset = Objects.requireNonNull(endZoneOffset, "endZoneOffset must not be null");
            return this;
        }

        public Builder clearStartZoneOffset() {
            mStartZoneOffset = null;
            return this;
        }

        public Builder clearEndZoneOffset() {
            mEndZoneOffset = null;
            return this;
        }

        public Builder setNotes(CharSequence notes) {
            mNotes = notes;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            mTitle = title;
            return this;
        }

        public Builder setRoute(ExerciseRoute route) {
            mRoute = route;
            return this;
        }

        public Builder setLaps(List<ExerciseLap> laps) {
            mLaps = new ArrayList<ExerciseLap>(Objects.requireNonNull(laps, "laps must not be null"));
            return this;
        }

        public Builder setSegments(List<ExerciseSegment> segments) {
            mSegments = new ArrayList<ExerciseSegment>(
                    Objects.requireNonNull(segments, "segments must not be null"));
            return this;
        }

        public Builder setPlannedExerciseSessionId(String id) {
            mPlannedExerciseSessionId = id;
            return this;
        }

        public ExerciseSessionRecord build() {
            return new ExerciseSessionRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mExerciseType, mNotes, mTitle, mRoute,
                    mSegments, mLaps, mPlannedExerciseSessionId);
        }
    }
}
