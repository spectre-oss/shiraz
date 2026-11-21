// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import java.util.Objects;

/** Request for overriding decision JS and scoring signals for an {@link AdSelectionConfig}. */
public class AddAdSelectionOverrideRequest {
    private final AdSelectionConfig mAdSelectionConfig;
    private final String mDecisionLogicJs;
    private final AdSelectionSignals mTrustedScoringSignals;
    private final PerBuyerDecisionLogic mPerBuyerDecisionLogic;

    public AddAdSelectionOverrideRequest(
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJs,
            AdSelectionSignals trustedScoringSignals,
            PerBuyerDecisionLogic perBuyerDecisionLogic) {
        this.mAdSelectionConfig =
                Objects.requireNonNull(adSelectionConfig, "adSelectionConfig");
        this.mDecisionLogicJs = Objects.requireNonNull(decisionLogicJs, "decisionLogicJs");
        this.mTrustedScoringSignals =
                Objects.requireNonNull(trustedScoringSignals, "trustedScoringSignals");
        this.mPerBuyerDecisionLogic =
                Objects.requireNonNull(perBuyerDecisionLogic, "perBuyerDecisionLogic");
    }

    public AddAdSelectionOverrideRequest(
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJs,
            AdSelectionSignals trustedScoringSignals) {
        this(
                adSelectionConfig,
                decisionLogicJs,
                trustedScoringSignals,
                PerBuyerDecisionLogic.EMPTY);
    }

    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    public String getDecisionLogicJs() {
        return mDecisionLogicJs;
    }

    public AdSelectionSignals getTrustedScoringSignals() {
        return mTrustedScoringSignals;
    }

    public PerBuyerDecisionLogic getPerBuyerDecisionLogic() {
        return mPerBuyerDecisionLogic;
    }
}
