// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

/** Exception reported by {@link OnDevicePersonalizationManager}. */
public class OnDevicePersonalizationException extends Exception {
    public static final int ERROR_ISOLATED_SERVICE_FAILED = 1;
    public static final int ERROR_PERSONALIZATION_DISABLED = 2;

    private final int mErrorCode;

    OnDevicePersonalizationException(int errorCode) {
        super("OnDevicePersonalizationException: errorCode=" + errorCode);
        this.mErrorCode = errorCode;
    }

    OnDevicePersonalizationException(int errorCode, String message) {
        super(message);
        this.mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }
}
