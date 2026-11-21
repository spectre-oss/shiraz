// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlannedExerciseSessionRecord extends IntervalRecord {
    private final int mExerciseType;
    private final CharSequence mNotes;
    private final CharSequence mTitle;
    private final boolean mHasExplicitTime;
    private final String mCompletedExerciseSessionId;
    private final List<PlannedExerciseBlock> mBlocks;

    PlannedExerciseSessionRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, int exerciseType,
            CharSequence notes, CharSequence title, boolean hasExplicitTime,
            String completedExerciseSessionId, List<PlannedExerciseBlock> blocks) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mExerciseType = exerciseType;
        mNotes = notes;
        mTitle = title;
        mHasExplicitTime = hasExplicitTime;
        mCompletedExerciseSessionId = completedExerciseSessionId;
        mBlocks = Collections.unmodifiableList(new ArrayList<PlannedExerciseBlock>(blocks));
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

    public LocalDate getStartDate() {
        return getStartTime().atOffset(getStartZoneOffset()).toLocalDate();
    }

    public Duration getDuration() {
        return Duration.between(getStartTime(), getEndTime());
    }

    public boolean hasExplicitTime() {
        return mHasExplicitTime;
    }

    public String getCompletedExerciseSessionId() {
        return mCompletedExerciseSessionId;
    }

    public List<PlannedExerciseBlock> getBlocks() {
        return mBlocks;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        PlannedExerciseSessionRecord that = (PlannedExerciseSessionRecord) object;
        return mExerciseType == that.mExerciseType
                && mHasExplicitTime == that.mHasExplicitTime
                && Objects.equals(mNotes != null ? mNotes.toString() : null,
                        that.mNotes != null ? that.mNotes.toString() : null)
                && Objects.equals(mTitle != null ? mTitle.toString() : null,
                        that.mTitle != null ? that.mTitle.toString() : null)
                && Objects.equals(mCompletedExerciseSessionId, that.mCompletedExerciseSessionId)
                && Objects.equals(mBlocks, that.mBlocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mExerciseType, mHasExplicitTime,
                mNotes != null ? mNotes.toString() : null,
                mTitle != null ? mTitle.toString() : null,
                mCompletedExerciseSessionId, mBlocks);
    }

    public static final class Builder {
        private Metadata mMetadata;
        private int mExerciseType;
        private Instant mStartTime;
        private Instant mEndTime;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;
        private final boolean mHasExplicitTime;
        private CharSequence mNotes;
        private CharSequence mTitle;
        private final List<PlannedExerciseBlock> mBlocks = new ArrayList<PlannedExerciseBlock>();

        public Builder(Metadata metadata, int exerciseType, Instant startTime, Instant endTime) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mExerciseType = exerciseType;
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            mHasExplicitTime = true;
        }

        public Builder(Metadata metadata, int exerciseType, LocalDate date, Duration duration) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mExerciseType = exerciseType;
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(duration, "duration must not be null");
            mStartTime = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
            mEndTime = mStartTime.plus(duration);
            mHasExplicitTime = false;
        }

        public Builder setMetadata(Metadata metadata) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            return this;
        }

        public Builder setExerciseType(int exerciseType) {
            mExerciseType = exerciseType;
            return this;
        }

        public Builder setStartTime(Instant startTime) {
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            return this;
        }

        public Builder setEndTime(Instant endTime) {
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            return this;
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

        public Builder addBlock(PlannedExerciseBlock block) {
            mBlocks.add(Objects.requireNonNull(block, "block must not be null"));
            return this;
        }

        public Builder setBlocks(List<PlannedExerciseBlock> blocks) {
            Objects.requireNonNull(blocks, "blocks must not be null");
            mBlocks.clear();
            mBlocks.addAll(blocks);
            return this;
        }

        public Builder clearBlocks() {
            mBlocks.clear();
            return this;
        }

        public PlannedExerciseSessionRecord build() {
            return new PlannedExerciseSessionRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mExerciseType, mNotes, mTitle,
                    mHasExplicitTime, null, mBlocks);
        }
    }
}
