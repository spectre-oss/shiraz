// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import java.util.Objects;

/** Request for overriding outcome selection JS and trusted signals in tests. */
public class AddAdSelectionFromOutcomesOverrideRequest {
    private final AdSelectionFromOutcomesConfig mAdSelectionFromOutcomesConfig;
    private final String mOutcomeSelectionLogicJs;
    private final AdSelectionSignals mOutcomeSelectionTrustedSignals;

    public AddAdSelectionFromOutcomesOverrideRequest(
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            String outcomeSelectionLogicJs,
            AdSelectionSignals outcomeSelectionTrustedSignals) {
        this.mAdSelectionFromOutcomesConfig =
                Objects.requireNonNull(
                        adSelectionFromOutcomesConfig, "adSelectionFromOutcomesConfig");
        this.mOutcomeSelectionLogicJs =
                Objects.requireNonNull(outcomeSelectionLogicJs, "outcomeSelectionLogicJs");
        this.mOutcomeSelectionTrustedSignals =
                Objects.requireNonNull(
                        outcomeSelectionTrustedSignals, "outcomeSelectionTrustedSignals");
    }

    public AdSelectionFromOutcomesConfig getAdSelectionFromOutcomesConfig() {
        return mAdSelectionFromOutcomesConfig;
    }

    public String getOutcomeSelectionLogicJs() {
        return mOutcomeSelectionLogicJs;
    }

    public AdSelectionSignals getOutcomeSelectionTrustedSignals() {
        return mOutcomeSelectionTrustedSignals;
    }
}
