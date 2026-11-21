// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.Network;
import java.net.InetAddress;
import java.util.Objects;

public final class IkeSessionConnectionInfo {
    private final InetAddress mLocalAddress;
    private final InetAddress mRemoteAddress;
    private final Network mNetwork;

    public IkeSessionConnectionInfo(
            InetAddress localAddress, InetAddress remoteAddress, Network network) {
        this.mLocalAddress = Objects.requireNonNull(localAddress, "localAddress was null");
        this.mRemoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress was null");
        this.mNetwork = Objects.requireNonNull(network, "network was null");
    }

    public InetAddress getLocalAddress() {
        return mLocalAddress;
    }

    public InetAddress getRemoteAddress() {
        return mRemoteAddress;
    }

    public Network getNetwork() {
        return mNetwork;
    }
}
