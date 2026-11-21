// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServiceDiscoveryInfo {
    private final PeerHandle mPeerHandle;
    private final int mPeerCipherSuite;
    private final byte[] mServiceSpecificInfo;
    private final List<byte[]> mMatchFilters;
    private final byte[] mScid;
    private final String mPairedAlias;
    private final AwarePairingConfig mPairingConfig;

    ServiceDiscoveryInfo(
            PeerHandle peerHandle,
            int peerCipherSuite,
            byte[] serviceSpecificInfo,
            List<byte[]> matchFilters,
            byte[] scid,
            String pairedAlias,
            AwarePairingConfig pairingConfig) {
        this.mPeerHandle = peerHandle;
        this.mPeerCipherSuite = peerCipherSuite;
        this.mServiceSpecificInfo =
                serviceSpecificInfo == null ? null : serviceSpecificInfo.clone();
        this.mMatchFilters =
                matchFilters == null
                        ? Collections.<byte[]>emptyList()
                        : Collections.unmodifiableList(new ArrayList<byte[]>(matchFilters));
        this.mScid = scid == null ? null : scid.clone();
        this.mPairedAlias = pairedAlias;
        this.mPairingConfig = pairingConfig;
    }

    public PeerHandle getPeerHandle() {
        return mPeerHandle;
    }

    public List<byte[]> getMatchFilters() {
        return mMatchFilters;
    }

    public byte[] getServiceSpecificInfo() {
        return mServiceSpecificInfo == null ? null : mServiceSpecificInfo.clone();
    }

    public byte[] getScid() {
        return mScid == null ? null : mScid.clone();
    }

    public int getPeerCipherSuite() {
        return mPeerCipherSuite;
    }

    public String getPairedAlias() {
        return mPairedAlias;
    }

    public AwarePairingConfig getPairingConfig() {
        return mPairingConfig;
    }
}
