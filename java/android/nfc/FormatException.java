// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

public class FormatException extends Exception {
    public FormatException() {
        super();
    }

    public FormatException(String message) {
        super(message);
    }

    public FormatException(String message, Throwable e) {
        super(message, e);
    }
}
