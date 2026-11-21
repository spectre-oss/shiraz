// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import java.util.Objects;

/** Request for overriding remote bidding JS and trusted signals for a custom audience in tests. */
public class AddCustomAudienceOverrideRequest {
    private final AdTechIdentifier mBuyer;
    private final String mName;
    private final String mBiddingLogicJs;
    private final long mBiddingLogicJsVersion;
    private final AdSelectionSignals mTrustedBiddingSignals;

    public AddCustomAudienceOverrideRequest(
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            AdSelectionSignals trustedBiddingSignals) {
        this(buyer, name, biddingLogicJs, 0L, trustedBiddingSignals);
    }

    private AddCustomAudienceOverrideRequest(
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            long biddingLogicJsVersion,
            AdSelectionSignals trustedBiddingSignals) {
        this.mBuyer = Objects.requireNonNull(buyer, "buyer");
        this.mName = Objects.requireNonNull(name, "name");
        this.mBiddingLogicJs = Objects.requireNonNull(biddingLogicJs, "biddingLogicJs");
        this.mBiddingLogicJsVersion = biddingLogicJsVersion;
        this.mTrustedBiddingSignals =
                Objects.requireNonNull(trustedBiddingSignals, "trustedBiddingSignals");
    }

    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    public String getName() {
        return mName;
    }

    public String getBiddingLogicJs() {
        return mBiddingLogicJs;
    }

    public long getBiddingLogicJsVersion() {
        return mBiddingLogicJsVersion;
    }

    public AdSelectionSignals getTrustedBiddingSignals() {
        return mTrustedBiddingSignals;
    }

    public static final class Builder {
        private AdTechIdentifier mBuyer;
        private String mName;
        private String mBiddingLogicJs = "";
        private long mBiddingLogicJsVersion;
        private AdSelectionSignals mTrustedBiddingSignals = AdSelectionSignals.EMPTY;

        public Builder() {
        }

        public Builder setBuyer(AdTechIdentifier buyer) {
            this.mBuyer = Objects.requireNonNull(buyer, "buyer");
            return this;
        }

        public Builder setName(String name) {
            this.mName = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder setTrustedBiddingSignals(AdSelectionSignals trustedBiddingSignals) {
            this.mTrustedBiddingSignals =
                    Objects.requireNonNull(trustedBiddingSignals, "trustedBiddingSignals");
            return this;
        }

        public Builder setBiddingLogicJs(String biddingLogicJs) {
            this.mBiddingLogicJs = Objects.requireNonNull(biddingLogicJs, "biddingLogicJs");
            return this;
        }

        public Builder setBiddingLogicJsVersion(long biddingLogicJsVersion) {
            this.mBiddingLogicJsVersion = biddingLogicJsVersion;
            return this;
        }

        public AddCustomAudienceOverrideRequest build() {
            return new AddCustomAudienceOverrideRequest(
                    mBuyer,
                    mName,
                    mBiddingLogicJs,
                    mBiddingLogicJsVersion,
                    mTrustedBiddingSignals);
        }
    }
}
