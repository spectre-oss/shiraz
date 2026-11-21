// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import java.util.Objects;

/** Request payload for {@link CustomAudienceManager#joinCustomAudience}. */
public class JoinCustomAudienceRequest {
    private final CustomAudience mCustomAudience;

    private JoinCustomAudienceRequest(CustomAudience customAudience) {
        this.mCustomAudience = Objects.requireNonNull(customAudience, "customAudience");
    }

    public CustomAudience getCustomAudience() {
        return mCustomAudience;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JoinCustomAudienceRequest)) {
            return false;
        }
        JoinCustomAudienceRequest that = (JoinCustomAudienceRequest) o;
        return Objects.equals(mCustomAudience, that.mCustomAudience);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCustomAudience);
    }

    public static final class Builder {
        private CustomAudience mCustomAudience;

        public Builder() {
        }

        public Builder setCustomAudience(CustomAudience customAudience) {
            this.mCustomAudience = Objects.requireNonNull(customAudience, "customAudience");
            return this;
        }

        public JoinCustomAudienceRequest build() {
            return new JoinCustomAudienceRequest(mCustomAudience);
        }
    }
}
