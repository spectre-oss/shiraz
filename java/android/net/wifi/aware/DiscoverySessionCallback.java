// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import java.util.List;

public class DiscoverySessionCallback {
    public DiscoverySessionCallback() {}

    public void onPublishStarted(PublishDiscoverySession session) {}

    public void onSubscribeStarted(SubscribeDiscoverySession session) {}

    public void onSessionConfigUpdated() {}

    public void onSessionConfigFailed() {}

    public void onSessionTerminated() {}

    public void onServiceDiscovered(
            PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {}

    public void onServiceDiscovered(ServiceDiscoveryInfo info) {
        if (info != null) {
            onServiceDiscovered(
                    info.getPeerHandle(), info.getServiceSpecificInfo(), info.getMatchFilters());
        }
    }

    public void onServiceDiscoveredWithinRange(
            PeerHandle peerHandle,
            byte[] serviceSpecificInfo,
            List<byte[]> matchFilter,
            int distanceMm) {}

    public void onServiceDiscoveredWithinRange(ServiceDiscoveryInfo info, int distanceMm) {
        if (info != null) {
            onServiceDiscoveredWithinRange(
                    info.getPeerHandle(),
                    info.getServiceSpecificInfo(),
                    info.getMatchFilters(),
                    distanceMm);
        }
    }

    public void onMessageSendSucceeded(int messageId) {}

    public void onMessageSendFailed(int messageId) {}

    public void onMessageReceived(PeerHandle peerHandle, byte[] message) {}

    public void onServiceLost(PeerHandle peerHandle, int reason) {}

    public void onPairingSetupRequestReceived(PeerHandle peerHandle, int requestId) {}

    public void onPairingSetupSucceeded(PeerHandle peerHandle, String alias) {}

    public void onPairingSetupFailed(PeerHandle peerHandle) {}

    public void onPairingVerificationSucceed(PeerHandle peerHandle, String alias) {}

    public void onPairingVerificationFailed(PeerHandle peerHandle) {}

    public void onBootstrappingSucceeded(PeerHandle peerHandle, int method) {}

    public void onBootstrappingFailed(PeerHandle peerHandle) {}
}
