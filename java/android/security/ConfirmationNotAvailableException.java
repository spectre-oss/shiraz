// SPDX-License-Identifier: GPL-3.0-only
package android.security;

public class ConfirmationNotAvailableException extends Exception {
    public ConfirmationNotAvailableException() {
    }

    public ConfirmationNotAvailableException(String message) {
        super(message);
    }
}
