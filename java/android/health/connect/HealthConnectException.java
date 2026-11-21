// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect;

public class HealthConnectException extends RuntimeException {
    public static final int ERROR_UNKNOWN = 1;
    public static final int ERROR_INTERNAL = 2;
    public static final int ERROR_INVALID_ARGUMENT = 3;
    public static final int ERROR_IO = 4;
    public static final int ERROR_SECURITY = 5;
    public static final int ERROR_REMOTE = 6;
    public static final int ERROR_RATE_LIMIT_EXCEEDED = 7;
    public static final int ERROR_DATA_SYNC_IN_PROGRESS = 8;
    public static final int ERROR_UNSUPPORTED_OPERATION = 9;

    private final int mErrorCode;

    public HealthConnectException(int errorCode) {
        this(errorCode, null, null);
    }

    public HealthConnectException(int errorCode, String message) {
        this(errorCode, message, null);
    }

    public HealthConnectException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }
}
