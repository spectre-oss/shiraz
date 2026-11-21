// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

public class DocTypeNotSupportedException extends IdentityCredentialException {
    public DocTypeNotSupportedException(String message) {
        super(message);
    }

    public DocTypeNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
