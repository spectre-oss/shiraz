// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.net.NetworkInfo;
import android.net.TransportInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Network identifiers are unavailable without a host location/SSID permission backend. */
public class WifiInfo implements TransportInfo, Parcelable {
    public static final String LINK_SPEED_UNITS = "Mbps";
    public static final String FREQUENCY_UNITS = "MHz";
    public static final int LINK_SPEED_UNKNOWN = -1;

    public static final int SECURITY_TYPE_UNKNOWN = -1;
    public static final int SECURITY_TYPE_OPEN = 0;
    public static final int SECURITY_TYPE_WEP = 1;
    public static final int SECURITY_TYPE_PSK = 2;
    public static final int SECURITY_TYPE_EAP = 3;
    public static final int SECURITY_TYPE_SAE = 4;
    public static final int SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT = 5;
    public static final int SECURITY_TYPE_OWE = 6;
    public static final int SECURITY_TYPE_WAPI_PSK = 7;
    public static final int SECURITY_TYPE_WAPI_CERT = 8;
    public static final int SECURITY_TYPE_EAP_WPA3_ENTERPRISE = 9;
    public static final int SECURITY_TYPE_OSEN = 10;
    public static final int SECURITY_TYPE_PASSPOINT_R1_R2 = 11;
    public static final int SECURITY_TYPE_PASSPOINT_R3 = 12;
    public static final int SECURITY_TYPE_DPP = 13;

    private static final String DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00";

    private final shiraz.runtime.CapturePermissions audit;
    private byte[] mSsidBytes;
    private String mBssid = DEFAULT_MAC_ADDRESS;
    private int mRssi = -127;
    private int mNetworkId = -1;
    private int mSubscriptionId = -1;
    private int mSecurityType = SECURITY_TYPE_UNKNOWN;
    private SupplicantState mSupplicantState = SupplicantState.UNINITIALIZED;

    WifiInfo(shiraz.runtime.CapturePermissions audit) {
        this.audit = audit;
    }

    private WifiInfo(Builder b) {
        this.audit = null;
        this.mSsidBytes = b.mSsidBytes == null ? null : b.mSsidBytes.clone();
        this.mBssid = b.mBssid;
        this.mRssi = b.mRssi;
        this.mNetworkId = b.mNetworkId;
        this.mSubscriptionId = b.mSubscriptionId;
        this.mSecurityType = b.mSecurityType;
    }

    public String getSSID() {
        if (audit != null) {
            audit.wifiAccess("getSSID:redacted");
            return WifiManager.UNKNOWN_SSID;
        }
        if (mSsidBytes == null) {
            return WifiManager.UNKNOWN_SSID;
        }
        return "\"" + new String(mSsidBytes, StandardCharsets.UTF_8) + "\"";
    }

    public String getBSSID() {
        return mBssid;
    }

    public MacAddress getApMldMacAddress() {
        return null;
    }

    public int getApMloLinkId() {
        return MloLink.INVALID_MLO_LINK_ID;
    }

    public List<MloLink> getAffiliatedMloLinks() {
        return Collections.emptyList();
    }

    public List<MloLink> getAssociatedMloLinks() {
        return Collections.emptyList();
    }

    public int getRssi() {
        return mRssi;
    }

    public int getWifiStandard() {
        return ScanResult.WIFI_STANDARD_UNKNOWN;
    }

    public int getLinkSpeed() {
        return LINK_SPEED_UNKNOWN;
    }

    public int getTxLinkSpeedMbps() {
        return LINK_SPEED_UNKNOWN;
    }

    public int getMaxSupportedTxLinkSpeedMbps() {
        return LINK_SPEED_UNKNOWN;
    }

    public int getRxLinkSpeedMbps() {
        return LINK_SPEED_UNKNOWN;
    }

    public int getMaxSupportedRxLinkSpeedMbps() {
        return LINK_SPEED_UNKNOWN;
    }

    public int getFrequency() {
        return -1;
    }

    public String getMacAddress() {
        return DEFAULT_MAC_ADDRESS;
    }

    public boolean isRestricted() {
        return false;
    }

    public String getPasspointFqdn() {
        return null;
    }

    public String getPasspointProviderFriendlyName() {
        return null;
    }

    public int getSubscriptionId() {
        return mSubscriptionId;
    }

    public int getNetworkId() {
        return mNetworkId;
    }

    public SupplicantState getSupplicantState() {
        return mSupplicantState;
    }

