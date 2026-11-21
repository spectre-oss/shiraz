// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

/** Checked exception reported by an {@link IsolatedWorker} implementation. */
public final class IsolatedServiceException extends Exception {
    private final int mErrorCode;

    public IsolatedServiceException(int errorCode) {
        super("IsolatedServiceException: errorCode=" + errorCode);
        this.mErrorCode = errorCode;
    }

    int getErrorCode() {
        return mErrorCode;
    }
}
