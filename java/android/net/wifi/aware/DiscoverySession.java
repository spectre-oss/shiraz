// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.NetworkSpecifier;
import java.util.Objects;

public class DiscoverySession implements AutoCloseable {
    final WifiAwareManager mMgr;
    final int mClientId;
    final int mSessionId;
    boolean mTerminated = false;

    DiscoverySession(WifiAwareManager mgr, int clientId, int sessionId) {
        mMgr = mgr;
        mClientId = clientId;
        mSessionId = sessionId;
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

    public void sendMessage(PeerHandle peerHandle, int messageId, byte[] message) {
        Objects.requireNonNull(peerHandle, "peerHandle");
    }

    public void initiatePairingRequest(
            PeerHandle peerHandle, String peerDeviceAlias, int cipherSuite, String password) {
        Objects.requireNonNull(peerHandle, "peerHandle");
        Objects.requireNonNull(peerDeviceAlias, "peerDeviceAlias");
    }

    public void acceptPairingRequest(
            int requestId,
            PeerHandle peerHandle,
            String peerDeviceAlias,
            int cipherSuite,
            String password) {
        Objects.requireNonNull(peerHandle, "peerHandle");
        Objects.requireNonNull(peerDeviceAlias, "peerDeviceAlias");
    }

    public void rejectPairingRequest(int requestId, PeerHandle peerHandle) {
        Objects.requireNonNull(peerHandle, "peerHandle");
    }

    public void initiateBootstrappingRequest(PeerHandle peerHandle, int method) {
        Objects.requireNonNull(peerHandle, "peerHandle");
    }

    @Deprecated
    public NetworkSpecifier createNetworkSpecifierOpen(PeerHandle peerHandle) {
        Objects.requireNonNull(peerHandle, "peerHandle");
        int role =
                (this instanceof SubscribeDiscoverySession)
                        ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
                        : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER;
        return new WifiAwareNetworkSpecifier(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB,
                role,
                mClientId,
                mSessionId,
                peerHandle.peerId,
                null,
                null,
                0,
                -1,
                0,
                false);
    }

    @Deprecated
    public NetworkSpecifier createNetworkSpecifierPassphrase(
            PeerHandle peerHandle, String passphrase) {
        Objects.requireNonNull(peerHandle, "peerHandle");
        Objects.requireNonNull(passphrase, "passphrase");
        int role =
                (this instanceof SubscribeDiscoverySession)
                        ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
                        : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER;
        WifiAwareDataPathSecurityConfig sec =
                new WifiAwareDataPathSecurityConfig(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                        null,
                        passphrase,
                        null);
        return new WifiAwareNetworkSpecifier(
                WifiAwareNetworkSpecifier.NETWORK_SPECIFIER_TYPE_IB,
                role,
                mClientId,
                mSessionId,
                peerHandle.peerId,
                null,
                sec,
                0,
                -1,
                0,
                false);
    }
}
