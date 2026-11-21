// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Objects;

public final class AppSearchResult<ValueType> {
    public static final int RESULT_OK = 0;
    public static final int RESULT_UNKNOWN_ERROR = 1;
    public static final int RESULT_INTERNAL_ERROR = 2;
    public static final int RESULT_INVALID_ARGUMENT = 3;
    public static final int RESULT_IO_ERROR = 4;
    public static final int RESULT_OUT_OF_SPACE = 5;
    public static final int RESULT_NOT_FOUND = 6;
    public static final int RESULT_INVALID_SCHEMA = 7;
    public static final int RESULT_SECURITY_ERROR = 8;
    public static final int RESULT_DENIED = 9;
    public static final int RESULT_RATE_LIMITED = 10;

    private final int mResultCode;
    private final ValueType mResultValue;
    private final String mErrorMessage;

    private AppSearchResult(int resultCode, ValueType resultValue, String errorMessage) {
        mResultCode = resultCode;
        mResultValue = resultValue;
        mErrorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return mResultCode == RESULT_OK;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public ValueType getResultValue() {
        if (!isSuccess()) {
            throw new IllegalStateException("AppSearchResult is a failure: " + this);
        }
        return mResultValue;
    }

    public String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AppSearchResult)) return false;
        AppSearchResult<?> otherResult = (AppSearchResult<?>) other;
        return mResultCode == otherResult.mResultCode
                && Objects.equals(mResultValue, otherResult.mResultValue)
                && Objects.equals(mErrorMessage, otherResult.mErrorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mResultCode), mResultValue, mErrorMessage);
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "[SUCCESS]: " + mResultValue;
        }
        return "[FAILURE(" + mResultCode + ")]: " + mErrorMessage;
    }

    public static <ValueType> AppSearchResult<ValueType> newSuccessfulResult(ValueType value) {
        return new AppSearchResult<ValueType>(RESULT_OK, value, null);
    }

    public static <ValueType> AppSearchResult<ValueType> newFailedResult(
            int resultCode, String errorMessage) {
        if (resultCode == RESULT_OK) {
            throw new IllegalArgumentException("Cannot create failed result with RESULT_OK");
        }
        return new AppSearchResult<ValueType>(resultCode, null, errorMessage);
    }
}
