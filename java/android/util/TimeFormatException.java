// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Exception thrown on malformed time strings. */
public class TimeFormatException extends RuntimeException {
    TimeFormatException() {}

    TimeFormatException(String s) {
        super(s);
    }
}
