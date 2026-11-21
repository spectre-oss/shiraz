// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.BloodGlucose;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BloodGlucoseRecord extends InstantRecord {
    private final int mSpecimenSource;
    private final BloodGlucose mLevel;
    private final int mRelationToMeal;
    private final int mMealType;

    BloodGlucoseRecord(Metadata metadata, Instant time, ZoneOffset zoneOffset,
            int specimenSource, BloodGlucose level, int relationToMeal, int mealType) {
        super(metadata, time, zoneOffset);
        mSpecimenSource = specimenSource;
        mLevel = Objects.requireNonNull(level, "level must not be null");
        mRelationToMeal = relationToMeal;
        mMealType = mealType;
    }

    public int getSpecimenSource() {
        return mSpecimenSource;
    }

    public BloodGlucose getLevel() {
        return mLevel;
    }

    public int getRelationToMeal() {
        return mRelationToMeal;
    }

    public int getMealType() {
        return mMealType;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        BloodGlucoseRecord that = (BloodGlucoseRecord) object;
        return mSpecimenSource == that.mSpecimenSource
                && mRelationToMeal == that.mRelationToMeal
                && mMealType == that.mMealType
                && Objects.equals(mLevel, that.mLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSpecimenSource, mLevel, mRelationToMeal, mMealType);
    }

    public static final class RelationToMealType {
        public static final int RELATION_TO_MEAL_UNKNOWN = 0;
        public static final int RELATION_TO_MEAL_GENERAL = 1;
        public static final int RELATION_TO_MEAL_FASTING = 2;
        public static final int RELATION_TO_MEAL_BEFORE_MEAL = 3;
        public static final int RELATION_TO_MEAL_AFTER_MEAL = 4;

        private RelationToMealType() {
        }
    }

    public static final class SpecimenSource {
        public static final int SPECIMEN_SOURCE_UNKNOWN = 0;
        public static final int SPECIMEN_SOURCE_INTERSTITIAL_FLUID = 1;
        public static final int SPECIMEN_SOURCE_CAPILLARY_BLOOD = 2;
        public static final int SPECIMEN_SOURCE_PLASMA = 3;
        public static final int SPECIMEN_SOURCE_SERUM = 4;
        public static final int SPECIMEN_SOURCE_TEARS = 5;
        public static final int SPECIMEN_SOURCE_WHOLE_BLOOD = 6;

        private SpecimenSource() {
        }
    }

    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private final int mSpecimenSource;
        private final BloodGlucose mLevel;
        private final int mRelationToMeal;
        private final int mMealType;
        private ZoneOffset mZoneOffset;

        public Builder(Metadata metadata, Instant time, int specimenSource,
                BloodGlucose level, int relationToMeal, int mealType) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mTime = Objects.requireNonNull(time, "time must not be null");
            mSpecimenSource = specimenSource;
            mLevel = Objects.requireNonNull(level, "level must not be null");
            mRelationToMeal = relationToMeal;
            mMealType = mealType;
        }

        public Builder setZoneOffset(ZoneOffset zoneOffset) {
            mZoneOffset = Objects.requireNonNull(zoneOffset, "zoneOffset must not be null");
            return this;
        }

        public Builder clearZoneOffset() {
            mZoneOffset = null;
            return this;
        }

        public BloodGlucoseRecord build() {
            return new BloodGlucoseRecord(mMetadata, mTime, mZoneOffset,
                    mSpecimenSource, mLevel, mRelationToMeal, mMealType);
        }
    }
}
