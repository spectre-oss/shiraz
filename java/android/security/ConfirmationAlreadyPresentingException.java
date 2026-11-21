// SPDX-License-Identifier: GPL-3.0-only
package android.security;

public class ConfirmationAlreadyPresentingException extends Exception {
    public ConfirmationAlreadyPresentingException() {
    }

    public ConfirmationAlreadyPresentingException(String message) {
        super(message);
    }
}
