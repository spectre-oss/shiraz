// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.exceptions;

import android.app.appsearch.AppSearchResult;

public class AppSearchException extends Exception {
    private final int mResultCode;

    public AppSearchException(int resultCode) {
        this(resultCode, null, null);
    }

    public AppSearchException(int resultCode, String message) {
        this(resultCode, message, null);
    }

    public AppSearchException(int resultCode, String message, Throwable cause) {
        super(message, cause);
        mResultCode = resultCode;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public <T> AppSearchResult<T> toAppSearchResult() {
        return AppSearchResult.newFailedResult(mResultCode, getMessage());
    }
}
