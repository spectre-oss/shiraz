// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.IOException;

/** Thrown by {@link Base64InputStream} when malformed Base64 input is encountered. */
public class Base64DataException extends IOException {
    public Base64DataException(String detailMessage) {
        super(detailMessage);
    }
}
