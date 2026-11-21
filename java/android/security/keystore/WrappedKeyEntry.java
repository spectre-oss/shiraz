// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.KeyStore;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

public class WrappedKeyEntry implements KeyStore.Entry {
    private final byte[] mWrappedKeyBytes;
    private final String mWrappingKeyAlias;
    private final String mTransformation;
    private final AlgorithmParameterSpec mAlgorithmParameterSpec;

    public WrappedKeyEntry(byte[] wrappedKeyBytes, String wrappingKeyAlias,
            String transformation, AlgorithmParameterSpec algorithmParameterSpec) {
        mWrappedKeyBytes = Objects.requireNonNull(wrappedKeyBytes, "wrappedKeyBytes == null").clone();
        mWrappingKeyAlias = Objects.requireNonNull(wrappingKeyAlias, "wrappingKeyAlias == null");
        mTransformation = Objects.requireNonNull(transformation, "transformation == null");
        mAlgorithmParameterSpec = algorithmParameterSpec;
    }

    public byte[] getWrappedKeyBytes() {
        return mWrappedKeyBytes.clone();
    }

    public String getWrappingKeyAlias() {
        return mWrappingKeyAlias;
    }

    public String getTransformation() {
        return mTransformation;
    }

    public AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return mAlgorithmParameterSpec;
    }
}
