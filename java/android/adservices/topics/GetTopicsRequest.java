// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.topics;

import java.util.Objects;

/** Request parameters for {@link TopicsManager#getTopics}. */
public final class GetTopicsRequest {
    private final String mAdsSdkName;
    private final boolean mShouldRecordObservation;

    private GetTopicsRequest(String adsSdkName, boolean shouldRecordObservation) {
        this.mAdsSdkName = adsSdkName == null ? "" : adsSdkName;
        this.mShouldRecordObservation = shouldRecordObservation;
    }

    public String getAdsSdkName() {
        return mAdsSdkName;
    }

    public boolean shouldRecordObservation() {
        return mShouldRecordObservation;
    }

    public static final class Builder {
        private String mAdsSdkName = "";
        private boolean mShouldRecordObservation = true;

        public Builder() {
        }

        public Builder setAdsSdkName(String adsSdkName) {
            Objects.requireNonNull(adsSdkName, "adsSdkName");
            this.mAdsSdkName = adsSdkName;
            return this;
        }

        public Builder setShouldRecordObservation(boolean shouldRecordObservation) {
            this.mShouldRecordObservation = shouldRecordObservation;
            return this;
        }

        public GetTopicsRequest build() {
            return new GetTopicsRequest(mAdsSdkName, mShouldRecordObservation);
        }
    }
}
