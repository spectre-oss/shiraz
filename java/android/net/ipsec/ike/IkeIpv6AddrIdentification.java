// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.net.Inet6Address;
import java.util.Objects;

public class IkeIpv6AddrIdentification extends IkeIdentification {
    public final Inet6Address ipv6Address;

    public IkeIpv6AddrIdentification(Inet6Address address) {
        this.ipv6Address = Objects.requireNonNull(address, "address is null");
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipv6Address);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeIpv6AddrIdentification)) {
            return false;
        }
        return Objects.equals(ipv6Address, ((IkeIpv6AddrIdentification) o).ipv6Address);
    }
}
