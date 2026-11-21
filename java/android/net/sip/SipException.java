// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

public class SipException extends Exception {
    public SipException() {}

    public SipException(String message) {
        super(message);
    }

    public SipException(String message, Throwable cause) {
        super(message, cause);
    }
}
