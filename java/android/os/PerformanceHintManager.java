// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.Closeable;
import java.util.Objects;

/** Manages thread performance hint sessions and frame work-duration telemetry. */
public final class PerformanceHintManager {
    private static final long DEFAULT_UPDATE_RATE_NANOS = 16_666_666L;

    public static class Session implements Closeable {
        private int[] tids;
        private long targetDurationNanos;
        private boolean preferPowerEfficiency;
        private boolean closed;

        Session(int[] tids, long initialTargetWorkDurationNanos) {
            this.tids = tids.clone();
            this.targetDurationNanos = initialTargetWorkDurationNanos;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

        public void updateTargetWorkDuration(long targetDurationNanos) {
            if (targetDurationNanos <= 0) {
                throw new IllegalArgumentException(
                        "the hint target duration should be positive.");
            }
            if (!closed) {
                this.targetDurationNanos = targetDurationNanos;
            }
        }

        public void reportActualWorkDuration(long actualDurationNanos) {
            if (actualDurationNanos <= 0) {
                throw new IllegalArgumentException(
                        "the actual duration should be positive.");
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        public void setPreferPowerEfficiency(boolean enabled) {
            this.preferPowerEfficiency = enabled;
        }

        public void setThreads(int[] tids) {
            Objects.requireNonNull(tids, "tids cannot be null");
            if (tids.length == 0) {
                throw new IllegalArgumentException("Thread id list can't be empty.");
            }
            if (!closed) {
                this.tids = tids.clone();
            }
        }

        public void reportActualWorkDuration(WorkDuration workDuration) {
            Objects.requireNonNull(workDuration, "workDuration");
            if (workDuration.getWorkPeriodStartTimestampNanos() <= 0
                    || workDuration.getActualTotalDurationNanos() <= 0
                    || workDuration.getActualCpuDurationNanos() < 0
                    || workDuration.getActualGpuDurationNanos() < 0
                    || (workDuration.getActualCpuDurationNanos()
                                    + workDuration.getActualGpuDurationNanos()
                            <= 0)) {
                throw new IllegalArgumentException("Invalid WorkDuration");
            }
        }
    }

    public PerformanceHintManager() {
    }

    public long getPreferredUpdateRateNanos() {
        return DEFAULT_UPDATE_RATE_NANOS;
    }

    public Session createHintSession(int[] tids, long initialTargetWorkDurationNanos) {
        Objects.requireNonNull(tids, "tids cannot be null");
        if (tids.length == 0) {
            throw new IllegalArgumentException("thread id list can't be empty.");
        }
        if (initialTargetWorkDurationNanos <= 0) {
            throw new IllegalArgumentException("the hint target duration should be positive.");
        }
        return new Session(tids, initialTargetWorkDurationNanos);
    }
}
