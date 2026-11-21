// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

public abstract class IkeNonProtocolException extends IkeException {
    IkeNonProtocolException() {}

    IkeNonProtocolException(String message) {
        super(message);
    }

    IkeNonProtocolException(Throwable cause) {
        super(cause);
    }

    IkeNonProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
