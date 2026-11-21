// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.net.Inet4Address;
import java.util.Objects;

public final class IkeIpv4AddrIdentification extends IkeIdentification {
    public final Inet4Address ipv4Address;

    public IkeIpv4AddrIdentification(Inet4Address address) {
        this.ipv4Address = Objects.requireNonNull(address, "address is null");
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipv4Address);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeIpv4AddrIdentification)) {
            return false;
        }
        return Objects.equals(ipv4Address, ((IkeIpv4AddrIdentification) o).ipv4Address);
    }
}
