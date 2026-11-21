// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Capability and transport flags reported for the host default network. */
public final class NetworkCapabilities implements Parcelable {
    public static final int NET_CAPABILITY_MMS = 0;
    public static final int NET_CAPABILITY_SUPL = 1;
    public static final int NET_CAPABILITY_DUN = 2;
    public static final int NET_CAPABILITY_FOTA = 3;
    public static final int NET_CAPABILITY_IMS = 4;
    public static final int NET_CAPABILITY_CBS = 5;
    public static final int NET_CAPABILITY_WIFI_P2P = 6;
    public static final int NET_CAPABILITY_IA = 7;
    public static final int NET_CAPABILITY_RCS = 8;
    public static final int NET_CAPABILITY_XCAP = 9;
    public static final int NET_CAPABILITY_EIMS = 10;
    public static final int NET_CAPABILITY_NOT_METERED = 11;
    public static final int NET_CAPABILITY_INTERNET = 12;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;
    public static final int NET_CAPABILITY_TRUSTED = 14;
    public static final int NET_CAPABILITY_NOT_VPN = 15;
    public static final int NET_CAPABILITY_VALIDATED = 16;
    public static final int NET_CAPABILITY_CAPTIVE_PORTAL = 17;
    public static final int NET_CAPABILITY_NOT_ROAMING = 18;
    public static final int NET_CAPABILITY_FOREGROUND = 19;
    public static final int NET_CAPABILITY_NOT_CONGESTED = 20;
    public static final int NET_CAPABILITY_NOT_SUSPENDED = 21;
    public static final int NET_CAPABILITY_MCX = 23;
    public static final int NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25;
    public static final int NET_CAPABILITY_ENTERPRISE = 29;
    public static final int NET_CAPABILITY_HEAD_UNIT = 32;
    public static final int NET_CAPABILITY_MMTEL = 33;
    public static final int NET_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35;
    public static final int NET_CAPABILITY_LOCAL_NETWORK = 36;

    public static final int NET_ENTERPRISE_ID_1 = 1;
    public static final int NET_ENTERPRISE_ID_2 = 2;
    public static final int NET_ENTERPRISE_ID_3 = 3;
    public static final int NET_ENTERPRISE_ID_4 = 4;
    public static final int NET_ENTERPRISE_ID_5 = 5;

    public static final int SIGNAL_STRENGTH_UNSPECIFIED = Integer.MIN_VALUE;

    public static final int TRANSPORT_CELLULAR = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;
    public static final int TRANSPORT_WIFI_AWARE = 5;
    public static final int TRANSPORT_LOWPAN = 6;
    public static final int TRANSPORT_USB = 8;
    public static final int TRANSPORT_THREAD = 9;
    public static final int TRANSPORT_SATELLITE = 10;

    private final BitSet capabilities = new BitSet();
    private final BitSet transports = new BitSet();
    private final BitSet enterpriseIds = new BitSet();
    private NetworkSpecifier specifier;
    private TransportInfo transportInfo;
    private Set<Integer> subscriptionIds = Collections.emptySet();
    private int ownerUid = -1;

    public NetworkCapabilities() {
    }

    public NetworkCapabilities(NetworkCapabilities other) {
        if (other != null) {
            capabilities.or(other.capabilities);
            transports.or(other.transports);
            enterpriseIds.or(other.enterpriseIds);
            specifier = other.specifier;
            transportInfo = other.transportInfo;
            subscriptionIds = other.subscriptionIds;
            ownerUid = other.ownerUid;
        }
    }

    /** A usable host route is Internet-configured, not proof of validation or cost. */
    public static NetworkCapabilities shirazHost(int transport) {
        NetworkCapabilities value = new NetworkCapabilities();
        value.capabilities.set(NET_CAPABILITY_INTERNET);
        if (transport >= 0) {
            value.transports.set(transport);
        }
        return value;
    }

    boolean satisfies(NetworkCapabilities required) {
        BitSet missing = (BitSet) required.capabilities.clone();
        missing.andNot(capabilities);
        if (!missing.isEmpty()) {
            return false;
        }
        if (!required.transports.isEmpty() && !required.transports.intersects(transports)) {
            return false;
        }
        if (required.specifier != null && !Objects.equals(required.specifier, specifier)) {
            return false;
        }
        return true;
    }

    boolean shirazSame(NetworkCapabilities other) {
        return equals(other);
    }

    public NetworkCapabilities addCapability(int capability) {
        if (capability >= 0) {
            capabilities.set(capability);
        }
        return this;
    }

    public NetworkCapabilities removeCapability(int capability) {
        if (capability >= 0) {
            capabilities.clear(capability);
        }
        return this;
    }

