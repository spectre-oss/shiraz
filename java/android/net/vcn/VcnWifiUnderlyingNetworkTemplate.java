// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class VcnWifiUnderlyingNetworkTemplate extends VcnUnderlyingNetworkTemplate {
    private final Set<String> mSsids;

    VcnWifiUnderlyingNetworkTemplate(
            int meteredMatchCriteria,
            int minEntryUpstreamKbps,
            int minExitUpstreamKbps,
            int minEntryDownstreamKbps,
            int minExitDownstreamKbps,
            Set<String> ssids) {
        super(
                meteredMatchCriteria,
                minEntryUpstreamKbps,
                minExitUpstreamKbps,
                minEntryDownstreamKbps,
                minExitDownstreamKbps);
        this.mSsids = Collections.unmodifiableSet(new LinkedHashSet<String>(ssids));
    }

    public Set<String> getSsids() {
        return mSsids;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(super.hashCode()), mSsids);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VcnWifiUnderlyingNetworkTemplate) || !super.equals(o)) {
            return false;
        }
        return Objects.equals(mSsids, ((VcnWifiUnderlyingNetworkTemplate) o).mSsids);
    }

    public static final class Builder {
        private int mMeteredMatchCriteria = MATCH_ANY;
        private int mMinEntryUpstreamKbps = 0;
        private int mMinExitUpstreamKbps = 0;
        private int mMinEntryDownstreamKbps = 0;
        private int mMinExitDownstreamKbps = 0;
        private final Set<String> mSsids = new LinkedHashSet<String>();

        public Builder() {}

        public Builder setMetered(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setMetered");
            this.mMeteredMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setSsids(Set<String> ssids) {
            Objects.requireNonNull(ssids, "ssids is null");
            for (String s : ssids) {
                Objects.requireNonNull(s, "ssid element is null");
            }
            mSsids.clear();
            mSsids.addAll(ssids);
            return this;
        }

        public Builder setMinUpstreamBandwidthKbps(
                int minEntryUpstreamBandwidthKbps, int minExitUpstreamBandwidthKbps) {
            validateBandwidth(minEntryUpstreamBandwidthKbps, minExitUpstreamBandwidthKbps);
            this.mMinEntryUpstreamKbps = minEntryUpstreamBandwidthKbps;
            this.mMinExitUpstreamKbps = minExitUpstreamBandwidthKbps;
            return this;
        }

        public Builder setMinDownstreamBandwidthKbps(
                int minEntryDownstreamBandwidthKbps, int minExitDownstreamBandwidthKbps) {
            validateBandwidth(minEntryDownstreamBandwidthKbps, minExitDownstreamBandwidthKbps);
            this.mMinEntryDownstreamKbps = minEntryDownstreamBandwidthKbps;
            this.mMinExitDownstreamKbps = minExitDownstreamBandwidthKbps;
            return this;
        }

        public VcnWifiUnderlyingNetworkTemplate build() {
            return new VcnWifiUnderlyingNetworkTemplate(
                    mMeteredMatchCriteria,
                    mMinEntryUpstreamKbps,
                    mMinExitUpstreamKbps,
                    mMinEntryDownstreamKbps,
                    mMinExitDownstreamKbps,
                    mSsids);
        }
    }
}
