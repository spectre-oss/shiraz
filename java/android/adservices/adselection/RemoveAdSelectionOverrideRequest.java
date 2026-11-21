// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import java.util.Objects;

/** Request for removing an override associated with an {@link AdSelectionConfig}. */
public class RemoveAdSelectionOverrideRequest {
    private final AdSelectionConfig mAdSelectionConfig;

    public RemoveAdSelectionOverrideRequest(AdSelectionConfig adSelectionConfig) {
        this.mAdSelectionConfig =
                Objects.requireNonNull(adSelectionConfig, "adSelectionConfig");
    }

    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }
}
