// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Objects;

/** Measured CPU, GPU, and total duration for a single work unit in {@link PerformanceHintManager}. */
public final class WorkDuration {
    private long workPeriodStartTimestampNanos;
    private long actualTotalDurationNanos;
    private long actualCpuDurationNanos;
    private long actualGpuDurationNanos;

    public WorkDuration() {
    }

    public void setActualTotalDurationNanos(long actualTotalDurationNanos) {
        if (actualTotalDurationNanos <= 0) {
            throw new IllegalArgumentException(
                    "the actual total duration should be greater than zero.");
        }
        this.actualTotalDurationNanos = actualTotalDurationNanos;
    }

    public void setWorkPeriodStartTimestampNanos(long workPeriodStartTimestampNanos) {
        if (workPeriodStartTimestampNanos <= 0) {
            throw new IllegalArgumentException(
                    "the work period start timestamp should be greater than zero.");
        }
        this.workPeriodStartTimestampNanos = workPeriodStartTimestampNanos;
    }

    public void setActualCpuDurationNanos(long actualCpuDurationNanos) {
        if (actualCpuDurationNanos < 0) {
            throw new IllegalArgumentException(
                    "the actual CPU duration should be greater than or equal to zero.");
        }
        this.actualCpuDurationNanos = actualCpuDurationNanos;
    }

    public void setActualGpuDurationNanos(long actualGpuDurationNanos) {
        if (actualGpuDurationNanos < 0) {
            throw new IllegalArgumentException(
                    "the actual GPU duration should be greater than or equal to zero.");
        }
        this.actualGpuDurationNanos = actualGpuDurationNanos;
    }

    public long getActualTotalDurationNanos() {
        return actualTotalDurationNanos;
    }

    public long getWorkPeriodStartTimestampNanos() {
        return workPeriodStartTimestampNanos;
    }

    public long getActualCpuDurationNanos() {
        return actualCpuDurationNanos;
    }

    public long getActualGpuDurationNanos() {
        return actualGpuDurationNanos;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof WorkDuration)) {
            return false;
        }
        WorkDuration other = (WorkDuration) obj;
        return other.workPeriodStartTimestampNanos == workPeriodStartTimestampNanos
                && other.actualTotalDurationNanos == actualTotalDurationNanos
                && other.actualCpuDurationNanos == actualCpuDurationNanos
                && other.actualGpuDurationNanos == actualGpuDurationNanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Long.valueOf(workPeriodStartTimestampNanos),
                Long.valueOf(actualTotalDurationNanos),
                Long.valueOf(actualCpuDurationNanos),
                Long.valueOf(actualGpuDurationNanos));
    }
}
