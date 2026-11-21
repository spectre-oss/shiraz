// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

import android.net.Network;
import java.util.Objects;

public final class IkeNetworkLostException extends IkeNonProtocolException {
    private final Network mNetwork;

    public IkeNetworkLostException(Network network) {
        super();
        this.mNetwork = Objects.requireNonNull(network, "network");
    }

    public Network getNetwork() {
        return mNetwork;
    }
}
