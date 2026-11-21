// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import java.util.Arrays;
import java.util.Objects;

public final class IkeKeyIdIdentification extends IkeIdentification {
    public final byte[] keyId;

    public IkeKeyIdIdentification(byte[] keyId) {
        Objects.requireNonNull(keyId, "keyId is null");
        this.keyId = keyId.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(keyId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeKeyIdIdentification)) {
            return false;
        }
        return Arrays.equals(keyId, ((IkeKeyIdIdentification) o).keyId);
    }
}
