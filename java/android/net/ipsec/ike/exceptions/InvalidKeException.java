// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

public final class InvalidKeException extends IkeProtocolException {
    private final int mDhGroup;

    public InvalidKeException(int dhGroup) {
        super(ERROR_TYPE_INVALID_KE_PAYLOAD);
        this.mDhGroup = dhGroup;
    }

    public int getDhGroup() {
        return mDhGroup;
    }
}
