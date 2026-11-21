// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;

/** IPv4 DHCP configuration snapshot (little-endian int addresses per Android convention). */
public class DhcpInfo implements Parcelable {
    public int ipAddress;
    public int gateway;
    public int netmask;
    public int dns1;
    public int dns2;
    public int serverAddress;
    public int leaseDuration;

    public DhcpInfo() {
    }

    DhcpInfo(DhcpInfo source) {
        if (source != null) {
            ipAddress = source.ipAddress;
            gateway = source.gateway;
            netmask = source.netmask;
            dns1 = source.dns1;
            dns2 = source.dns2;
            serverAddress = source.serverAddress;
            leaseDuration = source.leaseDuration;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ipaddr ");
        putAddress(sb, ipAddress);
        sb.append(" gateway ");
        putAddress(sb, gateway);
        sb.append(" netmask ");
        putAddress(sb, netmask);
        sb.append(" dns1 ");
        putAddress(sb, dns1);
        sb.append(" dns2 ");
        putAddress(sb, dns2);
        sb.append(" DHCP server ");
        putAddress(sb, serverAddress);
        sb.append(" lease ").append(leaseDuration).append(" seconds");
        return sb.toString();
    }

    private static void putAddress(StringBuilder sb, int addr) {
        sb.append(addr & 0xff)
                .append('.')
                .append((addr >> 8) & 0xff)
                .append('.')
                .append((addr >> 16) & 0xff)
                .append('.')
                .append((addr >> 24) & 0xff);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ipAddress);
        dest.writeInt(gateway);
        dest.writeInt(netmask);
        dest.writeInt(dns1);
        dest.writeInt(dns2);
        dest.writeInt(serverAddress);
        dest.writeInt(leaseDuration);
    }

    public static final Parcelable.Creator<DhcpInfo> CREATOR =
            new Parcelable.Creator<DhcpInfo>() {
                @Override
                public DhcpInfo createFromParcel(Parcel in) {
                    DhcpInfo info = new DhcpInfo();
                    info.ipAddress = in.readInt();
                    info.gateway = in.readInt();
                    info.netmask = in.readInt();
                    info.dns1 = in.readInt();
                    info.dns2 = in.readInt();
                    info.serverAddress = in.readInt();
                    info.leaseDuration = in.readInt();
                    return info;
                }

                @Override
                public DhcpInfo[] newArray(int size) {
                    return new DhcpInfo[size];
                }
            };
}
