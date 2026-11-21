// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.NetworkSpecifier;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class WifiAwareNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    static final int NETWORK_SPECIFIER_TYPE_IB = 0;
    static final int NETWORK_SPECIFIER_TYPE_IB_ANY_PEER = 1;
    static final int NETWORK_SPECIFIER_TYPE_OOB = 2;
    static final int NETWORK_SPECIFIER_TYPE_OOB_ANY_PEER = 3;

    public static final Creator<WifiAwareNetworkSpecifier> CREATOR =
            new Creator<WifiAwareNetworkSpecifier>() {
                @Override
                public WifiAwareNetworkSpecifier createFromParcel(Parcel in) {
                    int type = in.readInt();
                    int role = in.readInt();
                    int clientId = in.readInt();
                    int sessionId = in.readInt();
                    int peerId = in.readInt();
                    byte[] peerMac = in.createByteArray();
                    WifiAwareDataPathSecurityConfig sec =
                            in.readTypedObject(WifiAwareDataPathSecurityConfig.CREATOR);
                    int port = in.readInt();
                    int transportProtocol = in.readInt();
                    int channelMhz = in.readInt();
                    boolean forceChannel = in.readInt() != 0;
                    return new WifiAwareNetworkSpecifier(
                            type,
                            role,
                            clientId,
                            sessionId,
                            peerId,
                            peerMac,
                            sec,
                            port,
                            transportProtocol,
                            channelMhz,
                            forceChannel);
                }

                @Override
                public WifiAwareNetworkSpecifier[] newArray(int size) {
                    return new WifiAwareNetworkSpecifier[size];
                }
            };

    final int mType;
    final int mRole;
    final int mClientId;
    final int mSessionId;
    final int mPeerId;
    final byte[] mPeerMac;
    final WifiAwareDataPathSecurityConfig mSecurityConfig;
    final int mPort;
    final int mTransportProtocol;
    final int mChannelInMhz;
    final boolean mForceChannel;

    WifiAwareNetworkSpecifier(
            int type,
            int role,
            int clientId,
            int sessionId,
            int peerId,
            byte[] peerMac,
            WifiAwareDataPathSecurityConfig securityConfig,
            int port,
            int transportProtocol,
            int channelInMhz,
            boolean forceChannel) {
        mType = type;
        mRole = role;
        mClientId = clientId;
        mSessionId = sessionId;
        mPeerId = peerId;
        mPeerMac = peerMac == null ? null : peerMac.clone();
        mSecurityConfig = securityConfig;
        mPort = port;
        mTransportProtocol = transportProtocol;
        mChannelInMhz = channelInMhz;
        mForceChannel = forceChannel;
    }

    public int getChannelFrequencyMhz() {
        return mChannelInMhz;
    }

    public boolean isChannelRequired() {
        return mForceChannel;
    }

    public WifiAwareDataPathSecurityConfig getWifiAwareDataPathSecurityConfig() {
        return mSecurityConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mRole);
        dest.writeInt(mClientId);
        dest.writeInt(mSessionId);
        dest.writeInt(mPeerId);
        dest.writeByteArray(mPeerMac);
        dest.writeTypedObject(mSecurityConfig, flags);
        dest.writeInt(mPort);
        dest.writeInt(mTransportProtocol);
        dest.writeInt(mChannelInMhz);
        dest.writeInt(mForceChannel ? 1 : 0);
    }

    public boolean canBeSatisfiedBy(NetworkSpecifier other) {
        return equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mType),
                Integer.valueOf(mRole),
                Integer.valueOf(mClientId),
                Integer.valueOf(mSessionId),
                Integer.valueOf(mPeerId),
                Integer.valueOf(Arrays.hashCode(mPeerMac)),
                mSecurityConfig,
                Integer.valueOf(mPort),
                Integer.valueOf(mTransportProtocol),
                Integer.valueOf(mChannelInMhz),
                Boolean.valueOf(mForceChannel));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiAwareNetworkSpecifier)) {
            return false;
        }
        WifiAwareNetworkSpecifier lhs = (WifiAwareNetworkSpecifier) obj;
        return mType == lhs.mType
                && mRole == lhs.mRole
                && mClientId == lhs.mClientId
                && mSessionId == lhs.mSessionId
                && mPeerId == lhs.mPeerId
                && Arrays.equals(mPeerMac, lhs.mPeerMac)
                && Objects.equals(mSecurityConfig, lhs.mSecurityConfig)
                && mPort == lhs.mPort
                && mTransportProtocol == lhs.mTransportProtocol
                && mChannelInMhz == lhs.mChannelInMhz
                && mForceChannel == lhs.mForceChannel;
    }

    @Override
    public String toString() {
        return "WifiAwareNetworkSpecifier [type="
                + mType
                + ", role="
                + mRole
                + ", clientId="
                + mClientId
                + ", sessionId="
                + mSessionId
                + ", peerId="
                + mPeerId
                + ", port="
                + mPort
                + ", transportProtocol="
                + mTransportProtocol
                + ", channel="
                + mChannelInMhz
                + ", forceChannel="
                + mForceChannel
                + "]";
    }

    public static final class Builder {
        private final DiscoverySession mDiscoverySession;
        private final PeerHandle mPeerHandle;
        private String mPskPassphrase;
        private byte[] mPmk;
        private int mPort = 0;
        private int mTransportProtocol = -1;
        private int mChannelInMhz = 0;
        private boolean mForceChannel = false;
        private WifiAwareDataPathSecurityConfig mSecurityConfig;

        public Builder(DiscoverySession discoverySession, PeerHandle peerHandle) {
            mDiscoverySession = Objects.requireNonNull(discoverySession, "discoverySession");
            mPeerHandle = Objects.requireNonNull(peerHandle, "peerHandle");
        }

        public Builder(PublishDiscoverySession publishDiscoverySession) {
            mDiscoverySession =
                    Objects.requireNonNull(publishDiscoverySession, "publishDiscoverySession");
            mPeerHandle = null;
        }

        public Builder setPskPassphrase(String pskPassphrase) {
            mPskPassphrase = Objects.requireNonNull(pskPassphrase, "pskPassphrase");
            return this;
        }

        public Builder setPmk(byte[] pmk) {
            mPmk = Objects.requireNonNull(pmk, "pmk").clone();
            return this;
        }

        public Builder setPort(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("The port must be a positive value (<= 65535)");
            }
            mPort = port;
            return this;
        }

        public Builder setTransportProtocol(int transportProtocol) {
            if (transportProtocol < 0 || transportProtocol > 255) {
                throw new IllegalArgumentException(
                        "The transport protocol must be in range [0, 255]");
            }
            mTransportProtocol = transportProtocol;
            return this;
        }

        public Builder setChannelFrequencyMhz(int channelInMhz, boolean required) {
            mChannelInMhz = channelInMhz;
            mForceChannel = required;
            return this;
        }

        public Builder setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig securityConfig) {
            mSecurityConfig = Objects.requireNonNull(securityConfig, "securityConfig");
            return this;
        }

        public WifiAwareNetworkSpecifier build() {
            int role =
                    (mDiscoverySession instanceof SubscribeDiscoverySession)
                            ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
                            : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER;
            int type =
                    (mPeerHandle == null)
                            ? NETWORK_SPECIFIER_TYPE_IB_ANY_PEER
                            : NETWORK_SPECIFIER_TYPE_IB;
            WifiAwareDataPathSecurityConfig sec = mSecurityConfig;
            if (sec == null && (mPskPassphrase != null || mPmk != null)) {
                sec =
                        new WifiAwareDataPathSecurityConfig(
                                Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                                mPmk,
                                mPskPassphrase,
                                null);
            }
            return new WifiAwareNetworkSpecifier(
                    type,
                    role,
                    mDiscoverySession.mClientId,
                    mDiscoverySession.mSessionId,
                    mPeerHandle == null ? 0 : mPeerHandle.peerId,
                    null,
                    sec,
                    mPort,
                    mTransportProtocol,
                    mChannelInMhz,
                    mForceChannel);
        }
    }
}
