// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import java.util.Objects;

/** Request payload for {@link AdSelectionManager#updateAdCounterHistogram}. */
public class UpdateAdCounterHistogramRequest {
    private final long mAdSelectionId;
    private final int mAdEventType;
    private final AdTechIdentifier mCallerAdTech;

    private UpdateAdCounterHistogramRequest(
            long adSelectionId, int adEventType, AdTechIdentifier callerAdTech) {
        if (adSelectionId <= 0L) {
            throw new IllegalArgumentException("adSelectionId must be positive");
        }
        if (adEventType == FrequencyCapFilters.AD_EVENT_TYPE_WIN) {
            throw new IllegalArgumentException("Win events cannot be updated manually");
        }
        if (adEventType != FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION
                && adEventType != FrequencyCapFilters.AD_EVENT_TYPE_VIEW
                && adEventType != FrequencyCapFilters.AD_EVENT_TYPE_CLICK) {
            throw new IllegalArgumentException("Invalid adEventType: " + adEventType);
        }
        this.mAdSelectionId = adSelectionId;
        this.mAdEventType = adEventType;
        this.mCallerAdTech = Objects.requireNonNull(callerAdTech, "callerAdTech");
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    public int getAdEventType() {
        return mAdEventType;
    }

    public AdTechIdentifier getCallerAdTech() {
        return mCallerAdTech;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateAdCounterHistogramRequest)) {
            return false;
        }
        UpdateAdCounterHistogramRequest that = (UpdateAdCounterHistogramRequest) o;
        return mAdSelectionId == that.mAdSelectionId
                && mAdEventType == that.mAdEventType
                && Objects.equals(mCallerAdTech, that.mCallerAdTech);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Long.valueOf(mAdSelectionId), Integer.valueOf(mAdEventType), mCallerAdTech);
    }

    @Override
    public String toString() {
        return "UpdateAdCounterHistogramRequest{mAdSelectionId="
                + mAdSelectionId
                + ", mAdEventType="
                + mAdEventType
                + ", mCallerAdTech="
                + mCallerAdTech
                + "}";
    }

    public static final class Builder {
        private long mAdSelectionId;
        private int mAdEventType;
        private AdTechIdentifier mCallerAdTech;

        public Builder(long adSelectionId, int adEventType, AdTechIdentifier callerAdTech) {
            this.mAdSelectionId = adSelectionId;
            this.mAdEventType = adEventType;
            this.mCallerAdTech = Objects.requireNonNull(callerAdTech, "callerAdTech");
        }

        public Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        public Builder setAdEventType(int adEventType) {
            this.mAdEventType = adEventType;
            return this;
        }

        public Builder setCallerAdTech(AdTechIdentifier callerAdTech) {
            this.mCallerAdTech = Objects.requireNonNull(callerAdTech, "callerAdTech");
            return this;
        }

        public UpdateAdCounterHistogramRequest build() {
            return new UpdateAdCounterHistogramRequest(
                    mAdSelectionId, mAdEventType, mCallerAdTech);
        }
    }
}
