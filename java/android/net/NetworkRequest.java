// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;
import java.util.Set;

/** Capability and transport filter for connectivity callbacks. */
public class NetworkRequest implements Parcelable {
    final NetworkCapabilities capabilities;

    public NetworkRequest(NetworkRequest other) {
        this.capabilities = new NetworkCapabilities(other.capabilities);
    }

    NetworkRequest(NetworkCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public boolean canBeSatisfiedBy(NetworkCapabilities candidate) {
        return candidate != null && candidate.satisfies(capabilities);
    }

    public boolean hasCapability(int capability) {
        return capabilities.hasCapability(capability);
    }

    public boolean hasTransport(int transport) {
        return capabilities.hasTransport(transport);
    }

    public NetworkSpecifier getNetworkSpecifier() {
        return capabilities.getNetworkSpecifier();
    }

    public int[] getCapabilities() {
        return capabilities.getCapabilities();
    }

    public int[] getTransportTypes() {
        return capabilities.getTransportTypesInternal();
    }

    public Set<Integer> getSubscriptionIds() {
        return capabilities.getSubscriptionIds();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        capabilities.writeToParcel(dest, flags);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NetworkRequest)) {
            return false;
        }
        return Objects.equals(capabilities, ((NetworkRequest) obj).capabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(capabilities);
    }

    @Override
    public String toString() {
        return "NetworkRequest [ " + capabilities + " ]";
    }

    public static class Builder {
        private final NetworkCapabilities capabilities;

        public Builder() {
            this.capabilities = new NetworkCapabilities();
        }

        public Builder(NetworkRequest request) {
            Objects.requireNonNull(request, "request");
            this.capabilities = new NetworkCapabilities(request.capabilities);
        }

        public NetworkRequest build() {
            return new NetworkRequest(new NetworkCapabilities(capabilities));
        }

        public Builder addCapability(int capability) {
            capabilities.addCapability(capability);
            return this;
        }

        public Builder removeCapability(int capability) {
            capabilities.removeCapability(capability);
            return this;
        }

        public Builder clearCapabilities() {
            capabilities.clearCapabilities();
            return this;
        }

        public Builder addTransportType(int transport) {
            capabilities.addTransportType(transport);
            return this;
        }

        public Builder removeTransportType(int transport) {
            capabilities.removeTransportType(transport);
            return this;
        }

        @Deprecated
        public Builder setNetworkSpecifier(String networkSpecifier) {
            if (networkSpecifier == null || networkSpecifier.isEmpty()) {
                capabilities.setNetworkSpecifierInternal(null);
            } else {
                capabilities.setNetworkSpecifierInternal(
                        new EthernetNetworkSpecifier(networkSpecifier));
            }
            return this;
        }

        public Builder setNetworkSpecifier(NetworkSpecifier networkSpecifier) {
            capabilities.setNetworkSpecifierInternal(networkSpecifier);
            return this;
        }

        public Builder setSubscriptionIds(Set<Integer> subIds) {
            Objects.requireNonNull(subIds, "subIds");
            capabilities.setSubscriptionIdsInternal(subIds);
            return this;
        }

        public Builder setIncludeOtherUidNetworks(boolean include) {
            return this;
        }
    }

    public static final Parcelable.Creator<NetworkRequest> CREATOR =
            new Parcelable.Creator<NetworkRequest>() {
                @Override
                public NetworkRequest createFromParcel(Parcel in) {
                    return new NetworkRequest(NetworkCapabilities.CREATOR.createFromParcel(in));
                }

                @Override
                public NetworkRequest[] newArray(int size) {
                    return new NetworkRequest[size];
                }
            };
}
