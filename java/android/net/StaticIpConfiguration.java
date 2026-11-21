// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Static IPv4/IPv6 address, gateway, DNS, and search domain configuration. */
public final class StaticIpConfiguration implements Parcelable {
    private final LinkAddress mIpAddress;
    private final InetAddress mGateway;
    private final List<InetAddress> mDnsServers;
    private final String mDomains;

    StaticIpConfiguration(
            LinkAddress ipAddress,
            InetAddress gateway,
            List<InetAddress> dnsServers,
            String domains) {
        this.mIpAddress = ipAddress;
        this.mGateway = gateway;
        List<InetAddress> copy = new ArrayList<InetAddress>();
        if (dnsServers != null) {
            for (InetAddress s : dnsServers) {
                if (s != null) {
                    copy.add(s);
                }
            }
        }
        this.mDnsServers = Collections.unmodifiableList(copy);
        this.mDomains = domains;
    }

    public LinkAddress getIpAddress() {
        return mIpAddress;
    }

    public InetAddress getGateway() {
        return mGateway;
    }

    public List<InetAddress> getDnsServers() {
        return mDnsServers;
    }

    public String getDomains() {
        return mDomains;
    }

    @Override
    public String toString() {
        return "IP address "
                + mIpAddress
                + " Gateway "
                + mGateway
                + " DNS servers: "
                + mDnsServers
                + " Domains "
                + mDomains;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIpAddress, mGateway, mDnsServers, mDomains);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StaticIpConfiguration)) {
            return false;
        }
        StaticIpConfiguration other = (StaticIpConfiguration) obj;
        return Objects.equals(mIpAddress, other.mIpAddress)
                && Objects.equals(mGateway, other.mGateway)
                && Objects.equals(mDnsServers, other.mDnsServers)
                && Objects.equals(mDomains, other.mDomains);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIpAddress, flags);
        dest.writeByteArray(mGateway == null ? null : mGateway.getAddress());
        dest.writeInt(mDnsServers.size());
        for (InetAddress s : mDnsServers) {
            dest.writeByteArray(s.getAddress());
        }
        dest.writeString(mDomains);
    }

    public static final class Builder {
        private LinkAddress mIpAddress;
        private InetAddress mGateway;
        private final List<InetAddress> mDnsServers = new ArrayList<InetAddress>();
        private String mDomains;

        public Builder() {
        }

        public Builder setIpAddress(LinkAddress ipAddress) {
            this.mIpAddress = ipAddress;
            return this;
        }

        public Builder setGateway(InetAddress gateway) {
            this.mGateway = gateway;
            return this;
        }

        public Builder setDnsServers(Iterable<InetAddress> dnsServers) {
            Objects.requireNonNull(dnsServers, "dnsServers");
            mDnsServers.clear();
            for (InetAddress s : dnsServers) {
                mDnsServers.add(Objects.requireNonNull(s, "dnsServer"));
            }
            return this;
        }

        public Builder setDomains(String newDomains) {
            this.mDomains = newDomains;
            return this;
        }

        public StaticIpConfiguration build() {
            return new StaticIpConfiguration(mIpAddress, mGateway, mDnsServers, mDomains);
        }
    }

    public static final Parcelable.Creator<StaticIpConfiguration> CREATOR =
            new Parcelable.Creator<StaticIpConfiguration>() {
                @Override
                public StaticIpConfiguration createFromParcel(Parcel in) {
                    LinkAddress ip = in.readTypedObject(LinkAddress.CREATOR);
                    byte[] gwBytes = in.createByteArray();
                    InetAddress gw = null;
                    if (gwBytes != null) {
                        try {
                            gw = InetAddress.getByAddress(gwBytes);
                        } catch (UnknownHostException ignored) {
                        }
                    }
                    int dnsCount = in.readInt();
                    List<InetAddress> dns = new ArrayList<InetAddress>(dnsCount);
                    for (int i = 0; i < dnsCount; i++) {
                        byte[] b = in.createByteArray();
                        if (b != null) {
                            try {
                                dns.add(InetAddress.getByAddress(b));
                            } catch (UnknownHostException ignored) {
                            }
                        }
                    }
                    String domains = in.readString();
                    return new StaticIpConfiguration(ip, gw, dns, domains);
                }

                @Override
                public StaticIpConfiguration[] newArray(int size) {
                    return new StaticIpConfiguration[size];
                }
            };
}
