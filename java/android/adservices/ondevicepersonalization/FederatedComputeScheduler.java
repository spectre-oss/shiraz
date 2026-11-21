// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Scheduler for federated learning and analytics tasks inside an {@link IsolatedService}. */
public class FederatedComputeScheduler {
    private final Map<String, Params> mScheduled = new ConcurrentHashMap<String, Params>();

    FederatedComputeScheduler() {
    }

    public void schedule(Params params, FederatedComputeInput input) {
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(input, "input");
        mScheduled.put(input.getPopulationName(), params);
    }

    public void cancel(FederatedComputeInput input) {
        Objects.requireNonNull(input, "input");
        mScheduled.remove(input.getPopulationName());
    }

    public static class Params {
        private final TrainingInterval mTrainingInterval;

        public Params(TrainingInterval trainingInterval) {
            this.mTrainingInterval =
                    Objects.requireNonNull(trainingInterval, "trainingInterval");
        }

        public TrainingInterval getTrainingInterval() {
            return mTrainingInterval;
        }
    }
}
