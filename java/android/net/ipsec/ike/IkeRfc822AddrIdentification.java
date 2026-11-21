// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class IkeRfc822AddrIdentification extends IkeIdentification {
    public final String rfc822Name;

    public IkeRfc822AddrIdentification(String rfc822Name) {
        Objects.requireNonNull(rfc822Name, "rfc822Name is null");
        if (rfc822Name.isEmpty() || !StandardCharsets.US_ASCII.newEncoder().canEncode(rfc822Name)) {
            throw new IllegalArgumentException("Invalid RFC822 name: " + rfc822Name);
        }
        this.rfc822Name = rfc822Name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rfc822Name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeRfc822AddrIdentification)) {
            return false;
        }
        return Objects.equals(rfc822Name, ((IkeRfc822AddrIdentification) o).rfc822Name);
    }
}
