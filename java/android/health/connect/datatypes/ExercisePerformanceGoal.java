// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Velocity;
import java.util.Objects;

public abstract class ExercisePerformanceGoal {
    ExercisePerformanceGoal() {
    }

    public static final class AmrapGoal extends ExercisePerformanceGoal {
        public static final AmrapGoal INSTANCE = new AmrapGoal();

        private AmrapGoal() {
        }
    }

    public static final class CadenceGoal extends ExercisePerformanceGoal {
        private final double mMinRpm;
        private final double mMaxRpm;

        public CadenceGoal(double minRpm, double maxRpm) {
            mMinRpm = minRpm;
            mMaxRpm = maxRpm;
        }

        public double getMinRpm() {
            return mMinRpm;
        }

        public double getMaxRpm() {
            return mMaxRpm;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CadenceGoal)) return false;
            CadenceGoal that = (CadenceGoal) object;
            return Double.compare(mMinRpm, that.mMinRpm) == 0
                    && Double.compare(mMaxRpm, that.mMaxRpm) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMinRpm, mMaxRpm);
        }
    }

    public static final class HeartRateGoal extends ExercisePerformanceGoal {
        private final int mMinBpm;
        private final int mMaxBpm;

        public HeartRateGoal(int minBpm, int maxBpm) {
            mMinBpm = minBpm;
            mMaxBpm = maxBpm;
        }

        public int getMinBpm() {
            return mMinBpm;
        }

        public int getMaxBpm() {
            return mMaxBpm;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof HeartRateGoal)) return false;
            HeartRateGoal that = (HeartRateGoal) object;
            return mMinBpm == that.mMinBpm && mMaxBpm == that.mMaxBpm;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMinBpm, mMaxBpm);
        }
    }

    public static final class PowerGoal extends ExercisePerformanceGoal {
        private final Power mMinPower;
        private final Power mMaxPower;

        public PowerGoal(Power minPower, Power maxPower) {
            mMinPower = Objects.requireNonNull(minPower, "minPower must not be null");
            mMaxPower = Objects.requireNonNull(maxPower, "maxPower must not be null");
        }

        public Power getMinPower() {
            return mMinPower;
        }

        public Power getMaxPower() {
            return mMaxPower;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof PowerGoal)) return false;
            PowerGoal that = (PowerGoal) object;
            return Objects.equals(mMinPower, that.mMinPower)
                    && Objects.equals(mMaxPower, that.mMaxPower);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMinPower, mMaxPower);
        }
    }

    public static final class RateOfPerceivedExertionGoal extends ExercisePerformanceGoal {
        private final int mRpe;

        public RateOfPerceivedExertionGoal(int rpe) {
            mRpe = rpe;
        }

        public int getRpe() {
            return mRpe;
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof RateOfPerceivedExertionGoal)
                    && ((RateOfPerceivedExertionGoal) object).mRpe == mRpe;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(mRpe);
        }
    }

    public static final class SpeedGoal extends ExercisePerformanceGoal {
        private final Velocity mMinSpeed;
        private final Velocity mMaxSpeed;

        public SpeedGoal(Velocity minSpeed, Velocity maxSpeed) {
            mMinSpeed = Objects.requireNonNull(minSpeed, "minSpeed must not be null");
            mMaxSpeed = Objects.requireNonNull(maxSpeed, "maxSpeed must not be null");
        }

        public Velocity getMinSpeed() {
            return mMinSpeed;
        }

        public Velocity getMaxSpeed() {
            return mMaxSpeed;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof SpeedGoal)) return false;
            SpeedGoal that = (SpeedGoal) object;
            return Objects.equals(mMinSpeed, that.mMinSpeed)
                    && Objects.equals(mMaxSpeed, that.mMaxSpeed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMinSpeed, mMaxSpeed);
        }
    }

    public static final class UnknownGoal extends ExercisePerformanceGoal {
        public static final UnknownGoal INSTANCE = new UnknownGoal();

        private UnknownGoal() {
        }
    }

    public static final class WeightGoal extends ExercisePerformanceGoal {
        private final Mass mMass;

        public WeightGoal(Mass mass) {
            mMass = Objects.requireNonNull(mass, "mass must not be null");
        }

        public Mass getMass() {
            return mMass;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof WeightGoal)) return false;
            return Objects.equals(mMass, ((WeightGoal) object).mMass);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mMass);
        }
    }
}
