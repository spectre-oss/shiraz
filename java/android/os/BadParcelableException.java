// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class BadParcelableException extends RuntimeException {
    public BadParcelableException(String message){super(message);}
    public BadParcelableException(Exception cause){super(cause);}
}
