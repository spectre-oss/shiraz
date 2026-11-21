// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.topics;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Encrypted topic payload returned by the Topics API. */
public final class EncryptedTopic {
    private final byte[] mEncryptedTopic;
    private final String mKeyIdentifier;
    private final byte[] mEncapsulatedKey;

    public EncryptedTopic(
            byte[] encryptedTopic, String keyIdentifier, byte[] encapsulatedKey) {
        Objects.requireNonNull(encryptedTopic, "encryptedTopic");
        Objects.requireNonNull(keyIdentifier, "keyIdentifier");
        Objects.requireNonNull(encapsulatedKey, "encapsulatedKey");
        this.mEncryptedTopic = encryptedTopic.clone();
        this.mKeyIdentifier = keyIdentifier;
        this.mEncapsulatedKey = encapsulatedKey.clone();
    }

    public byte[] getEncryptedTopic() {
        return mEncryptedTopic.clone();
    }

    public String getKeyIdentifier() {
        return mKeyIdentifier;
    }

    public byte[] getEncapsulatedKey() {
        return mEncapsulatedKey.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EncryptedTopic)) {
            return false;
        }
        EncryptedTopic that = (EncryptedTopic) object;
        return Arrays.equals(mEncryptedTopic, that.mEncryptedTopic)
                && Objects.equals(mKeyIdentifier, that.mKeyIdentifier)
                && Arrays.equals(mEncapsulatedKey, that.mEncapsulatedKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(Arrays.hashCode(mEncryptedTopic)),
                mKeyIdentifier,
                Integer.valueOf(Arrays.hashCode(mEncapsulatedKey)));
    }

    @Override
    public String toString() {
        return "EncryptedTopic{mEncryptedTopic="
                + new String(mEncryptedTopic, StandardCharsets.UTF_8)
                + ", mKeyIdentifier="
                + mKeyIdentifier
                + ", mEncapsulatedKey="
                + new String(mEncapsulatedKey, StandardCharsets.UTF_8)
                + "}";
    }
}
