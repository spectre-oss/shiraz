// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

public final class NfcEvent {
    public final NfcAdapter nfcAdapter;
    public final int peerLlcpMajorVersion;
    public final int peerLlcpMinorVersion;

    NfcEvent(NfcAdapter nfcAdapter, byte peerLlcpVersion) {
        this.nfcAdapter = nfcAdapter;
        this.peerLlcpMajorVersion = (peerLlcpVersion & 0xF0) >> 4;
        this.peerLlcpMinorVersion = peerLlcpVersion & 0x0F;
    }
}
