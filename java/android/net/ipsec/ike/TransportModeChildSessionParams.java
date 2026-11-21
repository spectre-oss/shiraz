// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TransportModeChildSessionParams extends ChildSessionParams {
    TransportModeChildSessionParams(
            List<IkeTrafficSelector> inboundTs,
            List<IkeTrafficSelector> outboundTs,
            List<ChildSaProposal> proposals,
            int hardLifetimeSec,
            int softLifetimeSec) {
        super(inboundTs, outboundTs, proposals, hardLifetimeSec, softLifetimeSec);
    }

    public static final class Builder {
        private final List<IkeTrafficSelector> mInboundTs = new ArrayList<IkeTrafficSelector>();
        private final List<IkeTrafficSelector> mOutboundTs = new ArrayList<IkeTrafficSelector>();
        private final List<ChildSaProposal> mProposals = new ArrayList<ChildSaProposal>();
        private int mHardLifetimeSec = DEFAULT_HARD_LIFETIME_SEC;
        private int mSoftLifetimeSec = DEFAULT_SOFT_LIFETIME_SEC;

        public Builder() {}

        public Builder(TransportModeChildSessionParams childParams) {
            Objects.requireNonNull(childParams, "childParams was null");
            mInboundTs.addAll(childParams.getInboundTrafficSelectors());
            mOutboundTs.addAll(childParams.getOutboundTrafficSelectors());
            mProposals.addAll(childParams.getChildSaProposals());
            mHardLifetimeSec = childParams.getHardLifetimeSeconds();
            mSoftLifetimeSec = childParams.getSoftLifetimeSeconds();
        }

        public Builder addChildSaProposal(ChildSaProposal proposal) {
            mProposals.add(Objects.requireNonNull(proposal, "proposal was null"));
            return this;
        }

        public Builder addInboundTrafficSelectors(IkeTrafficSelector trafficSelector) {
            mInboundTs.add(Objects.requireNonNull(trafficSelector, "trafficSelector was null"));
            return this;
        }

        public Builder addOutboundTrafficSelectors(IkeTrafficSelector trafficSelector) {
            mOutboundTs.add(Objects.requireNonNull(trafficSelector, "trafficSelector was null"));
            return this;
        }

        public Builder setLifetimeSeconds(int hardLifetimeSeconds, int softLifetimeSeconds) {
            if (hardLifetimeSeconds <= softLifetimeSeconds || softLifetimeSeconds <= 0) {
                throw new IllegalArgumentException("Invalid lifetime values");
            }
            this.mHardLifetimeSec = hardLifetimeSeconds;
            this.mSoftLifetimeSec = softLifetimeSeconds;
            return this;
        }

        public TransportModeChildSessionParams build() {
            if (mProposals.isEmpty()) {
                throw new IllegalArgumentException("ChildSessionParams requires at least one SA proposal");
            }
            return new TransportModeChildSessionParams(
                    mInboundTs, mOutboundTs, mProposals, mHardLifetimeSec, mSoftLifetimeSec);
        }
    }
}
