// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.TransportInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class WifiAwareNetworkInfo implements TransportInfo, Parcelable {
    public static final Creator<WifiAwareNetworkInfo> CREATOR =
            new Creator<WifiAwareNetworkInfo>() {
                @Override
                public WifiAwareNetworkInfo createFromParcel(Parcel in) {
                    byte[] addrBytes = in.createByteArray();
                    String niName = in.readString();
                    Inet6Address ipv6 = null;
                    if (addrBytes != null) {
                        try {
                            NetworkInterface ni =
                                    niName == null ? null : NetworkInterface.getByName(niName);
                            if (ni != null) {
                                ipv6 = Inet6Address.getByAddress(null, addrBytes, ni);
                            } else {
                                InetAddress parsed = InetAddress.getByAddress(addrBytes);
                                if (parsed instanceof Inet6Address) {
                                    ipv6 = (Inet6Address) parsed;
                                }
                            }
                        } catch (SocketException | UnknownHostException ignored) {
                            ipv6 = null;
                        }
                    }
                    int port = in.readInt();
                    int transportProtocol = in.readInt();
                    int count = in.readInt();
                    List<WifiAwareChannelInfo> channels = new ArrayList<WifiAwareChannelInfo>();
                    for (int i = 0; i < count; i++) {
                        WifiAwareChannelInfo ch =
                                in.readTypedObject(WifiAwareChannelInfo.CREATOR);
                        if (ch != null) {
                            channels.add(ch);
                        }
                    }
                    return new WifiAwareNetworkInfo(ipv6, port, transportProtocol, channels);
                }

                @Override
                public WifiAwareNetworkInfo[] newArray(int size) {
                    return new WifiAwareNetworkInfo[size];
                }
            };

    private final Inet6Address mIpv6Addr;
    private final int mPort;
    private final int mTransportProtocol;
    private final List<WifiAwareChannelInfo> mChannelInfos;

    WifiAwareNetworkInfo(
            Inet6Address ipv6Addr,
            int port,
            int transportProtocol,
            List<WifiAwareChannelInfo> channelInfos) {
        mIpv6Addr = ipv6Addr;
        mPort = port;
        mTransportProtocol = transportProtocol;
        mChannelInfos =
                channelInfos == null
                        ? Collections.<WifiAwareChannelInfo>emptyList()
                        : Collections.unmodifiableList(
                                new ArrayList<WifiAwareChannelInfo>(channelInfos));
    }

    public Inet6Address getPeerIpv6Addr() {
        return mIpv6Addr;
    }

    public int getPort() {
        return mPort;
    }

    public int getTransportProtocol() {
        return mTransportProtocol;
    }

    public List<WifiAwareChannelInfo> getChannelInfoList() {
        return mChannelInfos;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mIpv6Addr == null) {
            dest.writeByteArray(null);
            dest.writeString(null);
        } else {
            dest.writeByteArray(mIpv6Addr.getAddress());
            NetworkInterface ni = mIpv6Addr.getScopedInterface();
            dest.writeString(ni == null ? null : ni.getName());
        }
        dest.writeInt(mPort);
        dest.writeInt(mTransportProtocol);
        dest.writeInt(mChannelInfos.size());
        for (int i = 0; i < mChannelInfos.size(); i++) {
            dest.writeTypedObject(mChannelInfos.get(i), flags);
        }
    }

    @Override
    public String toString() {
        return "AwareNetworkInfo: IPv6="
                + mIpv6Addr
                + ", port="
                + mPort
                + ", transportProtocol="
                + mTransportProtocol
                + ", channelInfos="
                + mChannelInfos;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiAwareNetworkInfo)) {
            return false;
        }
        WifiAwareNetworkInfo lhs = (WifiAwareNetworkInfo) obj;
        return Objects.equals(mIpv6Addr, lhs.mIpv6Addr)
                && mPort == lhs.mPort
                && mTransportProtocol == lhs.mTransportProtocol
                && Objects.equals(mChannelInfos, lhs.mChannelInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mIpv6Addr,
                Integer.valueOf(mPort),
                Integer.valueOf(mTransportProtocol),
                mChannelInfos);
    }
}
