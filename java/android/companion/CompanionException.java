// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

public class CompanionException extends RuntimeException {
    public CompanionException(String message) {
        super(message);
    }

    public CompanionException(String message, Throwable cause) {
        super(message, cause);
    }
}
