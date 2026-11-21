// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onTrainingExamples}. */
public final class TrainingExamplesOutput {
    private final List<TrainingExampleRecord> mTrainingExampleRecords;

    private TrainingExamplesOutput(List<TrainingExampleRecord> trainingExampleRecords) {
        List<TrainingExampleRecord> copy = new ArrayList<TrainingExampleRecord>();
        if (trainingExampleRecords != null) {
            for (TrainingExampleRecord r : trainingExampleRecords) {
                copy.add(Objects.requireNonNull(r, "trainingExampleRecord"));
            }
        }
        this.mTrainingExampleRecords = Collections.unmodifiableList(copy);
    }

    public List<TrainingExampleRecord> getTrainingExampleRecords() {
        return mTrainingExampleRecords;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrainingExamplesOutput)) {
            return false;
        }
        TrainingExamplesOutput that = (TrainingExamplesOutput) o;
        return Objects.equals(mTrainingExampleRecords, that.mTrainingExampleRecords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTrainingExampleRecords);
    }

    public static final class Builder {
        private List<TrainingExampleRecord> mTrainingExampleRecords =
                new ArrayList<TrainingExampleRecord>();

        public Builder() {
        }

        public Builder setTrainingExampleRecords(
                List<TrainingExampleRecord> trainingExampleRecords) {
            Objects.requireNonNull(
                    trainingExampleRecords, "trainingExampleRecords");
            this.mTrainingExampleRecords =
                    new ArrayList<TrainingExampleRecord>(trainingExampleRecords);
            return this;
        }

        public Builder addTrainingExampleRecord(TrainingExampleRecord value) {
            this.mTrainingExampleRecords.add(
                    Objects.requireNonNull(value, "trainingExampleRecord"));
            return this;
        }

        public TrainingExamplesOutput build() {
            return new TrainingExamplesOutput(mTrainingExampleRecords);
        }
    }
}
