// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Arrays;
import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onTrainingExamples}. */
public final class TrainingExamplesInput {
    private final String mPopulationName;
    private final String mTaskName;
    private final byte[] mResumptionToken;

    TrainingExamplesInput(
            String populationName, String taskName, byte[] resumptionToken) {
        this.mPopulationName = Objects.requireNonNull(populationName, "populationName");
        this.mTaskName = Objects.requireNonNull(taskName, "taskName");
        this.mResumptionToken =
                resumptionToken == null ? null : resumptionToken.clone();
    }

    public String getPopulationName() {
        return mPopulationName;
    }

    public String getTaskName() {
        return mTaskName;
    }

    public byte[] getResumptionToken() {
        return mResumptionToken == null ? null : mResumptionToken.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrainingExamplesInput)) {
            return false;
        }
        TrainingExamplesInput that = (TrainingExamplesInput) o;
        return Objects.equals(mPopulationName, that.mPopulationName)
                && Objects.equals(mTaskName, that.mTaskName)
                && Arrays.equals(mResumptionToken, that.mResumptionToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mPopulationName,
                mTaskName,
                Integer.valueOf(Arrays.hashCode(mResumptionToken)));
    }
}
