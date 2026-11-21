// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

/** Encrypted payload returned by {@link AdSelectionManager#getAdSelectionData}. */
public final class GetAdSelectionDataOutcome {
    private final long mAdSelectionId;
    private final byte[] mAdSelectionData;

    GetAdSelectionDataOutcome(long adSelectionId, byte[] adSelectionData) {
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionData = adSelectionData == null ? null : adSelectionData.clone();
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    @Deprecated
    public long getAdSelectionDataId() {
        return mAdSelectionId;
    }

    public byte[] getAdSelectionData() {
        return mAdSelectionData == null ? null : mAdSelectionData.clone();
    }
}
