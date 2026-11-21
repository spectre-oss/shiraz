// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

public final class InvalidMajorVersionException extends IkeProtocolException {
    private final byte mVersion;

    public InvalidMajorVersionException(byte version) {
        super(ERROR_TYPE_INVALID_MAJOR_VERSION);
        this.mVersion = version;
    }

    public byte getMajorVersion() {
        return mVersion;
    }
}
