// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import java.util.Objects;

/** Request payload for {@link AdSelectionManager#reportImpression}. */
public class ReportImpressionRequest {
    private final long mAdSelectionId;
    private final AdSelectionConfig mAdSelectionConfig;

    public ReportImpressionRequest(long adSelectionId, AdSelectionConfig adSelectionConfig) {
        if (adSelectionId <= 0L) {
            throw new IllegalArgumentException("adSelectionId must be positive");
        }
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionConfig =
                Objects.requireNonNull(adSelectionConfig, "adSelectionConfig");
    }

    public ReportImpressionRequest(long adSelectionId) {
        if (adSelectionId <= 0L) {
            throw new IllegalArgumentException("adSelectionId must be positive");
        }
        this.mAdSelectionId = adSelectionId;
        this.mAdSelectionConfig = null;
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }
}
