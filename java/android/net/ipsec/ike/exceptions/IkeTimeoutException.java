// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

import java.io.IOException;

public final class IkeTimeoutException extends IOException {
    public IkeTimeoutException(String message) {
        super(message);
    }
}
