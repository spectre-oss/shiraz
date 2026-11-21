// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SleepSessionRecord extends IntervalRecord {
    public static final AggregationType<Long> SLEEP_DURATION_TOTAL =
            new AggregationType<Long>(SleepSessionRecord.class, "sleep_duration_ms",
                    AggregationType.OP_SUM, AggregationType.KIND_LONG);

    private final CharSequence mNotes;
    private final CharSequence mTitle;
    private final List<Stage> mStages;

    SleepSessionRecord(Metadata metadata, Instant startTime, ZoneOffset startZoneOffset,
            Instant endTime, ZoneOffset endZoneOffset, CharSequence notes, CharSequence title,
            List<Stage> stages) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        mNotes = notes;
        mTitle = title;
        mStages = Collections.unmodifiableList(new ArrayList<Stage>(stages));
    }

    public CharSequence getNotes() {
        return mNotes;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public List<Stage> getStages() {
        return mStages;
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator accumulator) {
        if ("sleep_duration_ms".equals(metric)) {
            accumulator.addLong(Duration.between(getStartTime(), getEndTime()).toMillis());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        SleepSessionRecord that = (SleepSessionRecord) object;
        return Objects.equals(mNotes != null ? mNotes.toString() : null,
                        that.mNotes != null ? that.mNotes.toString() : null)
                && Objects.equals(mTitle != null ? mTitle.toString() : null,
                        that.mTitle != null ? that.mTitle.toString() : null)
                && Objects.equals(mStages, that.mStages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                mNotes != null ? mNotes.toString() : null,
                mTitle != null ? mTitle.toString() : null, mStages);
    }

    public static class Stage {
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final int mType;

        public Stage(Instant startTime, Instant endTime, int stageType) {
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
            if (!endTime.isAfter(startTime)) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            mType = stageType;
        }

        public Instant getStartTime() {
            return mStartTime;
        }

        public Instant getEndTime() {
            return mEndTime;
        }

        public int getType() {
            return mType;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Stage)) return false;
            Stage that = (Stage) object;
            return mType == that.mType
                    && Objects.equals(mStartTime, that.mStartTime)
                    && Objects.equals(mEndTime, that.mEndTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mStartTime, mEndTime, mType);
        }
    }

    public static final class StageType {
        public static final int STAGE_TYPE_UNKNOWN = 0;
        public static final int STAGE_TYPE_AWAKE = 1;
        public static final int STAGE_TYPE_SLEEPING = 2;
        public static final int STAGE_TYPE_AWAKE_OUT_OF_BED = 3;
        public static final int STAGE_TYPE_SLEEPING_LIGHT = 4;
        public static final int STAGE_TYPE_SLEEPING_DEEP = 5;
        public static final int STAGE_TYPE_SLEEPING_REM = 6;
        public static final int STAGE_TYPE_AWAKE_IN_BED = 7;

        private StageType() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;
        private CharSequence mNotes;
        private CharSequence mTitle;
        private List<Stage> mStages = Collections.emptyList();

        public Builder(Metadata metadata, Instant startTime, Instant endTime) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
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

        public Builder setStages(List<Stage> stages) {
            mStages = new ArrayList<Stage>(Objects.requireNonNull(stages, "stages must not be null"));
            return this;
        }

        public SleepSessionRecord build() {
            return new SleepSessionRecord(mMetadata, mStartTime, mStartZoneOffset,
                    mEndTime, mEndZoneOffset, mNotes, mTitle, mStages);
        }
    }
}