    @Deprecated
    public int getIpAddress() {
        if (audit != null) {
            audit.wifiAccess("getIpAddress:unavailable");
        }
        return 0;
    }

    public boolean getHiddenSSID() {
        return false;
    }

    public static NetworkInfo.DetailedState getDetailedStateOf(SupplicantState suppState) {
        if (suppState == null) {
            return NetworkInfo.DetailedState.IDLE;
        }
        switch (suppState) {
            case DISCONNECTED:
            case INTERFACE_DISABLED:
            case INACTIVE:
            case DORMANT:
                return NetworkInfo.DetailedState.DISCONNECTED;
            case SCANNING:
                return NetworkInfo.DetailedState.SCANNING;
            case AUTHENTICATING:
                return NetworkInfo.DetailedState.AUTHENTICATING;
            case ASSOCIATING:
            case ASSOCIATED:
            case FOUR_WAY_HANDSHAKE:
            case GROUP_HANDSHAKE:
                return NetworkInfo.DetailedState.CONNECTING;
            case COMPLETED:
                return NetworkInfo.DetailedState.OBTAINING_IPADDR;
            case UNINITIALIZED:
                return NetworkInfo.DetailedState.IDLE;
            case INVALID:
            default:
                return NetworkInfo.DetailedState.FAILED;
        }
    }

    public String getPasspointUniqueId() {
        return null;
    }

    public List<ScanResult.InformationElement> getInformationElements() {
        return null;
    }

    public WifiInfo makeCopy(long redactions) {
        Builder copy = new Builder();
        if (mSsidBytes != null && redactions == 0L) {
            copy.setSsid(mSsidBytes);
        }
        if (mBssid != null && redactions == 0L) {
            copy.setBssid(mBssid);
        }
        copy.setRssi(mRssi);
        copy.setNetworkId(mNetworkId);
        copy.setSubscriptionId(mSubscriptionId);
        copy.setCurrentSecurityType(mSecurityType);
        return copy.build();
    }

    public long getApplicableRedactions() {
        return 1L;
    }

    public int getCurrentSecurityType() {
        return mSecurityType;
    }

    @Override
    public String toString() {
        return "SSID: "
                + (mSsidBytes == null
                        ? WifiManager.UNKNOWN_SSID
                        : new String(mSsidBytes, StandardCharsets.UTF_8))
                + ", BSSID: "
                + mBssid
                + ", RSSI: "
                + mRssi;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNetworkId);
        dest.writeInt(mRssi);
        dest.writeString(mBssid);
        dest.writeByteArray(mSsidBytes);
        dest.writeInt(mSubscriptionId);
        dest.writeInt(mSecurityType);
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (!(that instanceof WifiInfo)) {
            return false;
        }
        WifiInfo o = (WifiInfo) that;
        return mRssi == o.mRssi
                && mNetworkId == o.mNetworkId
                && mSubscriptionId == o.mSubscriptionId
                && mSecurityType == o.mSecurityType
                && Objects.equals(mBssid, o.mBssid)
                && java.util.Arrays.equals(mSsidBytes, o.mSsidBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(java.util.Arrays.hashCode(mSsidBytes)),
                mBssid,
                Integer.valueOf(mRssi),
                Integer.valueOf(mNetworkId),
                Integer.valueOf(mSubscriptionId),
                Integer.valueOf(mSecurityType));
    }

    public static final class Builder {
        byte[] mSsidBytes;
        String mBssid = DEFAULT_MAC_ADDRESS;
        int mRssi = -127;
        int mNetworkId = -1;
        int mSubscriptionId = -1;
        int mSecurityType = SECURITY_TYPE_UNKNOWN;

        public Builder() {}

        public Builder setSsid(byte[] ssid) {
            this.mSsidBytes = Objects.requireNonNull(ssid, "ssid").clone();
            return this;
        }

        public Builder setBssid(String bssid) {
            this.mBssid = Objects.requireNonNull(bssid, "bssid");
            return this;
        }

        public Builder setRssi(int rssi) {
            this.mRssi = rssi;
            return this;
        }

        public Builder setNetworkId(int networkId) {
            this.mNetworkId = networkId;
            return this;
        }

        public Builder setSubscriptionId(int subId) {
            this.mSubscriptionId = subId;
            return this;
        }

        public Builder setCurrentSecurityType(int securityType) {
            this.mSecurityType = securityType;
            return this;
        }

        public WifiInfo build() {
            return new WifiInfo(this);
        }
    }
}
