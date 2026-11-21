// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

public abstract class IkeException extends Exception {
    IkeException() {}

    IkeException(String message) {
        super(message);
    }

    IkeException(Throwable cause) {
        super(cause);
    }

    IkeException(String message, Throwable cause) {
        super(message, cause);
    }
}
