// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

public final class ImsException extends Exception {
    public static final int CODE_ERROR_UNSPECIFIED = 0;
    public static final int CODE_ERROR_SERVICE_UNAVAILABLE = 1;
    public static final int CODE_ERROR_UNSUPPORTED_OPERATION = 2;
    public static final int CODE_ERROR_INVALID_SUBSCRIPTION = 3;

    private final int mCode;

    ImsException(String message) {
        this(message, CODE_ERROR_UNSPECIFIED);
    }

    ImsException(String message, int code) {
        super(getMessage(message, code));
        mCode = code;
    }

    ImsException(String message, int code, Throwable cause) {
        super(getMessage(message, code), cause);
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

    private static String getMessage(String message, int code) {
        if (message != null && !message.isEmpty()) {
            return message + " (code: " + code + ")";
        }
        return "code: " + code;
    }
}
