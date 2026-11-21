// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

public class PeerHandle {
    final int peerId;

    PeerHandle(int peerId) {
        this.peerId = peerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PeerHandle)) {
            return false;
        }
        return peerId == ((PeerHandle) o).peerId;
    }

    @Override
    public int hashCode() {
        return peerId;
    }
}
