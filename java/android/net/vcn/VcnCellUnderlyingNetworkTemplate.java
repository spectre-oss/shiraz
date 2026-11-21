// SPDX-License-Identifier: GPL-3.0-only
package android.net.vcn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class VcnCellUnderlyingNetworkTemplate extends VcnUnderlyingNetworkTemplate {
    private final Set<String> mAllowedOperatorPlmnIds;
    private final Set<Integer> mAllowedSpecificCarrierIds;
    private final int mRoamingMatchCriteria;
    private final int mOpportunisticMatchCriteria;
    private final int mCbsMatchCriteria;
    private final int mDunMatchCriteria;
    private final int mImsMatchCriteria;
    private final int mInternetMatchCriteria;
    private final int mMmsMatchCriteria;
    private final int mRcsMatchCriteria;

    VcnCellUnderlyingNetworkTemplate(Builder b) {
        super(
                b.mMeteredMatchCriteria,
                b.mMinEntryUpstreamKbps,
                b.mMinExitUpstreamKbps,
                b.mMinEntryDownstreamKbps,
                b.mMinExitDownstreamKbps);
        this.mAllowedOperatorPlmnIds =
                Collections.unmodifiableSet(new LinkedHashSet<String>(b.mOperatorPlmnIds));
        this.mAllowedSpecificCarrierIds =
                Collections.unmodifiableSet(new LinkedHashSet<Integer>(b.mCarrierIds));
        this.mRoamingMatchCriteria = b.mRoamingMatchCriteria;
        this.mOpportunisticMatchCriteria = b.mOpportunisticMatchCriteria;
        this.mCbsMatchCriteria = b.mCbsMatchCriteria;
        this.mDunMatchCriteria = b.mDunMatchCriteria;
        this.mImsMatchCriteria = b.mImsMatchCriteria;
        this.mInternetMatchCriteria = b.mInternetMatchCriteria;
        this.mMmsMatchCriteria = b.mMmsMatchCriteria;
        this.mRcsMatchCriteria = b.mRcsMatchCriteria;
    }

    public Set<String> getOperatorPlmnIds() {
        return mAllowedOperatorPlmnIds;
    }

    public Set<Integer> getSimSpecificCarrierIds() {
        return mAllowedSpecificCarrierIds;
    }

    public int getRoaming() {
        return mRoamingMatchCriteria;
    }

    public int getOpportunistic() {
        return mOpportunisticMatchCriteria;
    }

    public int getCbs() {
        return mCbsMatchCriteria;
    }

    public int getDun() {
        return mDunMatchCriteria;
    }

    public int getIms() {
        return mImsMatchCriteria;
    }

    public int getInternet() {
        return mInternetMatchCriteria;
    }

    public int getMms() {
        return mMmsMatchCriteria;
    }

    public int getRcs() {
        return mRcsMatchCriteria;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(super.hashCode()),
                mAllowedOperatorPlmnIds,
                mAllowedSpecificCarrierIds,
                Integer.valueOf(mRoamingMatchCriteria),
                Integer.valueOf(mOpportunisticMatchCriteria),
                Integer.valueOf(mCbsMatchCriteria),
                Integer.valueOf(mDunMatchCriteria),
                Integer.valueOf(mImsMatchCriteria),
                Integer.valueOf(mInternetMatchCriteria),
                Integer.valueOf(mMmsMatchCriteria),
                Integer.valueOf(mRcsMatchCriteria));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VcnCellUnderlyingNetworkTemplate) || !super.equals(o)) {
            return false;
        }
        VcnCellUnderlyingNetworkTemplate other = (VcnCellUnderlyingNetworkTemplate) o;
        return mRoamingMatchCriteria == other.mRoamingMatchCriteria
                && mOpportunisticMatchCriteria == other.mOpportunisticMatchCriteria
                && mCbsMatchCriteria == other.mCbsMatchCriteria
                && mDunMatchCriteria == other.mDunMatchCriteria
                && mImsMatchCriteria == other.mImsMatchCriteria
                && mInternetMatchCriteria == other.mInternetMatchCriteria
                && mMmsMatchCriteria == other.mMmsMatchCriteria
                && mRcsMatchCriteria == other.mRcsMatchCriteria
                && Objects.equals(mAllowedOperatorPlmnIds, other.mAllowedOperatorPlmnIds)
                && Objects.equals(mAllowedSpecificCarrierIds, other.mAllowedSpecificCarrierIds);
    }

    public static final class Builder {
        int mMeteredMatchCriteria = MATCH_ANY;
        final Set<String> mOperatorPlmnIds = new LinkedHashSet<String>();
        final Set<Integer> mCarrierIds = new LinkedHashSet<Integer>();
        int mRoamingMatchCriteria = MATCH_ANY;
        int mOpportunisticMatchCriteria = MATCH_ANY;
        int mMinEntryUpstreamKbps = 0;
        int mMinExitUpstreamKbps = 0;
        int mMinEntryDownstreamKbps = 0;
        int mMinExitDownstreamKbps = 0;
        int mCbsMatchCriteria = MATCH_ANY;
        int mDunMatchCriteria = MATCH_ANY;
        int mImsMatchCriteria = MATCH_ANY;
        int mInternetMatchCriteria = MATCH_REQUIRED;
        int mMmsMatchCriteria = MATCH_ANY;
        int mRcsMatchCriteria = MATCH_ANY;

        public Builder() {}

        public Builder setMetered(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setMetered");
            this.mMeteredMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setOperatorPlmnIds(Set<String> operatorPlmnIds) {
            Objects.requireNonNull(operatorPlmnIds, "operatorPlmnIds is null");
            mOperatorPlmnIds.clear();
            mOperatorPlmnIds.addAll(operatorPlmnIds);
            return this;
        }

        public Builder setSimSpecificCarrierIds(Set<Integer> simSpecificCarrierIds) {
            Objects.requireNonNull(simSpecificCarrierIds, "simSpecificCarrierIds is null");
            mCarrierIds.clear();
            mCarrierIds.addAll(simSpecificCarrierIds);
            return this;
        }

        public Builder setRoaming(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setRoaming");
            this.mRoamingMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setOpportunistic(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setOpportunistic");
            this.mOpportunisticMatchCriteria = matchCriteria;
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

        public Builder setCbs(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setCbs");
            this.mCbsMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setDun(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setDun");
            this.mDunMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setIms(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setIms");
            this.mImsMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setInternet(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setInternet");
            this.mInternetMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setMms(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setMms");
            this.mMmsMatchCriteria = matchCriteria;
            return this;
        }

        public Builder setRcs(int matchCriteria) {
            validateMatchCriteria(matchCriteria, "setRcs");
            this.mRcsMatchCriteria = matchCriteria;
            return this;
        }

        public VcnCellUnderlyingNetworkTemplate build() {
            return new VcnCellUnderlyingNetworkTemplate(this);
        }
    }
}
