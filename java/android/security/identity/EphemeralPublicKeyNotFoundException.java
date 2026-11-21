// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

public class EphemeralPublicKeyNotFoundException extends IdentityCredentialException {
    public EphemeralPublicKeyNotFoundException(String message) {
        super(message);
    }

    public EphemeralPublicKeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
