// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.adservices.common.AdTechIdentifier;
import java.util.Objects;

/** Request payload for {@link CustomAudienceManager#leaveCustomAudience}. */
public final class LeaveCustomAudienceRequest {
    private final AdTechIdentifier mBuyer;
    private final String mName;

    private LeaveCustomAudienceRequest(AdTechIdentifier buyer, String name) {
        this.mBuyer = Objects.requireNonNull(buyer, "buyer");
        this.mName = Objects.requireNonNull(name, "name");
    }

    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    public String getName() {
        return mName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaveCustomAudienceRequest)) {
            return false;
        }
        LeaveCustomAudienceRequest that = (LeaveCustomAudienceRequest) o;
        return Objects.equals(mBuyer, that.mBuyer) && Objects.equals(mName, that.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBuyer, mName);
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

        public LeaveCustomAudienceRequest build() {
            return new LeaveCustomAudienceRequest(mBuyer, mName);
        }
    }
}
