// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.InvalidKeyException;

public class KeyExpiredException extends InvalidKeyException {
    public KeyExpiredException() {
        super("Key expired");
    }

    public KeyExpiredException(String message) {
        super(message);
    }

    public KeyExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
