// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import java.util.Objects;

public class SubscribeDiscoverySession extends DiscoverySession {
    SubscribeConfig mConfig;

    SubscribeDiscoverySession(WifiAwareManager mgr, int clientId, int sessionId) {
        super(mgr, clientId, sessionId);
    }

    public void updateSubscribe(SubscribeConfig subscribeConfig) {
        mConfig = Objects.requireNonNull(subscribeConfig, "subscribeConfig");
    }
}
