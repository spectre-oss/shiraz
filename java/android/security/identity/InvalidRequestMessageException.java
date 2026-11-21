// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

public class InvalidRequestMessageException extends IdentityCredentialException {
    public InvalidRequestMessageException(String message) {
        super(message);
    }

    public InvalidRequestMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
