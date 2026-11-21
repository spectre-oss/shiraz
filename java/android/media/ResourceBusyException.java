// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class ResourceBusyException extends MediaDrmException {
    public ResourceBusyException(String detailMessage) {
        super(detailMessage);
    }

    ResourceBusyException(String detailMessage, int vendorError, int oemError, int errorContext) {
        super(detailMessage, vendorError, oemError, errorContext);
    }
}
