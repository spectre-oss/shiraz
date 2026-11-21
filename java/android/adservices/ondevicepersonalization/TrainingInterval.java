// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.time.Duration;
import java.util.Objects;

/** Training schedule configuration for {@link FederatedComputeScheduler}. */
public final class TrainingInterval {
    public static final int SCHEDULING_MODE_ONE_TIME = 1;
    public static final int SCHEDULING_MODE_RECURRENT = 2;

    private final int mSchedulingMode;
    private final Duration mMinimumInterval;

    private TrainingInterval(int schedulingMode, Duration minimumInterval) {
        if (schedulingMode != SCHEDULING_MODE_ONE_TIME
                && schedulingMode != SCHEDULING_MODE_RECURRENT) {
            throw new IllegalArgumentException(
                    "Invalid schedulingMode: " + schedulingMode);
        }
        if (schedulingMode == SCHEDULING_MODE_RECURRENT
                && (minimumInterval == null
                        || minimumInterval.isZero()
                        || minimumInterval.isNegative())) {
            throw new IllegalArgumentException(
                    "Recurrent scheduling requires a positive minimumInterval");
        }
        this.mSchedulingMode = schedulingMode;
        this.mMinimumInterval = minimumInterval == null ? Duration.ZERO : minimumInterval;
    }

    public int getSchedulingMode() {
        return mSchedulingMode;
    }

    public Duration getMinimumInterval() {
        return mMinimumInterval;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrainingInterval)) {
            return false;
        }
        TrainingInterval that = (TrainingInterval) o;
        return mSchedulingMode == that.mSchedulingMode
                && Objects.equals(mMinimumInterval, that.mMinimumInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mSchedulingMode), mMinimumInterval);
    }

    public static final class Builder {
        private int mSchedulingMode = SCHEDULING_MODE_ONE_TIME;
        private Duration mMinimumInterval = Duration.ZERO;

        public Builder() {
        }

        public Builder setSchedulingMode(int value) {
            this.mSchedulingMode = value;
            return this;
        }

        public Builder setMinimumInterval(Duration value) {
            this.mMinimumInterval = Objects.requireNonNull(value, "minimumInterval");
            return this;
        }

        public TrainingInterval build() {
            return new TrainingInterval(mSchedulingMode, mMinimumInterval);
        }
    }
}
