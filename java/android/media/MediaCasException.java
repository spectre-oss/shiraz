// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public class MediaCasException extends Exception {
    MediaCasException(String detailMessage) {
        super(detailMessage);
    }

    public static final class UnsupportedCasException extends MediaCasException {
        UnsupportedCasException(String detailMessage) {
            super(detailMessage);
        }
    }

    public static final class NotProvisionedException extends MediaCasException {
        NotProvisionedException(String detailMessage) {
            super(detailMessage);
        }
    }

    public static final class DeniedByServerException extends MediaCasException {
        DeniedByServerException(String detailMessage) {
            super(detailMessage);
        }
    }

    public static final class ResourceBusyException extends MediaCasException {
        ResourceBusyException(String detailMessage) {
            super(detailMessage);
        }
    }

    public static final class InsufficientResourceException extends MediaCasException {
        InsufficientResourceException(String detailMessage) {
            super(detailMessage);
        }
    }
}
