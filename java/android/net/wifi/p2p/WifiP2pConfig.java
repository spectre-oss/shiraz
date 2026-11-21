// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.net.MacAddress;
import android.net.wifi.WpsInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class WifiP2pConfig implements Parcelable {
    public static final int GROUP_OWNER_INTENT_MIN = 0;
    public static final int GROUP_OWNER_INTENT_MAX = 15;
    public static final int GROUP_OWNER_INTENT_AUTO = -1;

    public static final int GROUP_OWNER_BAND_AUTO = 0;
    public static final int GROUP_OWNER_BAND_2GHZ = 1;
    public static final int GROUP_OWNER_BAND_5GHZ = 2;

    public static final int GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP = 0;
    public static final int GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL = 1;

    public static final Creator<WifiP2pConfig> CREATOR =
            new Creator<WifiP2pConfig>() {
                @Override
                public WifiP2pConfig createFromParcel(Parcel in) {
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = in.readString();
                    config.wps = in.readTypedObject(WpsInfo.CREATOR);
                    config.groupOwnerIntent = in.readInt();
                    config.mNetId = in.readInt();
                    config.mNetworkName = in.readString();
                    config.mPassphrase = in.readString();
                    config.mGroupOperatingBand = in.readInt();
                    config.mGroupOperatingFrequency = in.readInt();
                    config.mGroupClientIpProvisioningMode = in.readInt();
                    return config;
                }

                @Override
                public WifiP2pConfig[] newArray(int size) {
                    return new WifiP2pConfig[size];
                }
            };

    public String deviceAddress = "";
    public WpsInfo wps;
    public int groupOwnerIntent = GROUP_OWNER_INTENT_AUTO;

    int mNetId = WifiP2pGroup.NETWORK_ID_PERSISTENT;
    String mNetworkName = "";
    String mPassphrase = "";
    int mGroupOperatingBand = GROUP_OWNER_BAND_AUTO;
    int mGroupOperatingFrequency = 0;
    int mGroupClientIpProvisioningMode = GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;

    public WifiP2pConfig() {
        wps = new WpsInfo();
        wps.setup = WpsInfo.PBC;
    }

    public WifiP2pConfig(WifiP2pConfig source) {
        if (source != null) {
            deviceAddress = source.deviceAddress;
            wps = new WpsInfo(source.wps);
            groupOwnerIntent = source.groupOwnerIntent;
            mNetId = source.mNetId;
            mNetworkName = source.mNetworkName;
            mPassphrase = source.mPassphrase;
            mGroupOperatingBand = source.mGroupOperatingBand;
            mGroupOperatingFrequency = source.mGroupOperatingFrequency;
            mGroupClientIpProvisioningMode = source.mGroupClientIpProvisioningMode;
        }
    }

    public String getNetworkName() {
        return mNetworkName;
    }

    public String getPassphrase() {
        return mPassphrase;
    }

    public int getGroupOwnerBand() {
        if (mGroupOperatingFrequency > 0) {
            return mGroupOperatingFrequency;
        }
        return mGroupOperatingBand;
    }

    public int getNetworkId() {
        return mNetId;
    }

    public int getGroupClientIpProvisioningMode() {
        return mGroupClientIpProvisioningMode;
    }

    @Override
    public String toString() {
        return "\n address: "
                + deviceAddress
                + "\n wps: "
                + wps
                + "\n groupOwnerIntent: "
                + groupOwnerIntent
                + "\n netId: "
                + mNetId
                + "\n networkName: "
                + mNetworkName
                + "\n groupOperatingBand: "
                + mGroupOperatingBand
                + "\n groupOperatingFrequency: "
                + mGroupOperatingFrequency
                + "\n groupClientIpProvisioningMode: "
                + mGroupClientIpProvisioningMode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceAddress);
        dest.writeTypedObject(wps, flags);
        dest.writeInt(groupOwnerIntent);
        dest.writeInt(mNetId);
        dest.writeString(mNetworkName);
        dest.writeString(mPassphrase);
        dest.writeInt(mGroupOperatingBand);
        dest.writeInt(mGroupOperatingFrequency);
        dest.writeInt(mGroupClientIpProvisioningMode);
    }

    public static final class Builder {
        private MacAddress mDeviceAddress;
        private String mNetworkName = "";
        private String mPassphrase = "";
        private int mGroupOperatingBand = GROUP_OWNER_BAND_AUTO;
        private int mGroupOperatingFrequency = 0;
        private int mNetId = WifiP2pGroup.NETWORK_ID_PERSISTENT;
        private int mGroupClientIpProvisioningMode = GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;

        public Builder() {}

        public Builder setDeviceAddress(MacAddress deviceAddress) {
            mDeviceAddress = deviceAddress;
            return this;
        }

        public Builder setNetworkName(String networkName) {
            Objects.requireNonNull(networkName, "network name must be non-null");
            if (networkName.isEmpty() || !networkName.startsWith("DIRECT-")) {
                throw new IllegalArgumentException(
                        "network name must start with 'DIRECT-'");
            }
            mNetworkName = networkName;
            return this;
        }

        public Builder setPassphrase(String passphrase) {
            Objects.requireNonNull(passphrase, "passphrase must be non-null");
            if (passphrase.length() < 8 || passphrase.length() > 63) {
                throw new IllegalArgumentException(
                        "The length of a passphrase must be between 8 and 63.");
            }
            mPassphrase = passphrase;
            return this;
        }

        public Builder setGroupOperatingBand(int band) {
            if (band != GROUP_OWNER_BAND_AUTO
                    && band != GROUP_OWNER_BAND_2GHZ
                    && band != GROUP_OWNER_BAND_5GHZ) {
                throw new IllegalArgumentException("Invalid constant for the band");
            }
            mGroupOperatingBand = band;
            mGroupOperatingFrequency = 0;
            return this;
        }

        public Builder setGroupOperatingFrequency(int frequency) {
            if (frequency <= 0) {
                throw new IllegalArgumentException("Invalid frequency");
            }
            mGroupOperatingFrequency = frequency;
            mGroupOperatingBand = GROUP_OWNER_BAND_AUTO;
            return this;
        }

        public Builder enablePersistentMode(boolean persistent) {
            mNetId =
                    persistent
                            ? WifiP2pGroup.NETWORK_ID_PERSISTENT
                            : WifiP2pGroup.NETWORK_ID_TEMPORARY;
            return this;
        }

        public Builder setGroupClientIpProvisioningMode(int groupClientIpProvisioningMode) {
            if (groupClientIpProvisioningMode != GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP
                    && groupClientIpProvisioningMode
                            != GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL) {
                throw new IllegalArgumentException(
                        "Invalid groupClientIpProvisioningMode");
            }
            mGroupClientIpProvisioningMode = groupClientIpProvisioningMode;
            return this;
        }

        public WifiP2pConfig build() {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress =
                    mDeviceAddress == null ? "02:00:00:00:00:00" : mDeviceAddress.toString();
            config.mNetworkName = mNetworkName;
            config.mPassphrase = mPassphrase;
            config.mGroupOperatingBand = mGroupOperatingBand;
            config.mGroupOperatingFrequency = mGroupOperatingFrequency;
            config.mNetId = mNetId;
            config.mGroupClientIpProvisioningMode = mGroupClientIpProvisioningMode;
            return config;
        }
    }
}
