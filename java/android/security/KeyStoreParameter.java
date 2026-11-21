// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import android.content.Context;
import java.security.KeyStore;
import java.util.Objects;

@Deprecated
public final class KeyStoreParameter implements KeyStore.ProtectionParameter {
    private final boolean mEncryptionRequired;

    private KeyStoreParameter(boolean encryptionRequired) {
        mEncryptionRequired = encryptionRequired;
    }

    @Deprecated
    public boolean isEncryptionRequired() {
        return mEncryptionRequired;
    }

    @Deprecated
    public static final class Builder {
        private boolean mEncryptionRequired;

        public Builder(Context context) {
            Objects.requireNonNull(context, "context == null");
        }

        @Deprecated
        public Builder setEncryptionRequired(boolean required) {
            mEncryptionRequired = required;
            return this;
        }

        public KeyStoreParameter build() {
            return new KeyStoreParameter(mEncryptionRequired);
        }
    }
}
