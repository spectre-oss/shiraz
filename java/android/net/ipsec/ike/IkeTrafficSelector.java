// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;

public final class IkeTrafficSelector {
    public final int startPort;
    public final int endPort;
    public final InetAddress startingAddress;
    public final InetAddress endingAddress;

    public IkeTrafficSelector(
            int startPort, int endPort, InetAddress startingAddress, InetAddress endingAddress) {
        Objects.requireNonNull(startingAddress, "startingAddress is null");
        Objects.requireNonNull(endingAddress, "endingAddress is null");
        if (startPort < 0 || startPort > 65535 || endPort < 0 || endPort > 65535) {
            throw new IllegalArgumentException(
                    "Invalid port range: [" + startPort + ", " + endPort + "]");
        }
        if (startPort > endPort) {
            throw new IllegalArgumentException(
                    "startPort (" + startPort + ") > endPort (" + endPort + ")");
        }
        boolean startV4 = startingAddress instanceof Inet4Address;
        boolean endV4 = endingAddress instanceof Inet4Address;
        boolean startV6 = startingAddress instanceof Inet6Address;
        boolean endV6 = endingAddress instanceof Inet6Address;
        if ((startV4 != endV4) || (startV6 != endV6)) {
            throw new IllegalArgumentException("Address family mismatch in IkeTrafficSelector");
        }
        byte[] a = startingAddress.getAddress();
        byte[] b = endingAddress.getAddress();
        if (compareUnsigned(a, b) > 0) {
            throw new IllegalArgumentException(
                    "startingAddress is larger than endingAddress");
        }
        this.startPort = startPort;
        this.endPort = endPort;
        this.startingAddress = startingAddress;
        this.endingAddress = endingAddress;
    }

    private static int compareUnsigned(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int ua = a[i] & 0xff;
            int ub = b[i] & 0xff;
            if (ua != ub) {
                return ua - ub;
            }
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(startPort),
                Integer.valueOf(endPort),
                startingAddress,
                endingAddress);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeTrafficSelector)) {
            return false;
        }
        IkeTrafficSelector other = (IkeTrafficSelector) o;
        return startPort == other.startPort
                && endPort == other.endPort
                && Objects.equals(startingAddress, other.startingAddress)
                && Objects.equals(endingAddress, other.endingAddress);
    }
}
