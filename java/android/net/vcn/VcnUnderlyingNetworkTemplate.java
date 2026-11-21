// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import java.util.Objects;

public abstract class VcnUnderlyingNetworkTemplate {
    public static final int MATCH_ANY = 0;
    public static final int MATCH_REQUIRED = 1;
    public static final int MATCH_FORBIDDEN = 2;

    private final int mMeteredMatchCriteria;
    private final int mMinEntryUpstreamBandwidthKbps;
    private final int mMinExitUpstreamBandwidthKbps;
    private final int mMinEntryDownstreamBandwidthKbps;
    private final int mMinExitDownstreamBandwidthKbps;

    VcnUnderlyingNetworkTemplate(
            int meteredMatchCriteria,
            int minEntryUpstreamBandwidthKbps,
            int minExitUpstreamBandwidthKbps,
            int minEntryDownstreamBandwidthKbps,
            int minExitDownstreamBandwidthKbps) {
        this.mMeteredMatchCriteria = meteredMatchCriteria;
        this.mMinEntryUpstreamBandwidthKbps = minEntryUpstreamBandwidthKbps;
        this.mMinExitUpstreamBandwidthKbps = minExitUpstreamBandwidthKbps;
        this.mMinEntryDownstreamBandwidthKbps = minEntryDownstreamBandwidthKbps;
        this.mMinExitDownstreamBandwidthKbps = minExitDownstreamBandwidthKbps;
    }

    static void validateMatchCriteria(int matchCriteria, String fieldName) {
        if (matchCriteria != MATCH_ANY
                && matchCriteria != MATCH_REQUIRED
                && matchCriteria != MATCH_FORBIDDEN) {
            throw new IllegalArgumentException(
                    "Invalid match criteria for " + fieldName + ": " + matchCriteria);
        }
    }

    static void validateBandwidth(int minEntryKbps, int minExitKbps) {
        if (minEntryKbps < 0 || minExitKbps < 0 || minEntryKbps < minExitKbps) {
            throw new IllegalArgumentException(
                    "Invalid bandwidth thresholds: entry="
                            + minEntryKbps
                            + ", exit="
                            + minExitKbps);
        }
    }

    public int getMetered() {
        return mMeteredMatchCriteria;
    }

    public int getMinEntryUpstreamBandwidthKbps() {
        return mMinEntryUpstreamBandwidthKbps;
    }

    public int getMinExitUpstreamBandwidthKbps() {
        return mMinExitUpstreamBandwidthKbps;
    }

    public int getMinEntryDownstreamBandwidthKbps() {
        return mMinEntryDownstreamBandwidthKbps;
    }

    public int getMinExitDownstreamBandwidthKbps() {
        return mMinExitDownstreamBandwidthKbps;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mMeteredMatchCriteria),
                Integer.valueOf(mMinEntryUpstreamBandwidthKbps),
                Integer.valueOf(mMinExitUpstreamBandwidthKbps),
                Integer.valueOf(mMinEntryDownstreamBandwidthKbps),
                Integer.valueOf(mMinExitDownstreamBandwidthKbps));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VcnUnderlyingNetworkTemplate)) {
            return false;
        }
        VcnUnderlyingNetworkTemplate other = (VcnUnderlyingNetworkTemplate) o;
        return mMeteredMatchCriteria == other.mMeteredMatchCriteria
                && mMinEntryUpstreamBandwidthKbps == other.mMinEntryUpstreamBandwidthKbps
                && mMinExitUpstreamBandwidthKbps == other.mMinExitUpstreamBandwidthKbps
                && mMinEntryDownstreamBandwidthKbps == other.mMinEntryDownstreamBandwidthKbps
                && mMinExitDownstreamBandwidthKbps == other.mMinExitDownstreamBandwidthKbps;
    }
}
