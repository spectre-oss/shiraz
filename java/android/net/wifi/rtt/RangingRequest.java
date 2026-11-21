// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.PeerHandle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RangingRequest implements Parcelable {
    private static final int MAX_PEERS = 10;
    private static final int DEFAULT_RTT_BURST_SIZE = 8;
    private static final int MIN_RTT_BURST_SIZE = 2;
    private static final int MAX_RTT_BURST_SIZE = 31;

    public static final Creator<RangingRequest> CREATOR =
            new Creator<RangingRequest>() {
                @Override
                public RangingRequest createFromParcel(Parcel in) {
                    int count = in.readInt();
                    List<ResponderConfig> peers = new ArrayList<ResponderConfig>(count);
                    for (int i = 0; i < count; i++) {
                        ResponderConfig peer = in.readTypedObject(ResponderConfig.CREATOR);
                        if (peer != null) {
                            peers.add(peer);
                        }
                    }
                    int burstSize = in.readInt();
                    return new RangingRequest(peers, burstSize);
                }

                @Override
                public RangingRequest[] newArray(int size) {
                    return new RangingRequest[size];
                }
            };

    final List<ResponderConfig> mRttPeers;
    final int mRttBurstSize;

    private RangingRequest(List<ResponderConfig> rttPeers, int rttBurstSize) {
        mRttPeers = Collections.unmodifiableList(new ArrayList<ResponderConfig>(rttPeers));
        mRttBurstSize = rttBurstSize;
    }

    public static int getMaxPeers() {
        return MAX_PEERS;
    }

    public static int getDefaultRttBurstSize() {
        return DEFAULT_RTT_BURST_SIZE;
    }

    public static int getMinRttBurstSize() {
        return MIN_RTT_BURST_SIZE;
    }

    public static int getMaxRttBurstSize() {
        return MAX_RTT_BURST_SIZE;
    }

    public int getRttBurstSize() {
        return mRttBurstSize;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRttPeers.size());
        for (int i = 0; i < mRttPeers.size(); i++) {
            dest.writeTypedObject(mRttPeers.get(i), flags);
        }
        dest.writeInt(mRttBurstSize);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RangingRequest: [mRttBurstSize=");
        sb.append(mRttBurstSize).append(", peers=[");
        for (int i = 0; i < mRttPeers.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mRttPeers.get(i));
        }
        sb.append("]]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RangingRequest)) {
            return false;
        }
        RangingRequest lhs = (RangingRequest) o;
        return mRttPeers.size() == lhs.mRttPeers.size()
                && mRttPeers.containsAll(lhs.mRttPeers)
                && mRttBurstSize == lhs.mRttBurstSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRttPeers, Integer.valueOf(mRttBurstSize));
    }

    public static final class Builder {
        private final List<ResponderConfig> mRttPeers = new ArrayList<ResponderConfig>();
        private int mRttBurstSize = DEFAULT_RTT_BURST_SIZE;

        public Builder() {}

        public Builder setRttBurstSize(int rttBurstSize) {
            if (rttBurstSize < MIN_RTT_BURST_SIZE || rttBurstSize > MAX_RTT_BURST_SIZE) {
                throw new IllegalArgumentException("Invalid RTT burst size " + rttBurstSize);
            }
            mRttBurstSize = rttBurstSize;
            return this;
        }

        public Builder addAccessPoint(ScanResult apInfo) {
            Objects.requireNonNull(apInfo, "Null ScanResult!");
            return addResponder(ResponderConfig.fromScanResult(apInfo));
        }

        public Builder addAccessPoints(List<ScanResult> apInfos) {
            Objects.requireNonNull(apInfos, "Null list of ScanResults!");
            for (int i = 0; i < apInfos.size(); i++) {
                addAccessPoint(apInfos.get(i));
            }
            return this;
        }

        public Builder addResponder(ResponderConfig responder) {
            Objects.requireNonNull(responder, "Null Responder!");
            mRttPeers.add(responder);
            return this;
        }

        public Builder addResponders(List<ResponderConfig> responders) {
            Objects.requireNonNull(responders, "Null list of Responders");
            for (int i = 0; i < responders.size(); i++) {
                addResponder(responders.get(i));
            }
            return this;
        }

        public Builder addNon80211mcCapableAccessPoint(ScanResult apInfo) {
            Objects.requireNonNull(apInfo, "Null ScanResult!");
            if (apInfo.is80211mcResponder()) {
                throw new IllegalArgumentException(
                        "AP supports the 802.11mc protocol. Use addAccessPoint instead.");
            }
            return addResponder(ResponderConfig.fromScanResult(apInfo));
        }

        public Builder addNon80211mcCapableAccessPoints(List<ScanResult> apInfos) {
            Objects.requireNonNull(apInfos, "Null list of ScanResults!");
            for (int i = 0; i < apInfos.size(); i++) {
                addNon80211mcCapableAccessPoint(apInfos.get(i));
            }
            return this;
        }

        public Builder addWifiAwarePeer(MacAddress peerMacAddress) {
            Objects.requireNonNull(peerMacAddress, "Null peer MAC address");
            mRttPeers.add(
                    new ResponderConfig(
                            peerMacAddress,
                            null,
                            ResponderConfig.RESPONDER_AWARE,
                            true,
                            false,
                            0,
                            0,
                            0,
                            0,
                            0));
            return this;
        }

        public Builder addWifiAwarePeer(PeerHandle peerHandle) {
            Objects.requireNonNull(peerHandle, "Null peer handler (identifier)");
            mRttPeers.add(
                    new ResponderConfig(
                            null,
                            peerHandle,
                            ResponderConfig.RESPONDER_AWARE,
                            true,
                            false,
                            0,
                            0,
                            0,
                            0,
                            0));
            return this;
        }

        public RangingRequest build() {
            return new RangingRequest(mRttPeers, mRttBurstSize);
        }
    }
}
