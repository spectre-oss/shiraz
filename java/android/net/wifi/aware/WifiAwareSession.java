// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.NetworkSpecifier;
import android.os.Handler;
import java.util.Objects;

public class WifiAwareSession implements AutoCloseable {
    private final WifiAwareManager mMgr;
    private final int mClientId;
    private boolean mTerminated = false;

    WifiAwareSession(WifiAwareManager mgr, int clientId) {
        mMgr = mgr;
        mClientId = clientId;
    }

    @Override
    public void close() {
        mTerminated = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!mTerminated) {
                close();
            }
        } finally {
            super.finalize();
        }
    }

    public void publish(
            PublishConfig publishConfig, final DiscoverySessionCallback callback, Handler handler) {
        Objects.requireNonNull(publishConfig, "publishConfig");
        Objects.requireNonNull(callback, "callback");
        if (handler != null) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            callback.onSessionConfigFailed();
                        }
                    });
        } else {
            callback.onSessionConfigFailed();
        }
    }

    public void subscribe(
            SubscribeConfig subscribeConfig,
            final DiscoverySessionCallback callback,
            Handler handler) {
        Objects.requireNonNull(subscribeConfig, "subscribeConfig");
        Objects.requireNonNull(callback, "callback");
        if (handler != null) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            callback.onSessionConfigFailed();
                        }
                    });
        } else {
            callback.onSessionConfigFailed();
        }
    }

    public NetworkSpecifier createNetworkSpecifierOpen(int role, byte[] peer) {
        if (role != WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
                && role != WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER) {
            throw new IllegalArgumentException("Invalid role");
        }
        int type =
                (peer == null)
                        ? WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER
                        : WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB;
        return new WifiAwareNetworkSpecifier(
                type, role, mClientId, 0, 0, peer, null, 0, -1, 0, false);
    }

    public NetworkSpecifier createNetworkSpecifierPassphrase(
            int role, byte[] peer, String passphrase) {
        Objects.requireNonNull(passphrase, "passphrase");
        if (role != WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
                && role != WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER) {
            throw new IllegalArgumentException("Invalid role");
        }
        int type =
                (peer == null)
                        ? WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER
                        : WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_OOB;
        WifiAwareDataPathSecurityConfig sec =
                new WifiAwareDataPathSecurityConfig(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                        null,
                        passphrase,
                        null);
        return new WifiAwareNetworkSpecifier(
                type, role, mClientId, 0, 0, peer, sec, 0, -1, 0, false);
    }
}
