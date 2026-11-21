// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.os.Bundle;

public final class RequestSurfacePackageException extends Exception {
    private final int mRequestSurfacePackageErrorCode;
    private final Bundle mExtraErrorInformation;

    public RequestSurfacePackageException(int requestSurfacePackageErrorCode, String message) {
        this(requestSurfacePackageErrorCode, message, null, new Bundle());
    }

    public RequestSurfacePackageException(
            int requestSurfacePackageErrorCode, String message, Throwable cause) {
        this(requestSurfacePackageErrorCode, message, cause, new Bundle());
    }

    public RequestSurfacePackageException(
            int requestSurfacePackageErrorCode,
            String message,
            Throwable cause,
            Bundle extraErrorInformation) {
        super(message, cause);
        mRequestSurfacePackageErrorCode = requestSurfacePackageErrorCode;
        mExtraErrorInformation = extraErrorInformation != null ? extraErrorInformation : new Bundle();
    }

    public int getRequestSurfacePackageErrorCode() {
        return mRequestSurfacePackageErrorCode;
    }

    public Bundle getExtraErrorInformation() {
        return mExtraErrorInformation;
    }
}
