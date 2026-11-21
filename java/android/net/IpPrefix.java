// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/** Immutable IPv4 or IPv6 CIDR prefix with masked network address bits. */
public final class IpPrefix implements Parcelable {
    private final byte[] mAddress;
    private final int mPrefixLength;

    public IpPrefix(InetAddress address, int prefixLength) {
        Objects.requireNonNull(address, "address");
        byte[] raw = address.getAddress();
        checkAndMask(raw, prefixLength);
        this.mAddress = raw;
        this.mPrefixLength = prefixLength;
    }

    IpPrefix(byte[] rawAddress, int prefixLength) {
        Objects.requireNonNull(rawAddress, "rawAddress");
        byte[] raw = rawAddress.clone();
        checkAndMask(raw, prefixLength);
        this.mAddress = raw;
        this.mPrefixLength = prefixLength;
    }

    IpPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        int slash = prefix.indexOf('/');
        if (slash <= 0 || slash == prefix.length() - 1 || prefix.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("Invalid IpPrefix: " + prefix);
        }
        InetAddress addr = InetAddresses.parseNumericAddress(prefix.substring(0, slash));
        int len;
        try {
            len = Integer.parseInt(prefix.substring(slash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid prefix length: " + prefix, e);
        }
        byte[] raw = addr.getAddress();
        checkAndMask(raw, len);
        this.mAddress = raw;
        this.mPrefixLength = len;
    }

    private static void checkAndMask(byte[] addr, int prefixLength) {
        if (addr.length != 4 && addr.length != 16) {
            throw new IllegalArgumentException("Invalid IP byte length: " + addr.length);
        }
        int maxPrefix = addr.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefix) {
            throw new IllegalArgumentException(
                    "Invalid prefix length " + prefixLength + " for max " + maxPrefix);
        }
        int fullBytes = prefixLength / 8;
        int remBits = prefixLength % 8;
        if (fullBytes < addr.length) {
            if (remBits != 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                addr[fullBytes] = (byte) (addr[fullBytes] & mask);
                fullBytes++;
            }
            for (int i = fullBytes; i < addr.length; i++) {
                addr[i] = 0;
            }
        }
    }

    public InetAddress getAddress() {
        try {
            return InetAddress.getByAddress(mAddress);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public byte[] getRawAddress() {
        return mAddress.clone();
    }

    public int getPrefixLength() {
        return mPrefixLength;
    }

    public boolean contains(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] candidate = address.getAddress();
        if (candidate.length != mAddress.length) {
            return false;
        }
        checkAndMask(candidate, mPrefixLength);
        return Arrays.equals(mAddress, candidate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IpPrefix)) {
            return false;
        }
        IpPrefix that = (IpPrefix) o;
        return mPrefixLength == that.mPrefixLength && Arrays.equals(mAddress, that.mAddress);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mAddress) + 11 * mPrefixLength;
    }

    @Override
    public String toString() {
        return getAddress().getHostAddress() + "/" + mPrefixLength;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mAddress);
        dest.writeInt(mPrefixLength);
    }

    public static final Parcelable.Creator<IpPrefix> CREATOR =
            new Parcelable.Creator<IpPrefix>() {
                @Override
                public IpPrefix createFromParcel(Parcel in) {
                    byte[] bytes = in.createByteArray();
                    int prefixLength = in.readInt();
                    return new IpPrefix(bytes, prefixLength);
                }

                @Override
                public IpPrefix[] newArray(int size) {
                    return new IpPrefix[size];
                }
            };
}
