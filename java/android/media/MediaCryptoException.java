// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class MediaCryptoException extends Exception implements MediaDrmThrowable {
    private final int mVendorError;
    private final int mOemError;
    private final int mErrorContext;

    public MediaCryptoException(String detailMessage) {
        this(detailMessage, 0, 0, 0);
    }

    MediaCryptoException(String detailMessage, int vendorError, int oemError, int errorContext) {
        super(detailMessage);
        mVendorError = vendorError;
        mOemError = oemError;
        mErrorContext = errorContext;
    }

    @Override
    public int getVendorError() {
        return mVendorError;
    }

    @Override
    public int getOemError() {
        return mOemError;
    }

    @Override
    public int getErrorContext() {
        return mErrorContext;
    }
}