    void clearCapabilities() {
        capabilities.clear();
    }

    public NetworkCapabilities addTransportType(int transport) {
        if (transport >= 0) {
            transports.set(transport);
        }
        return this;
    }

    void removeTransportType(int transport) {
        if (transport >= 0) {
            transports.clear(transport);
        }
    }

    void setNetworkSpecifierInternal(NetworkSpecifier spec) {
        this.specifier = spec;
    }

    void setSubscriptionIdsInternal(Set<Integer> subIds) {
        if (subIds == null || subIds.isEmpty()) {
            this.subscriptionIds = Collections.emptySet();
        } else {
            this.subscriptionIds =
                    Collections.unmodifiableSet(new LinkedHashSet<Integer>(subIds));
        }
    }

    public int[] getEnterpriseIds() {
        return toIntArray(enterpriseIds);
    }

    public boolean hasEnterpriseId(int enterpriseId) {
        return enterpriseId >= 0 && enterpriseIds.get(enterpriseId);
    }

    public int[] getCapabilities() {
        return toIntArray(capabilities);
    }

    int[] getTransportTypesInternal() {
        return toIntArray(transports);
    }

    private static int[] toIntArray(BitSet bs) {
        int[] res = new int[bs.cardinality()];
        int idx = 0;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            res[idx++] = i;
        }
        return res;
    }

    public boolean hasCapability(int capability) {
        return capability >= 0 && capabilities.get(capability);
    }

    public boolean hasTransport(int transport) {
        return transport >= 0 && transports.get(transport);
    }

    public int getOwnerUid() {
        return ownerUid;
    }

    public int getLinkDownstreamBandwidthKbps() {
        return 0;
    }

    public int getLinkUpstreamBandwidthKbps() {
        return 0;
    }

    public NetworkSpecifier getNetworkSpecifier() {
        return specifier;
    }

    public TransportInfo getTransportInfo() {
        return transportInfo;
    }

    public int getSignalStrength() {
        return SIGNAL_STRENGTH_UNSPECIFIED;
    }

    public Set<Integer> getSubscriptionIds() {
        return subscriptionIds;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NetworkCapabilities)) {
            return false;
        }
        NetworkCapabilities other = (NetworkCapabilities) obj;
        return capabilities.equals(other.capabilities)
                && transports.equals(other.transports)
                && enterpriseIds.equals(other.enterpriseIds)
                && ownerUid == other.ownerUid
                && Objects.equals(specifier, other.specifier)
                && Objects.equals(transportInfo, other.transportInfo)
                && Objects.equals(subscriptionIds, other.subscriptionIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                capabilities,
                transports,
                enterpriseIds,
                Integer.valueOf(ownerUid),
                specifier,
                transportInfo,
                subscriptionIds);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(capabilities.toByteArray());
        dest.writeByteArray(transports.toByteArray());
        dest.writeByteArray(enterpriseIds.toByteArray());
        dest.writeInt(ownerUid);
        dest.writeInt(subscriptionIds.size());
        for (Integer subId : subscriptionIds) {
            dest.writeInt(subId.intValue());
        }
    }

    @Override
    public String toString() {
        return "[ Transports: " + transports + " Capabilities: " + capabilities + " ]";
    }

    public static final Parcelable.Creator<NetworkCapabilities> CREATOR =
            new Parcelable.Creator<NetworkCapabilities>() {
                @Override
                public NetworkCapabilities createFromParcel(Parcel in) {
                    NetworkCapabilities nc = new NetworkCapabilities();
                    byte[] capBytes = in.createByteArray();
                    if (capBytes != null) {
                        nc.capabilities.or(BitSet.valueOf(capBytes));
                    }
                    byte[] trBytes = in.createByteArray();
                    if (trBytes != null) {
                        nc.transports.or(BitSet.valueOf(trBytes));
                    }
                    byte[] entBytes = in.createByteArray();
                    if (entBytes != null) {
                        nc.enterpriseIds.or(BitSet.valueOf(entBytes));
                    }
                    nc.ownerUid = in.readInt();
                    int subCount = in.readInt();
                    if (subCount > 0) {
                        Set<Integer> subs = new LinkedHashSet<Integer>(subCount);
                        for (int i = 0; i < subCount; i++) {
                            subs.add(Integer.valueOf(in.readInt()));
                        }
                        nc.subscriptionIds = Collections.unmodifiableSet(subs);
                    }
                    return nc;
                }

                @Override
                public NetworkCapabilities[] newArray(int size) {
                    return new NetworkCapabilities[size];
                }
            };
}
