// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlannedExerciseBlock {
    private final int mRepetitions;
    private final CharSequence mDescription;
    private final List<PlannedExerciseStep> mSteps;

    PlannedExerciseBlock(int repetitions, CharSequence description, List<PlannedExerciseStep> steps) {
        if (repetitions <= 0) {
            throw new IllegalArgumentException("repetitions must be positive");
        }
        mRepetitions = repetitions;
        mDescription = description;
        mSteps = Collections.unmodifiableList(new ArrayList<PlannedExerciseStep>(steps));
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public List<PlannedExerciseStep> getSteps() {
        return mSteps;
    }

    public int getRepetitions() {
        return mRepetitions;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PlannedExerciseBlock)) return false;
        PlannedExerciseBlock that = (PlannedExerciseBlock) object;
        return mRepetitions == that.mRepetitions
                && Objects.equals(mDescription != null ? mDescription.toString() : null,
                        that.mDescription != null ? that.mDescription.toString() : null)
                && Objects.equals(mSteps, that.mSteps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRepetitions,
                mDescription != null ? mDescription.toString() : null, mSteps);
    }

    public static final class Builder {
        private int mRepetitions;
        private CharSequence mDescription;
        private final List<PlannedExerciseStep> mSteps = new ArrayList<PlannedExerciseStep>();

        public Builder(int repetitions) {
            mRepetitions = repetitions;
        }

        public Builder setRepetitions(int repetitions) {
            mRepetitions = repetitions;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder addStep(PlannedExerciseStep step) {
            mSteps.add(Objects.requireNonNull(step, "step must not be null"));
            return this;
        }

        public Builder setSteps(List<PlannedExerciseStep> steps) {
            Objects.requireNonNull(steps, "steps must not be null");
            mSteps.clear();
            mSteps.addAll(steps);
            return this;
        }

        public Builder clearSteps() {
            mSteps.clear();
            return this;
        }

        public PlannedExerciseBlock build() {
            return new PlannedExerciseBlock(mRepetitions, mDescription, mSteps);
        }
    }
}
