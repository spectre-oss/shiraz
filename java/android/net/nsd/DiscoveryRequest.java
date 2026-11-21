// SPDX-License-Identifier: GPL-3.0-only
package android.net.nsd;

import android.net.Network;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DiscoveryRequest implements Parcelable {
    public static final Creator<DiscoveryRequest> CREATOR =
            new Creator<DiscoveryRequest>() {
                @Override
                public DiscoveryRequest createFromParcel(Parcel in) {
                    String serviceType = in.readString();
                    String subtype = in.readString();
                    Network network = in.readParcelable(Network.class.getClassLoader());
                    return new DiscoveryRequest(serviceType, subtype, network);
                }

                @Override
                public DiscoveryRequest[] newArray(int size) {
                    return new DiscoveryRequest[size];
                }
            };

    private final String mServiceType;
    private final String mSubtype;
    private final Network mNetwork;

    DiscoveryRequest(String serviceType, String subtype, Network network) {
        this.mServiceType = serviceType;
        this.mSubtype = subtype;
        this.mNetwork = network;
    }

    public String getServiceType() {
        return mServiceType;
    }

    public String getSubtype() {
        return mSubtype;
    }

    public Network getNetwork() {
        return mNetwork;
    }

    @Override
    public String toString() {
        return "DiscoveryRequest{serviceType="
                + mServiceType
                + ", subtype="
                + mSubtype
                + ", network="
                + mNetwork
                + "}";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiscoveryRequest)) {
            return false;
        }
        DiscoveryRequest o = (DiscoveryRequest) other;
        return Objects.equals(mServiceType, o.mServiceType)
                && Objects.equals(mSubtype, o.mSubtype)
                && Objects.equals(mNetwork, o.mNetwork);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mServiceType, mSubtype, mNetwork);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mServiceType);
        dest.writeString(mSubtype);
        dest.writeParcelable(mNetwork, flags);
    }

    public static final class Builder {
        private final String mServiceType;
        private String mSubtype;
        private Network mNetwork;

        public Builder(String serviceType) {
            Objects.requireNonNull(serviceType, "serviceType cannot be null");
            if (serviceType.isEmpty()) {
                throw new IllegalArgumentException("serviceType cannot be empty");
            }
            this.mServiceType = serviceType;
        }

        public Builder setSubtype(String subtype) {
            this.mSubtype = subtype;
            return this;
        }

        public Builder setNetwork(Network network) {
            this.mNetwork = network;
            return this;
        }

        public DiscoveryRequest build() {
            return new DiscoveryRequest(mServiceType, mSubtype, mNetwork);
        }
    }
}
