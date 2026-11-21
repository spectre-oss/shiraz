// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlannedExerciseStep {
    public static final int EXERCISE_CATEGORY_UNKNOWN = 0;
    public static final int EXERCISE_CATEGORY_WARMUP = 1;
    public static final int EXERCISE_CATEGORY_REST = 2;
    public static final int EXERCISE_CATEGORY_ACTIVE = 3;
    public static final int EXERCISE_CATEGORY_COOLDOWN = 4;
    public static final int EXERCISE_CATEGORY_RECOVERY = 5;

    private final int mExerciseType;
    private final int mExerciseCategory;
    private final CharSequence mDescription;
    private final ExerciseCompletionGoal mCompletionGoal;
    private final List<ExercisePerformanceGoal> mPerformanceGoals;

    PlannedExerciseStep(int exerciseType, int exerciseCategory, CharSequence description,
            ExerciseCompletionGoal completionGoal, List<ExercisePerformanceGoal> performanceGoals) {
        mExerciseType = exerciseType;
        mExerciseCategory = exerciseCategory;
        mDescription = description;
        mCompletionGoal = Objects.requireNonNull(completionGoal, "completionGoal must not be null");
        mPerformanceGoals = Collections.unmodifiableList(
                new ArrayList<ExercisePerformanceGoal>(performanceGoals));
    }

    public int getExerciseType() {
        return mExerciseType;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public int getExerciseCategory() {
        return mExerciseCategory;
    }

    public ExerciseCompletionGoal getCompletionGoal() {
        return mCompletionGoal;
    }

    public List<ExercisePerformanceGoal> getPerformanceGoals() {
        return mPerformanceGoals;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PlannedExerciseStep)) return false;
        PlannedExerciseStep that = (PlannedExerciseStep) object;
        return mExerciseType == that.mExerciseType
                && mExerciseCategory == that.mExerciseCategory
                && Objects.equals(mDescription != null ? mDescription.toString() : null,
                        that.mDescription != null ? that.mDescription.toString() : null)
                && Objects.equals(mCompletionGoal, that.mCompletionGoal)
                && Objects.equals(mPerformanceGoals, that.mPerformanceGoals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExerciseType, mExerciseCategory,
                mDescription != null ? mDescription.toString() : null,
                mCompletionGoal, mPerformanceGoals);
    }

    public static final class Builder {
        private int mExerciseType;
        private int mExerciseCategory;
        private CharSequence mDescription;
        private ExerciseCompletionGoal mCompletionGoal;
        private final List<ExercisePerformanceGoal> mPerformanceGoals =
                new ArrayList<ExercisePerformanceGoal>();

        public Builder(int exerciseType, int exerciseCategory,
                ExerciseCompletionGoal completionGoal) {
            mExerciseType = exerciseType;
            mExerciseCategory = exerciseCategory;
            mCompletionGoal = Objects.requireNonNull(completionGoal, "completionGoal must not be null");
        }

        public Builder setExerciseType(int exerciseType) {
            mExerciseType = exerciseType;
            return this;
        }

        public Builder setExerciseCategory(int exerciseCategory) {
            mExerciseCategory = exerciseCategory;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setCompletionGoal(ExerciseCompletionGoal completionGoal) {
            mCompletionGoal = Objects.requireNonNull(completionGoal, "completionGoal must not be null");
            return this;
        }

        public Builder addPerformanceGoal(ExercisePerformanceGoal goal) {
            mPerformanceGoals.add(Objects.requireNonNull(goal, "goal must not be null"));
            return this;
        }

        public Builder setPerformanceGoals(List<ExercisePerformanceGoal> goals) {
            Objects.requireNonNull(goals, "goals must not be null");
            mPerformanceGoals.clear();
            mPerformanceGoals.addAll(goals);
            return this;
        }

        public Builder clearPerformanceGoals() {
            mPerformanceGoals.clear();
            return this;
        }

        public PlannedExerciseStep build() {
            return new PlannedExerciseStep(mExerciseType, mExerciseCategory, mDescription,
                    mCompletionGoal, mPerformanceGoals);
        }
    }
}
