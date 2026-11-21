// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

public final class IkeInternalException extends IkeNonProtocolException {
    public IkeInternalException(Throwable cause) {
        super(cause);
    }

    public IkeInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
