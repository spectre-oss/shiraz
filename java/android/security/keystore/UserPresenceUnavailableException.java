// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.InvalidKeyException;

public class UserPresenceUnavailableException extends InvalidKeyException {
    public UserPresenceUnavailableException() {
        super("No StrongBox available.");
    }

    public UserPresenceUnavailableException(String message) {
        super(message);
    }

    public UserPresenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
