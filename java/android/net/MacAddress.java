// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class MacAddress implements Parcelable {
    public static final int TYPE_UNICAST = 1;
    public static final int TYPE_MULTICAST = 2;
    public static final int TYPE_BROADCAST = 3;

    private static final int ETHER_ADDR_LEN = 6;
    private static final long VALID_LONG_MASK = (1L << 48) - 1;
    private static final long LOCALLY_ASSIGNED_MASK = 1L << 41;
    private static final long MULTICAST_MASK = 1L << 40;

    public static final MacAddress BROADCAST_ADDRESS =
            fromBytes(new byte[] { -1, -1, -1, -1, -1, -1 });

    private final long mAddr;

    private MacAddress(long addr) {
        mAddr = addr & VALID_LONG_MASK;
    }

    public int getAddressType() {
        if (equals(BROADCAST_ADDRESS)) {
            return TYPE_BROADCAST;
        }
        if ((mAddr & MULTICAST_MASK) != 0) {
            return TYPE_MULTICAST;
        }
        return TYPE_UNICAST;
    }

    public boolean isLocallyAssigned() {
        return (mAddr & LOCALLY_ASSIGNED_MASK) != 0;
    }

    public byte[] toByteArray() {
        byte[] b = new byte[ETHER_ADDR_LEN];
        for (int i = 0; i < ETHER_ADDR_LEN; i++) {
            b[i] = (byte) ((mAddr >> ((ETHER_ADDR_LEN - 1 - i) * 8)) & 0xFF);
        }
        return b;
    }

    @Override
    public String toString() {
        byte[] b = toByteArray();
        return String.format(Locale.US, "%02x:%02x:%02x:%02x:%02x:%02x",
                b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF,
                b[3] & 0xFF, b[4] & 0xFF, b[5] & 0xFF);
    }

    public String toOuiString() {
        byte[] b = toByteArray();
        return String.format(Locale.US, "%02x:%02x:%02x",
                b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(mAddr);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof MacAddress) && ((MacAddress) o).mAddr == mAddr;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mAddr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static MacAddress fromString(String addr) {
        Objects.requireNonNull(addr, "addr must not be null");
        String[] parts = addr.split(":", -1);
        if (parts.length != ETHER_ADDR_LEN) {
            throw new IllegalArgumentException("Invalid MAC address: " + addr);
        }
        byte[] bytes = new byte[ETHER_ADDR_LEN];
        for (int i = 0; i < ETHER_ADDR_LEN; i++) {
            String p = parts[i];
            if (p.length() != 2) {
                throw new IllegalArgumentException("Invalid MAC octet in: " + addr);
            }
            int val = Integer.parseInt(p, 16);
            if (val < 0 || val > 255) {
                throw new IllegalArgumentException("Invalid MAC byte in: " + addr);
            }
            bytes[i] = (byte) val;
        }
        return fromBytes(bytes);
    }

    public static MacAddress fromBytes(byte[] addr) {
        Objects.requireNonNull(addr, "addr must not be null");
        if (addr.length != ETHER_ADDR_LEN) {
            throw new IllegalArgumentException(
                    Arrays.toString(addr) + " was not a valid MAC address");
        }
        long val = 0;
        for (int i = 0; i < ETHER_ADDR_LEN; i++) {
            val = (val << 8) | (addr[i] & 0xFFL);
        }
        return new MacAddress(val);
    }

    public boolean matches(MacAddress baseAddress, MacAddress mask) {
        Objects.requireNonNull(baseAddress, "baseAddress must not be null");
        Objects.requireNonNull(mask, "mask must not be null");
        return (mAddr & mask.mAddr) == (baseAddress.mAddr & mask.mAddr);
    }

    public Inet6Address getLinkLocalIpv6FromEui48Mac() {
        byte[] mac = toByteArray();
        byte[] ipv6 = new byte[16];
        ipv6[0] = (byte) 0xfe;
        ipv6[1] = (byte) 0x80;
        ipv6[8] = (byte) (mac[0] ^ 0x02);
        ipv6[9] = mac[1];
        ipv6[10] = mac[2];
        ipv6[11] = (byte) 0xff;
        ipv6[12] = (byte) 0xfe;
        ipv6[13] = mac[3];
        ipv6[14] = mac[4];
        ipv6[15] = mac[5];
        try {
            return Inet6Address.getByAddress(null, ipv6, 0);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static final Creator<MacAddress> CREATOR = new Creator<MacAddress>() {
        @Override
        public MacAddress createFromParcel(Parcel in) {
            return new MacAddress(in.readLong());
        }

        @Override
        public MacAddress[] newArray(int size) {
            return new MacAddress[size];
        }
    };
}
