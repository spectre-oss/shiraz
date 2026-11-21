// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import java.time.Duration;
import java.util.Objects;

public abstract class ExerciseCompletionGoal {
    ExerciseCompletionGoal() {
    }

    public static final class ActiveCaloriesBurnedGoal extends ExerciseCompletionGoal {
        private final Energy mActiveCalories;

        public ActiveCaloriesBurnedGoal(Energy activeCalories) {
            mActiveCalories = Objects.requireNonNull(activeCalories, "activeCalories must not be null");
        }

        public Energy getActiveCalories() {
            return mActiveCalories;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ActiveCaloriesBurnedGoal)) return false;
            return Objects.equals(mActiveCalories, ((ActiveCaloriesBurnedGoal) object).mActiveCalories);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mActiveCalories);
        }
    }

    public static final class DistanceGoal extends ExerciseCompletionGoal {
        private final Length mDistance;

        public DistanceGoal(Length distance) {
            mDistance = Objects.requireNonNull(distance, "distance must not be null");
        }

        public Length getDistance() {
            return mDistance;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof DistanceGoal)) return false;
            return Objects.equals(mDistance, ((DistanceGoal) object).mDistance);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mDistance);
        }
    }

    public static final class DistanceWithVariableRestGoal extends ExerciseCompletionGoal {
        private final Length mDistance;
        private final Duration mDuration;

        public DistanceWithVariableRestGoal(Length distance, Duration duration) {
            mDistance = Objects.requireNonNull(distance, "distance must not be null");
            mDuration = Objects.requireNonNull(duration, "duration must not be null");
        }

        public Length getDistance() {
            return mDistance;
        }

        public Duration getDuration() {
            return mDuration;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof DistanceWithVariableRestGoal)) return false;
            DistanceWithVariableRestGoal that = (DistanceWithVariableRestGoal) object;
            return Objects.equals(mDistance, that.mDistance)
                    && Objects.equals(mDuration, that.mDuration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDistance, mDuration);
        }
    }

    public static final class DurationGoal extends ExerciseCompletionGoal {
        private final Duration mDuration;

        public DurationGoal(Duration duration) {
            mDuration = Objects.requireNonNull(duration, "duration must not be null");
        }

        public Duration getDuration() {
            return mDuration;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof DurationGoal)) return false;
            return Objects.equals(mDuration, ((DurationGoal) object).mDuration);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mDuration);
        }
    }

    public static final class RepetitionsGoal extends ExerciseCompletionGoal {
        private final int mRepetitions;

        public RepetitionsGoal(int repetitions) {
            mRepetitions = repetitions;
        }

        public int getRepetitions() {
            return mRepetitions;
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof RepetitionsGoal)
                    && ((RepetitionsGoal) object).mRepetitions == mRepetitions;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(mRepetitions);
        }
    }

    public static final class StepsGoal extends ExerciseCompletionGoal {
        private final int mSteps;

        public StepsGoal(int steps) {
            mSteps = steps;
        }

        public int getSteps() {
            return mSteps;
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof StepsGoal) && ((StepsGoal) object).mSteps == mSteps;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(mSteps);
        }
    }

    public static final class TotalCaloriesBurnedGoal extends ExerciseCompletionGoal {
        private final Energy mTotalCalories;

        public TotalCaloriesBurnedGoal(Energy totalCalories) {
            mTotalCalories = Objects.requireNonNull(totalCalories, "totalCalories must not be null");
        }

        public Energy getTotalCalories() {
            return mTotalCalories;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof TotalCaloriesBurnedGoal)) return false;
            return Objects.equals(mTotalCalories, ((TotalCaloriesBurnedGoal) object).mTotalCalories);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mTotalCalories);
        }
    }

    public static final class UnknownGoal extends ExerciseCompletionGoal {
        public static final UnknownGoal INSTANCE = new UnknownGoal();

        private UnknownGoal() {
        }
    }

    public static final class UnspecifiedGoal extends ExerciseCompletionGoal {
        public static final UnspecifiedGoal INSTANCE = new UnspecifiedGoal();

        private UnspecifiedGoal() {
        }
    }
}
