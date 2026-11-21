// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdTechIdentifier;
import java.util.Objects;

/** Request for removing a test override on a custom audience. */
public class RemoveCustomAudienceOverrideRequest {
    private final AdTechIdentifier mBuyer;
    private final String mName;

    public RemoveCustomAudienceOverrideRequest(AdTechIdentifier buyer, String name) {
        this.mBuyer = Objects.requireNonNull(buyer, "buyer");
        this.mName = Objects.requireNonNull(name, "name");
    }

    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    public String getName() {
        return mName;
    }

    public static final class Builder {
        private AdTechIdentifier mBuyer;
        private String mName;

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

        public RemoveCustomAudienceOverrideRequest build() {
            return new RemoveCustomAudienceOverrideRequest(mBuyer, mName);
        }
    }
}
