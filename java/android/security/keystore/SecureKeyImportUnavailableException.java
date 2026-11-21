// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.ProviderException;

public class SecureKeyImportUnavailableException extends ProviderException {
    public SecureKeyImportUnavailableException() {
        super();
    }

    public SecureKeyImportUnavailableException(String message) {
        super(message);
    }

    public SecureKeyImportUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecureKeyImportUnavailableException(Throwable cause) {
        super(cause);
    }
}
