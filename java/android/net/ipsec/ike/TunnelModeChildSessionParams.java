// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.system.OsConstants;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TunnelModeChildSessionParams extends ChildSessionParams {
    private final List<TunnelModeChildConfigRequest> mConfigRequests;

    TunnelModeChildSessionParams(
            List<IkeTrafficSelector> inboundTs,
            List<IkeTrafficSelector> outboundTs,
            List<ChildSaProposal> proposals,
            List<TunnelModeChildConfigRequest> configRequests,
            int hardLifetimeSec,
            int softLifetimeSec) {
        super(inboundTs, outboundTs, proposals, hardLifetimeSec, softLifetimeSec);
        this.mConfigRequests =
                Collections.unmodifiableList(
                        new ArrayList<TunnelModeChildConfigRequest>(configRequests));
    }

    public List<TunnelModeChildConfigRequest> getConfigurationRequests() {
        return mConfigRequests;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(super.hashCode()), mConfigRequests);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TunnelModeChildSessionParams) || !super.equals(o)) {
            return false;
        }
        return Objects.equals(
                mConfigRequests, ((TunnelModeChildSessionParams) o).mConfigRequests);
    }

    public interface TunnelModeChildConfigRequest {}

    public interface ConfigRequestIpv4Address extends TunnelModeChildConfigRequest {
        Inet4Address getAddress();
    }

    public interface ConfigRequestIpv4DhcpServer extends TunnelModeChildConfigRequest {}

    public interface ConfigRequestIpv4DnsServer extends TunnelModeChildConfigRequest {}

    public interface ConfigRequestIpv4Netmask extends TunnelModeChildConfigRequest {}

    public interface ConfigRequestIpv6Address extends TunnelModeChildConfigRequest {
        Inet6Address getAddress();

        int getPrefixLength();
    }

    public interface ConfigRequestIpv6DnsServer extends TunnelModeChildConfigRequest {}

    private static final class Ipv4AddrReq implements ConfigRequestIpv4Address {
        private final Inet4Address mAddr;

        Ipv4AddrReq(Inet4Address addr) {
            this.mAddr = addr;
        }

        @Override
        public Inet4Address getAddress() {
            return mAddr;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Ipv4AddrReq.class, mAddr);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Ipv4AddrReq) && Objects.equals(mAddr, ((Ipv4AddrReq) o).mAddr);
        }
    }

    private static final class Ipv4NetmaskReq implements ConfigRequestIpv4Netmask {
        @Override
        public int hashCode() {
            return Ipv4NetmaskReq.class.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv4NetmaskReq;
        }
    }

    private static final class Ipv6AddrReq implements ConfigRequestIpv6Address {
        private final Inet6Address mAddr;
        private final int mPrefixLen;

        Ipv6AddrReq(Inet6Address addr, int prefixLen) {
            this.mAddr = addr;
            this.mPrefixLen = prefixLen;
        }

        @Override
        public Inet6Address getAddress() {
            return mAddr;
        }

        @Override
        public int getPrefixLength() {
            return mPrefixLen;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Ipv6AddrReq.class, mAddr, Integer.valueOf(mPrefixLen));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Ipv6AddrReq)) {
                return false;
            }
            Ipv6AddrReq other = (Ipv6AddrReq) o;
            return mPrefixLen == other.mPrefixLen && Objects.equals(mAddr, other.mAddr);
        }
    }

    private static final class Ipv4DnsReq implements ConfigRequestIpv4DnsServer {
        @Override
        public int hashCode() {
            return Ipv4DnsReq.class.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv4DnsReq;
        }
    }

    private static final class Ipv6DnsReq implements ConfigRequestIpv6DnsServer {
        @Override
        public int hashCode() {
            return Ipv6DnsReq.class.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv6DnsReq;
        }
    }

    private static final class Ipv4DhcpReq implements ConfigRequestIpv4DhcpServer {
        @Override
        public int hashCode() {
            return Ipv4DhcpReq.class.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv4DhcpReq;
        }
    }

    public static final class Builder {
        private final List<IkeTrafficSelector> mInboundTs = new ArrayList<IkeTrafficSelector>();
        private final List<IkeTrafficSelector> mOutboundTs = new ArrayList<IkeTrafficSelector>();
        private final List<ChildSaProposal> mProposals = new ArrayList<ChildSaProposal>();
        private final List<TunnelModeChildConfigRequest> mConfigRequests =
                new ArrayList<TunnelModeChildConfigRequest>();
        private int mHardLifetimeSec = DEFAULT_HARD_LIFETIME_SEC;
        private int mSoftLifetimeSec = DEFAULT_SOFT_LIFETIME_SEC;

        public Builder() {}

        public Builder(TunnelModeChildSessionParams childParams) {
            Objects.requireNonNull(childParams, "childParams was null");
            mInboundTs.addAll(childParams.getInboundTrafficSelectors());
            mOutboundTs.addAll(childParams.getOutboundTrafficSelectors());
            mProposals.addAll(childParams.getChildSaProposals());
            mConfigRequests.addAll(childParams.getConfigurationRequests());
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

        public Builder addInternalAddressRequest(int addressFamily) {
            if (addressFamily == OsConstants.AF_INET) {
                mConfigRequests.add(new Ipv4AddrReq(null));
                mConfigRequests.add(new Ipv4NetmaskReq());
            } else if (addressFamily == OsConstants.AF_INET6) {
                mConfigRequests.add(new Ipv6AddrReq(null, 0));
            } else {
                throw new IllegalArgumentException("Invalid address family: " + addressFamily);
            }
            return this;
        }

        public Builder addInternalAddressRequest(Inet4Address address) {
            Objects.requireNonNull(address, "address was null");
            mConfigRequests.add(new Ipv4AddrReq(address));
            mConfigRequests.add(new Ipv4NetmaskReq());
            return this;
        }

        public Builder addInternalAddressRequest(Inet6Address address, int prefixLen) {
            Objects.requireNonNull(address, "address was null");
            if (prefixLen < 0 || prefixLen > 128) {
                throw new IllegalArgumentException("Invalid prefix length: " + prefixLen);
            }
            mConfigRequests.add(new Ipv6AddrReq(address, prefixLen));
            return this;
        }

        public Builder addInternalDnsServerRequest(int addressFamily) {
            if (addressFamily == OsConstants.AF_INET) {
                mConfigRequests.add(new Ipv4DnsReq());
            } else if (addressFamily == OsConstants.AF_INET6) {
                mConfigRequests.add(new Ipv6DnsReq());
            } else {
                throw new IllegalArgumentException("Invalid address family: " + addressFamily);
            }
            return this;
        }

        public Builder addInternalDhcpServerRequest(int addressFamily) {
            if (addressFamily == OsConstants.AF_INET) {
                mConfigRequests.add(new Ipv4DhcpReq());
            } else {
                throw new IllegalArgumentException("Invalid address family: " + addressFamily);
            }
            return this;
        }

        public TunnelModeChildSessionParams build() {
            if (mProposals.isEmpty()) {
                throw new IllegalArgumentException(
                        "TunnelModeChildSessionParams requires at least one SA proposal");
            }
            return new TunnelModeChildSessionParams(
                    mInboundTs,
                    mOutboundTs,
                    mProposals,
                    mConfigRequests,
                    mHardLifetimeSec,
                    mSoftLifetimeSec);
        }
    }
}
