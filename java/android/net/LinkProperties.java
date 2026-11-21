// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Resolver and link snapshot for the host default network; no Android Private DNS service. */
public final class LinkProperties implements Parcelable {
    private String ifaceName;
    private final ArrayList<LinkAddress> linkAddresses = new ArrayList<LinkAddress>();
    private final ArrayList<InetAddress> dns = new ArrayList<InetAddress>();
    private Inet4Address dhcpServerAddress;
    private String domains;
    private int mtu;
    private final ArrayList<RouteInfo> routes = new ArrayList<RouteInfo>();
    private ProxyInfo httpProxy;
    private IpPrefix nat64Prefix;

    public LinkProperties() {
    }

    public LinkProperties(LinkProperties other) {
        if (other != null) {
            ifaceName = other.ifaceName;
            linkAddresses.addAll(other.linkAddresses);
            dns.addAll(other.dns);
            dhcpServerAddress = other.dhcpServerAddress;
            domains = other.domains;
            mtu = other.mtu;
            routes.addAll(other.routes);
            httpProxy = other.httpProxy == null ? null : new ProxyInfo(other.httpProxy);
            nat64Prefix = other.nat64Prefix;
        }
    }

    public void setInterfaceName(String iface) {
        this.ifaceName = iface;
    }

    public String getInterfaceName() {
        return ifaceName;
    }

    public List<LinkAddress> getLinkAddresses() {
        return Collections.unmodifiableList(new ArrayList<LinkAddress>(linkAddresses));
    }

    public void setLinkAddresses(Collection<LinkAddress> addresses) {
        Objects.requireNonNull(addresses, "addresses");
        ArrayList<LinkAddress> copy = new ArrayList<LinkAddress>(addresses);
        linkAddresses.clear();
        for (LinkAddress la : copy) {
            if (la != null && !linkAddresses.contains(la)) {
                linkAddresses.add(la);
            }
        }
    }

    public List<InetAddress> getDnsServers() {
        return Collections.unmodifiableList(new ArrayList<InetAddress>(dns));
    }

    public boolean addDnsServer(InetAddress server) {
        if (server == null || dns.contains(server)) {
            return false;
        }
        dns.add(server);
        return true;
    }

    public void setDnsServers(Collection<InetAddress> servers) {
        ArrayList<InetAddress> copy = new ArrayList<InetAddress>(servers);
        dns.clear();
        for (InetAddress server : copy) {
            addDnsServer(server);
        }
    }

    public boolean isPrivateDnsActive() {
        return false;
    }

    public void setDhcpServerAddress(Inet4Address serverAddress) {
        this.dhcpServerAddress = serverAddress;
    }

    public Inet4Address getDhcpServerAddress() {
        return dhcpServerAddress;
    }

    public String getPrivateDnsServerName() {
        return null;
    }

    public String getDomains() {
        return domains;
    }

    public void setDomains(String value) {
        domains = value;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public int getMtu() {
        return mtu;
    }

    public boolean addRoute(RouteInfo route) {
        if (route == null || routes.contains(route)) {
            return false;
        }
        routes.add(route);
        return true;
    }

    public List<RouteInfo> getRoutes() {
        return Collections.unmodifiableList(new ArrayList<RouteInfo>(routes));
    }

    public void setHttpProxy(ProxyInfo proxy) {
        this.httpProxy = proxy == null ? null : new ProxyInfo(proxy);
    }

    public ProxyInfo getHttpProxy() {
        return httpProxy == null ? null : new ProxyInfo(httpProxy);
    }

    public IpPrefix getNat64Prefix() {
        return nat64Prefix;
    }

    public void setNat64Prefix(IpPrefix prefix) {
        if (prefix != null && prefix.getPrefixLength() != 96) {
            throw new IllegalArgumentException("Only 96-bit NAT64 prefixes are supported");
        }
        this.nat64Prefix = prefix;
    }

    public void clear() {
        ifaceName = null;
        linkAddresses.clear();
        dns.clear();
        dhcpServerAddress = null;
        domains = null;
        mtu = 0;
        routes.clear();
        httpProxy = null;
        nat64Prefix = null;
    }

    public boolean isWakeOnLanSupported() {
        return false;
    }

    boolean shirazSame(LinkProperties other) {
        return equals(other);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "{InterfaceName: "
                + ifaceName
                + " LinkAddresses: "
                + linkAddresses
                + " DnsAddresses: "
                + dns
                + " Domains: "
                + domains
                + " MTU: "
                + mtu
                + " Routes: "
                + routes
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LinkProperties)) {
            return false;
        }
        LinkProperties other = (LinkProperties) obj;
        return Objects.equals(ifaceName, other.ifaceName)
                && linkAddresses.equals(other.linkAddresses)
                && dns.equals(other.dns)
                && Objects.equals(dhcpServerAddress, other.dhcpServerAddress)
                && Objects.equals(domains, other.domains)
                && mtu == other.mtu
                && routes.equals(other.routes)
                && Objects.equals(httpProxy, other.httpProxy)
                && Objects.equals(nat64Prefix, other.nat64Prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ifaceName,
                linkAddresses,
                dns,
                dhcpServerAddress,
                domains,
                Integer.valueOf(mtu),
                routes,
                httpProxy,
                nat64Prefix);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(ifaceName);
        dest.writeTypedList(linkAddresses);
        dest.writeInt(dns.size());
        for (InetAddress d : dns) {
            dest.writeByteArray(d.getAddress());
        }
        dest.writeByteArray(dhcpServerAddress == null ? null : dhcpServerAddress.getAddress());
        dest.writeString(domains);
        dest.writeInt(mtu);
        dest.writeTypedList(routes);
        dest.writeTypedObject(httpProxy, flags);
        dest.writeTypedObject(nat64Prefix, flags);
    }

    public static final Parcelable.Creator<LinkProperties> CREATOR =
            new Parcelable.Creator<LinkProperties>() {
                @Override
                public LinkProperties createFromParcel(Parcel in) {
                    LinkProperties lp = new LinkProperties();
                    lp.ifaceName = in.readString();
                    List<LinkAddress> addrs = in.createTypedArrayList(LinkAddress.CREATOR);
                    if (addrs != null) {
                        lp.linkAddresses.addAll(addrs);
                    }
                    int dnsCount = in.readInt();
                    for (int i = 0; i < dnsCount; i++) {
                        byte[] b = in.createByteArray();
                        if (b != null) {
                            try {
                                lp.addDnsServer(InetAddress.getByAddress(b));
                            } catch (UnknownHostException ignored) {
                            }
                        }
                    }
                    byte[] dhcpBytes = in.createByteArray();
                    if (dhcpBytes != null) {
                        try {
                            InetAddress parsed = InetAddress.getByAddress(dhcpBytes);
                            if (parsed instanceof Inet4Address) {
                                lp.dhcpServerAddress = (Inet4Address) parsed;
                            }
                        } catch (UnknownHostException ignored) {
                        }
                    }
                    lp.domains = in.readString();
                    lp.mtu = in.readInt();
                    List<RouteInfo> rts = in.createTypedArrayList(RouteInfo.CREATOR);
                    if (rts != null) {
                        lp.routes.addAll(rts);
                    }
                    lp.httpProxy = in.readTypedObject(ProxyInfo.CREATOR);
                    lp.nat64Prefix = in.readTypedObject(IpPrefix.CREATOR);
                    return lp;
                }

                @Override
                public LinkProperties[] newArray(int size) {
                    return new LinkProperties[size];
                }
            };
}
