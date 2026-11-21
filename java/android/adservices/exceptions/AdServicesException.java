// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.exceptions;

/** Checked exception reported by AdServices APIs. */
public class AdServicesException extends Exception {
    public AdServicesException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdServicesException(String message) {
        super(message);
    }
}
