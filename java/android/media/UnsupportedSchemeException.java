// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class UnsupportedSchemeException extends MediaDrmException {
    public UnsupportedSchemeException(String detailMessage) {
        super(detailMessage);
    }

    UnsupportedSchemeException(String detailMessage, int vendorError, int oemError, int errorContext) {
        super(detailMessage, vendorError, oemError, errorContext);
    }
}
