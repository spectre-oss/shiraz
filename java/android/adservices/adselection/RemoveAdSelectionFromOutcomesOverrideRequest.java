// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import java.util.Objects;

/** Request for removing an override associated with an {@link AdSelectionFromOutcomesConfig}. */
public class RemoveAdSelectionFromOutcomesOverrideRequest {
    private final AdSelectionFromOutcomesConfig mAdSelectionFromOutcomesConfig;

    public RemoveAdSelectionFromOutcomesOverrideRequest(
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig) {
        this.mAdSelectionFromOutcomesConfig =
                Objects.requireNonNull(
                        adSelectionFromOutcomesConfig, "adSelectionFromOutcomesConfig");
    }

    public AdSelectionFromOutcomesConfig getAdSelectionFromOutcomesConfig() {
        return mAdSelectionFromOutcomesConfig;
    }
}
