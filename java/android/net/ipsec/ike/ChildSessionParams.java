// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.InetAddresses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class ChildSessionParams {
    static final int DEFAULT_HARD_LIFETIME_SEC = 7200;
    static final int DEFAULT_SOFT_LIFETIME_SEC = 3600;

    static final IkeTrafficSelector DEFAULT_IPV4_TS =
            new IkeTrafficSelector(
                    0,
                    65535,
                    InetAddresses.parseNumericAddress("0.0.0.0"),
                    InetAddresses.parseNumericAddress("255.255.255.255"));
    static final IkeTrafficSelector DEFAULT_IPV6_TS =
            new IkeTrafficSelector(
                    0,
                    65535,
                    InetAddresses.parseNumericAddress("::"),
                    InetAddresses.parseNumericAddress(
                            "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));

    private final List<IkeTrafficSelector> mInboundTrafficSelectors;
    private final List<IkeTrafficSelector> mOutboundTrafficSelectors;
    private final List<ChildSaProposal> mSaProposals;
    private final int mHardLifetimeSec;
    private final int mSoftLifetimeSec;

    ChildSessionParams(
            List<IkeTrafficSelector> inboundTs,
            List<IkeTrafficSelector> outboundTs,
            List<ChildSaProposal> proposals,
            int hardLifetimeSec,
            int softLifetimeSec) {
        this.mInboundTrafficSelectors =
                inboundTs.isEmpty()
                        ? Collections.unmodifiableList(
                                Arrays.asList(DEFAULT_IPV4_TS, DEFAULT_IPV6_TS))
                        : Collections.unmodifiableList(
                                new ArrayList<IkeTrafficSelector>(inboundTs));
        this.mOutboundTrafficSelectors =
                outboundTs.isEmpty()
                        ? Collections.unmodifiableList(
                                Arrays.asList(DEFAULT_IPV4_TS, DEFAULT_IPV6_TS))
                        : Collections.unmodifiableList(
                                new ArrayList<IkeTrafficSelector>(outboundTs));
        this.mSaProposals =
                Collections.unmodifiableList(new ArrayList<ChildSaProposal>(proposals));
        this.mHardLifetimeSec = hardLifetimeSec;
        this.mSoftLifetimeSec = softLifetimeSec;
    }

    public List<IkeTrafficSelector> getInboundTrafficSelectors() {
        return mInboundTrafficSelectors;
    }

    public List<IkeTrafficSelector> getOutboundTrafficSelectors() {
        return mOutboundTrafficSelectors;
    }

    public List<ChildSaProposal> getChildSaProposals() {
        return mSaProposals;
    }

    public int getHardLifetimeSeconds() {
        return mHardLifetimeSec;
    }

    public int getSoftLifetimeSeconds() {
        return mSoftLifetimeSec;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mInboundTrafficSelectors,
                mOutboundTrafficSelectors,
                mSaProposals,
                Integer.valueOf(mHardLifetimeSec),
                Integer.valueOf(mSoftLifetimeSec));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChildSessionParams)) {
            return false;
        }
        ChildSessionParams other = (ChildSessionParams) o;
        return mHardLifetimeSec == other.mHardLifetimeSec
                && mSoftLifetimeSec == other.mSoftLifetimeSec
                && Objects.equals(mInboundTrafficSelectors, other.mInboundTrafficSelectors)
                && Objects.equals(mOutboundTrafficSelectors, other.mOutboundTrafficSelectors)
                && Objects.equals(mSaProposals, other.mSaProposals);
    }
}
