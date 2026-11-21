// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class NotProvisionedException extends MediaDrmException {
    public NotProvisionedException(String detailMessage) {
        super(detailMessage);
    }

    NotProvisionedException(String detailMessage, int vendorError, int oemError, int errorContext) {
        super(detailMessage, vendorError, oemError, errorContext);
    }
}
