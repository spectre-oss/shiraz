// SPDX-License-Identifier: GPL-3.0-only
package android.os;

public class ParcelFormatException extends RuntimeException {
    public ParcelFormatException() {
        super();
    }

    public ParcelFormatException(String reason) {
        super(reason);
    }
}
