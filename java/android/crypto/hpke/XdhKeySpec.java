// SPDX-License-Identifier: GPL-3.0-only
package android.crypto.hpke;

import java.security.MessageDigest;
import java.security.spec.EncodedKeySpec;
import java.util.Arrays;

/** Raw encoded X25519 / X448 key specification for HPKE. */
public final class XdhKeySpec extends EncodedKeySpec {
    public XdhKeySpec(byte[] encodedKey) {
        super(encodedKey);
    }

    @Override
    public String getFormat() {
        return "RAW";
    }

    public byte[] getKey() {
        return getEncoded();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EncodedKeySpec)) return false;
        EncodedKeySpec that = (EncodedKeySpec) other;
        return getFormat().equalsIgnoreCase(that.getFormat())
                && MessageDigest.isEqual(getEncoded(), that.getEncoded());
    }

    @Override
    public int hashCode() {
        return 31 * getFormat().toLowerCase(java.util.Locale.ROOT).hashCode()
                + Arrays.hashCode(getEncoded());
    }
}
