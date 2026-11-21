// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;

/** Request parameters for {@link AdSelectionManager#persistAdSelectionResult}. */
public final class PersistAdSelectionResultRequest {
    private final long mAdSelectionId;
    private final AdTechIdentifier mSeller;
    private final byte[] mAdSelectionResult;

    private PersistAdSelectionResultRequest(
            long adSelectionId, AdTechIdentifier seller, byte[] adSelectionResult) {
        this.mAdSelectionId = adSelectionId;
        this.mSeller = seller;
        this.mAdSelectionResult = adSelectionResult == null ? null : adSelectionResult.clone();
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    @Deprecated
    public long getAdSelectionDataId() {
        return mAdSelectionId;
    }

    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    public byte[] getAdSelectionResult() {
        return mAdSelectionResult == null ? null : mAdSelectionResult.clone();
    }

    public static final class Builder {
        private long mAdSelectionId;
        private AdTechIdentifier mSeller;
        private byte[] mAdSelectionResult;

        public Builder() {
        }

        public Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        @Deprecated
        public Builder setAdSelectionDataId(long adSelectionDataId) {
            this.mAdSelectionId = adSelectionDataId;
            return this;
        }

        public Builder setSeller(AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        public Builder setAdSelectionResult(byte[] adSelectionResult) {
            this.mAdSelectionResult =
                    adSelectionResult == null ? null : adSelectionResult.clone();
            return this;
        }

        public PersistAdSelectionResultRequest build() {
            return new PersistAdSelectionResultRequest(
                    mAdSelectionId, mSeller, mAdSelectionResult);
        }
    }
}
