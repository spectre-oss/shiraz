// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class IkeFqdnIdentification extends IkeIdentification {
    public final String fqdn;

    public IkeFqdnIdentification(String fqdn) {
        Objects.requireNonNull(fqdn, "fqdn is null");
        if (fqdn.isEmpty() || !StandardCharsets.US_ASCII.newEncoder().canEncode(fqdn)) {
            throw new IllegalArgumentException("Invalid FQDN: " + fqdn);
        }
        this.fqdn = fqdn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fqdn);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeFqdnIdentification)) {
            return false;
        }
        return Objects.equals(fqdn, ((IkeFqdnIdentification) o).fqdn);
    }
}
