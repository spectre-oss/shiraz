// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.InvalidKeyException;

public class KeyPermanentlyInvalidatedException extends InvalidKeyException {
    public KeyPermanentlyInvalidatedException() {
        super("Key permanently invalidated");
    }

    public KeyPermanentlyInvalidatedException(String message) {
        super(message);
    }

    public KeyPermanentlyInvalidatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
