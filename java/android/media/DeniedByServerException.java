// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class DeniedByServerException extends MediaDrmException {
    public DeniedByServerException(String detailMessage) {
        super(detailMessage);
    }

    DeniedByServerException(String detailMessage, int vendorError, int oemError, int errorContext) {
        super(detailMessage, vendorError, oemError, errorContext);
    }
}
