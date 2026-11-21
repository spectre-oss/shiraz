// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** IP address with prefix length, flags, and scope on a network interface. */
public class LinkAddress implements Parcelable {
    private static final int RT_SCOPE_UNIVERSE = 0;
    private static final int RT_SCOPE_SITE = 200;
    private static final int RT_SCOPE_LINK = 253;
    private static final int RT_SCOPE_HOST = 254;

    private final InetAddress mAddress;
    private final int mPrefixLength;
    private final int mFlags;
    private final int mScope;

    LinkAddress(InetAddress address, int prefixLength, int flags, int scope) {
        Objects.requireNonNull(address, "address");
        int maxPrefix = (address instanceof Inet4Address) ? 32 : 128;
        if (prefixLength < 0 || prefixLength > maxPrefix) {
            throw new IllegalArgumentException(
                    "Bad LinkAddress prefixLength " + prefixLength + " for " + address);
        }
        if (address.isMulticastAddress()) {
            throw new IllegalArgumentException("Multicast LinkAddress not supported: " + address);
        }
        this.mAddress = address;
        this.mPrefixLength = prefixLength;
        this.mFlags = flags;
        this.mScope = scope;
    }

    LinkAddress(InetAddress address, int prefixLength) {
        this(address, prefixLength, 0, scopeForUnicastAddress(address));
    }

    LinkAddress(String address) {
        Objects.requireNonNull(address, "address");
        int slash = address.indexOf('/');
        if (slash <= 0 || slash == address.length() - 1 || address.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("Invalid LinkAddress: " + address);
        }
        InetAddress parsed = InetAddresses.parseNumericAddress(address.substring(0, slash));
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(address.substring(slash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid LinkAddress prefix length: " + address, e);
        }
        int maxPrefix = (parsed instanceof Inet4Address) ? 32 : 128;
        if (prefixLength < 0 || prefixLength > maxPrefix || parsed.isMulticastAddress()) {
            throw new IllegalArgumentException("Invalid LinkAddress: " + address);
        }
        this.mAddress = parsed;
        this.mPrefixLength = prefixLength;
        this.mFlags = 0;
        this.mScope = scopeForUnicastAddress(parsed);
    }

    private static int scopeForUnicastAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return RT_SCOPE_HOST;
        }
        if (addr.isLinkLocalAddress()) {
            return RT_SCOPE_LINK;
        }
        if (addr.isSiteLocalAddress()) {
            return RT_SCOPE_SITE;
        }
        return RT_SCOPE_UNIVERSE;
    }

    public InetAddress getAddress() {
        return mAddress;
    }

    public int getPrefixLength() {
        return mPrefixLength;
    }

    public int getFlags() {
        return mFlags;
    }

    public int getScope() {
        return mScope;
    }

    @Override
    public String toString() {
        return mAddress.getHostAddress() + "/" + mPrefixLength;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LinkAddress)) {
            return false;
        }
        LinkAddress other = (LinkAddress) obj;
        return mPrefixLength == other.mPrefixLength
                && mFlags == other.mFlags
                && mScope == other.mScope
                && Objects.equals(mAddress, other.mAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mAddress,
                Integer.valueOf(mPrefixLength),
                Integer.valueOf(mFlags),
                Integer.valueOf(mScope));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mAddress.getAddress());
        dest.writeInt(mPrefixLength);
        dest.writeInt(mFlags);
        dest.writeInt(mScope);
    }

    public static final Parcelable.Creator<LinkAddress> CREATOR =
            new Parcelable.Creator<LinkAddress>() {
                @Override
                public LinkAddress createFromParcel(Parcel in) {
                    byte[] raw = in.createByteArray();
                    int prefixLength = in.readInt();
                    int addrFlags = in.readInt();
                    int scope = in.readInt();
                    try {
                        return new LinkAddress(
                                InetAddress.getByAddress(raw), prefixLength, addrFlags, scope);
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException("Invalid address in parcel", e);
                    }
                }

                @Override
                public LinkAddress[] newArray(int size) {
                    return new LinkAddress[size];
                }
            };
}
