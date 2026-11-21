// SPDX-License-Identifier: GPL-3.0-only
package android.security;

public class KeyStoreException extends Exception {
    public static final int ERROR_OTHER = 1;
    public static final int ERROR_USER_AUTHENTICATION_REQUIRED = 2;
    public static final int ERROR_KEYSTORE_UNINITIALIZED = 3;
    public static final int ERROR_INTERNAL_SYSTEM_ERROR = 4;
    public static final int ERROR_PERMISSION_DENIED = 5;
    public static final int ERROR_KEY_DOES_NOT_EXIST = 6;
    public static final int ERROR_KEY_CORRUPTED = 7;
    public static final int ERROR_ID_ATTESTATION_FAILURE = 8;
    public static final int ERROR_ATTESTATION_CHALLENGE_TOO_LARGE = 9;
    public static final int ERROR_KEYMINT_FAILURE = 10;
    public static final int ERROR_KEYSTORE_FAILURE = 11;
    public static final int ERROR_UNIMPLEMENTED = 12;
    public static final int ERROR_INCORRECT_USAGE = 13;
    public static final int ERROR_KEY_NOT_TEMPORALLY_VALID = 14;
    public static final int ERROR_KEY_OPERATION_EXPIRED = 15;
    public static final int ERROR_ATTESTATION_KEYS_UNAVAILABLE = 16;

    public static final int RETRY_NEVER = 1;
    public static final int RETRY_WITH_EXPONENTIAL_BACKOFF = 2;
    public static final int RETRY_WHEN_CONNECTIVITY_AVAILABLE = 3;
    public static final int RETRY_AFTER_NEXT_REBOOT = 4;

    private final int mErrorCode;
    private final int mNumericErrorCode;
    private final int mRetryPolicy;

    public KeyStoreException(int errorCode, String message) {
        this(errorCode, message, ERROR_OTHER, RETRY_NEVER);
    }

    public KeyStoreException(int errorCode, String message, int numericErrorCode, int retryPolicy) {
        super(message);
        mErrorCode = errorCode;
        mNumericErrorCode = numericErrorCode;
        mRetryPolicy = retryPolicy;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    public int getNumericErrorCode() {
        return mNumericErrorCode;
    }

    public boolean isTransientFailure() {
        return mRetryPolicy != RETRY_NEVER;
    }

    public boolean requiresUserAuthentication() {
        return mNumericErrorCode == ERROR_USER_AUTHENTICATION_REQUIRED;
    }

    public boolean isSystemError() {
        return mNumericErrorCode == ERROR_INTERNAL_SYSTEM_ERROR
                || mNumericErrorCode == ERROR_KEYMINT_FAILURE
                || mNumericErrorCode == ERROR_KEYSTORE_FAILURE;
    }

    public int getRetryPolicy() {
        return mRetryPolicy;
    }

    @Override
    public String toString() {
        return super.toString() + " (errorCode=" + mErrorCode
                + ", numericErrorCode=" + mNumericErrorCode
                + ", retryPolicy=" + mRetryPolicy + ")";
    }
}
