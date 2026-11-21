// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.util.Objects;
import javax.security.auth.x500.X500Principal;

public final class IkeDerAsn1DnIdentification extends IkeIdentification {
    public final X500Principal derAsn1Dn;

    public IkeDerAsn1DnIdentification(X500Principal derAsn1Dn) {
        this.derAsn1Dn = Objects.requireNonNull(derAsn1Dn, "derAsn1Dn is null");
    }

    @Override
    public int hashCode() {
        return Objects.hash(derAsn1Dn);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeDerAsn1DnIdentification)) {
            return false;
        }
        return Objects.equals(derAsn1Dn, ((IkeDerAsn1DnIdentification) o).derAsn1Dn);
    }
}
