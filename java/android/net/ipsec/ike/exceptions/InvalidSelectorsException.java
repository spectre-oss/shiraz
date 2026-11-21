// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

import java.util.Objects;

public final class InvalidSelectorsException extends IkeProtocolException {
    private final int mIpSecSpi;
    private final byte[] mIpSecPacketInfo;

    public InvalidSelectorsException(int spi, byte[] packetInfo) {
        super(ERROR_TYPE_INVALID_SELECTORS);
        Objects.requireNonNull(packetInfo, "packetInfo is null");
        this.mIpSecSpi = spi;
        this.mIpSecPacketInfo = packetInfo.clone();
    }

    public int getIpSecSpi() {
        return mIpSecSpi;
    }

    public byte[] getIpSecPacketInfo() {
        return mIpSecPacketInfo.clone();
    }
}
