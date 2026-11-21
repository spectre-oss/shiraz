// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import java.util.Objects;

public class PublishDiscoverySession extends DiscoverySession {
    PublishConfig mConfig;

    PublishDiscoverySession(WifiAwareManager mgr, int clientId, int sessionId) {
        super(mgr, clientId, sessionId);
    }

    public void updatePublish(PublishConfig publishConfig) {
        mConfig = Objects.requireNonNull(publishConfig, "publishConfig");
    }
}
