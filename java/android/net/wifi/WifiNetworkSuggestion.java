// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class WifiNetworkSuggestion implements Parcelable {
    public static final int RANDOMIZATION_PERSISTENT = 0;
    public static final int RANDOMIZATION_NON_PERSISTENT = 1;

    public static final Creator<WifiNetworkSuggestion> CREATOR =
            new Creator<WifiNetworkSuggestion>() {
                @Override
                public WifiNetworkSuggestion createFromParcel(Parcel in) {
                    Builder b = new Builder();
                    b.mWifiSsid = in.readTypedObject(WifiSsid.CREATOR);
                    b.mBssid = in.readTypedObject(MacAddress.CREATOR);
                    b.mPassphrase = in.readString();
                    b.mEnterpriseConfig = in.readTypedObject(WifiEnterpriseConfig.CREATOR);
                    b.mPasspointConfig = in.readTypedObject(PasspointConfiguration.CREATOR);
                    b.mIsEnhancedOpen = in.readInt() != 0;
                    b.mIsHiddenSsid = in.readInt() != 0;
                    b.mIsAppInteractionRequired = in.readInt() != 0;
                    b.mIsUserInteractionRequired = in.readInt() != 0;
                    b.mIsMetered = in.readInt() != 0;
                    b.mIsCredentialSharedWithUser = in.readInt() != 0;
                    b.mIsInitialAutojoinEnabled = in.readInt() != 0;
                    b.mIsUntrusted = in.readInt() != 0;
                    b.mIsRestricted = in.readInt() != 0;
                    b.mIsCarrierMerged = in.readInt() != 0;
                    b.mIsWifi7Enabled = in.readInt() != 0;
                    b.mPriority = in.readInt();
                    b.mPriorityGroup = in.readInt();
                    b.mSubscriptionId = in.readInt();
                    b.mSubscriptionGroup = in.readTypedObject(ParcelUuid.CREATOR);
                    b.mMacRandomizationSetting = in.readInt();
                    return new WifiNetworkSuggestion(b);
                }

                @Override
                public WifiNetworkSuggestion[] newArray(int size) {
                    return new WifiNetworkSuggestion[size];
                }
            };

    private final WifiSsid mWifiSsid;
    private final MacAddress mBssid;
    private final String mPassphrase;
    private final WifiEnterpriseConfig mEnterpriseConfig;
    private final PasspointConfiguration mPasspointConfig;
    private final boolean mIsEnhancedOpen;
    private final boolean mIsHiddenSsid;
    private final boolean mIsAppInteractionRequired;
    private final boolean mIsUserInteractionRequired;
    private final boolean mIsMetered;
    private final boolean mIsCredentialSharedWithUser;
    private final boolean mIsInitialAutojoinEnabled;
    private final boolean mIsUntrusted;
    private final boolean mIsRestricted;
    private final boolean mIsCarrierMerged;
    private final boolean mIsWifi7Enabled;
    private final int mPriority;
    private final int mPriorityGroup;
    private final int mSubscriptionId;
    private final ParcelUuid mSubscriptionGroup;
    private final int mMacRandomizationSetting;

    WifiNetworkSuggestion(Builder b) {
        this.mWifiSsid = b.mWifiSsid;
        this.mBssid = b.mBssid;
        this.mPassphrase = b.mPassphrase;
        this.mEnterpriseConfig = b.mEnterpriseConfig;
        this.mPasspointConfig = b.mPasspointConfig;
        this.mIsEnhancedOpen = b.mIsEnhancedOpen;
        this.mIsHiddenSsid = b.mIsHiddenSsid;
        this.mIsAppInteractionRequired = b.mIsAppInteractionRequired;
        this.mIsUserInteractionRequired = b.mIsUserInteractionRequired;
        this.mIsMetered = b.mIsMetered;
        this.mIsCredentialSharedWithUser = b.mIsCredentialSharedWithUser;
        this.mIsInitialAutojoinEnabled = b.mIsInitialAutojoinEnabled;
        this.mIsUntrusted = b.mIsUntrusted;
        this.mIsRestricted = b.mIsRestricted;
        this.mIsCarrierMerged = b.mIsCarrierMerged;
        this.mIsWifi7Enabled = b.mIsWifi7Enabled;
        this.mPriority = b.mPriority;
        this.mPriorityGroup = b.mPriorityGroup;
        this.mSubscriptionId = b.mSubscriptionId;
        this.mSubscriptionGroup = b.mSubscriptionGroup;
        this.mMacRandomizationSetting = b.mMacRandomizationSetting;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mWifiSsid, flags);
        dest.writeTypedObject(mBssid, flags);
        dest.writeString(mPassphrase);
        dest.writeTypedObject(mEnterpriseConfig, flags);
        dest.writeTypedObject(mPasspointConfig, flags);
        dest.writeInt(mIsEnhancedOpen ? 1 : 0);
        dest.writeInt(mIsHiddenSsid ? 1 : 0);
        dest.writeInt(mIsAppInteractionRequired ? 1 : 0);
        dest.writeInt(mIsUserInteractionRequired ? 1 : 0);
        dest.writeInt(mIsMetered ? 1 : 0);
        dest.writeInt(mIsCredentialSharedWithUser ? 1 : 0);
        dest.writeInt(mIsInitialAutojoinEnabled ? 1 : 0);
        dest.writeInt(mIsUntrusted ? 1 : 0);
        dest.writeInt(mIsRestricted ? 1 : 0);
        dest.writeInt(mIsCarrierMerged ? 1 : 0);
        dest.writeInt(mIsWifi7Enabled ? 1 : 0);
        dest.writeInt(mPriority);
        dest.writeInt(mPriorityGroup);
        dest.writeInt(mSubscriptionId);
        dest.writeTypedObject(mSubscriptionGroup, flags);
        dest.writeInt(mMacRandomizationSetting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWifiSsid, mBssid, mPasspointConfig, Integer.valueOf(mSubscriptionId), mSubscriptionGroup);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiNetworkSuggestion)) {
            return false;
        }
        WifiNetworkSuggestion lhs = (WifiNetworkSuggestion) obj;
        return Objects.equals(mWifiSsid, lhs.mWifiSsid)
                && Objects.equals(mBssid, lhs.mBssid)
                && Objects.equals(mPasspointConfig, lhs.mPasspointConfig)
                && mSubscriptionId == lhs.mSubscriptionId
                && Objects.equals(mSubscriptionGroup, lhs.mSubscriptionGroup);
    }

    @Override
    public String toString() {
        return "WifiNetworkSuggestion{ssid=" + mWifiSsid + ", bssid=" + mBssid + "}";
    }

    public MacAddress getBssid() {
        return mBssid;
    }

    public boolean isCredentialSharedWithUser() {
        return mIsCredentialSharedWithUser;
    }

    public boolean isAppInteractionRequired() {
        return mIsAppInteractionRequired;
    }

    public boolean isEnhancedOpen() {
        return mIsEnhancedOpen;
    }

    public boolean isHiddenSsid() {
        return mIsHiddenSsid;
    }

    public boolean isInitialAutojoinEnabled() {
        return mIsInitialAutojoinEnabled;
    }

    public boolean isMetered() {
        return mIsMetered;
    }

    public boolean isUserInteractionRequired() {
        return mIsUserInteractionRequired;
    }

    public PasspointConfiguration getPasspointConfig() {
        return mPasspointConfig;
    }

    public int getPriority() {
        return mPriority;
    }

    public String getSsid() {
        if (mWifiSsid == null) {
            return null;
        }
        return new String(mWifiSsid.getBytes(), StandardCharsets.UTF_8);
    }

    public WifiSsid getWifiSsid() {
        return mWifiSsid;
    }

    public boolean isUntrusted() {
        return mIsUntrusted;
    }

    public boolean isRestricted() {
        return mIsRestricted;
    }

    public boolean isCarrierMerged() {
        return mIsCarrierMerged;
    }

    public WifiEnterpriseConfig getEnterpriseConfig() {
        return mEnterpriseConfig;
    }

    public String getPassphrase() {
        return mPassphrase;
    }

    public int getPriorityGroup() {
        return mPriorityGroup;
    }

    public int getSubscriptionId() {
        return mSubscriptionId;
    }

    public int getMacRandomizationSetting() {
        return mMacRandomizationSetting;
    }

    public ParcelUuid getSubscriptionGroup() {
        return mSubscriptionGroup;
    }

    public boolean isWifi7Enabled() {
        return mIsWifi7Enabled;
    }

    public static final class Builder {
        WifiSsid mWifiSsid;
        MacAddress mBssid;
        String mPassphrase;
        WifiEnterpriseConfig mEnterpriseConfig;
        PasspointConfiguration mPasspointConfig;
        boolean mIsEnhancedOpen;
        boolean mIsHiddenSsid;
        boolean mIsAppInteractionRequired;
        boolean mIsUserInteractionRequired;
        boolean mIsMetered;
        boolean mIsCredentialSharedWithUser = true;
        boolean mIsInitialAutojoinEnabled = true;
        boolean mIsUntrusted;
        boolean mIsRestricted;
        boolean mIsCarrierMerged;
        boolean mIsWifi7Enabled = true;
        boolean mIsWpa3SaeH2eOnlyModeEnabled;
        int mPriority = 0;
        int mPriorityGroup = 0;
        int mSubscriptionId = -1;
        ParcelUuid mSubscriptionGroup;
        int mMacRandomizationSetting = RANDOMIZATION_PERSISTENT;

        public Builder() {}

        public Builder setSsid(String ssid) {
            Objects.requireNonNull(ssid, "ssid");
            byte[] utf8 = ssid.getBytes(StandardCharsets.UTF_8);
            if (utf8.length == 0 || utf8.length > 32) {
                throw new IllegalArgumentException("Invalid SSID length");
            }
            this.mWifiSsid = WifiSsid.fromBytes(utf8);
            return this;
        }

        public Builder setWifiSsid(WifiSsid wifiSsid) {
            Objects.requireNonNull(wifiSsid, "wifiSsid");
            if (wifiSsid.getBytes().length == 0) {
                throw new IllegalArgumentException("Empty WifiSsid");
            }
            this.mWifiSsid = wifiSsid;
            return this;
        }

        public Builder setBssid(MacAddress bssid) {
            this.mBssid = Objects.requireNonNull(bssid, "bssid");
            return this;
        }

        public Builder setIsEnhancedOpen(boolean isEnhancedOpen) {
            this.mIsEnhancedOpen = isEnhancedOpen;
            return this;
        }

        public Builder setWpa2Passphrase(String passphrase) {
            this.mPassphrase = Objects.requireNonNull(passphrase, "passphrase");
            return this;
        }

        public Builder setWpa3Passphrase(String passphrase) {
            this.mPassphrase = Objects.requireNonNull(passphrase, "passphrase");
            return this;
        }

        public Builder setWpa2EnterpriseConfig(WifiEnterpriseConfig enterpriseConfig) {
            this.mEnterpriseConfig =
                    new WifiEnterpriseConfig(
                            Objects.requireNonNull(enterpriseConfig, "enterpriseConfig"));
            return this;
        }

        @Deprecated
        public Builder setWpa3EnterpriseConfig(WifiEnterpriseConfig enterpriseConfig) {
            return setWpa3EnterpriseStandardModeConfig(enterpriseConfig);
        }

        public Builder setWpa3EnterpriseStandardModeConfig(
                WifiEnterpriseConfig enterpriseConfig) {
            this.mEnterpriseConfig =
                    new WifiEnterpriseConfig(
                            Objects.requireNonNull(enterpriseConfig, "enterpriseConfig"));
            return this;
        }

        public Builder setWpa3Enterprise192BitModeConfig(WifiEnterpriseConfig enterpriseConfig) {
            this.mEnterpriseConfig =
                    new WifiEnterpriseConfig(
                            Objects.requireNonNull(enterpriseConfig, "enterpriseConfig"));
            return this;
        }

        public Builder setPasspointConfig(PasspointConfiguration passpointConfig) {
            this.mPasspointConfig =
                    new PasspointConfiguration(
                            Objects.requireNonNull(passpointConfig, "passpointConfig"));
            return this;
        }

        public Builder setSubscriptionId(int subscriptionId) {
            this.mSubscriptionId = subscriptionId;
            return this;
        }

        public Builder setSubscriptionGroup(ParcelUuid groupUuid) {
            this.mSubscriptionGroup = Objects.requireNonNull(groupUuid, "groupUuid");
            return this;
        }

        public Builder setPriorityGroup(int priorityGroup) {
            if (priorityGroup < 0) {
                throw new IllegalArgumentException("priorityGroup < 0");
            }
            this.mPriorityGroup = priorityGroup;
            return this;
        }

        public Builder setWapiPassphrase(String passphrase) {
            this.mPassphrase = Objects.requireNonNull(passphrase, "passphrase");
            return this;
        }

        public Builder setWapiEnterpriseConfig(WifiEnterpriseConfig enterpriseConfig) {
            this.mEnterpriseConfig =
                    new WifiEnterpriseConfig(
                            Objects.requireNonNull(enterpriseConfig, "enterpriseConfig"));
            return this;
        }

        public Builder setIsHiddenSsid(boolean isHiddenSsid) {
            this.mIsHiddenSsid = isHiddenSsid;
            return this;
        }

        public Builder setMacRandomizationSetting(int macRandomizationSetting) {
            if (macRandomizationSetting != RANDOMIZATION_PERSISTENT
                    && macRandomizationSetting != RANDOMIZATION_NON_PERSISTENT) {
                throw new IllegalArgumentException("Invalid macRandomizationSetting");
            }
            this.mMacRandomizationSetting = macRandomizationSetting;
            return this;
        }

        public Builder setIsAppInteractionRequired(boolean isAppInteractionRequired) {
            this.mIsAppInteractionRequired = isAppInteractionRequired;
            return this;
        }

        public Builder setIsUserInteractionRequired(boolean isUserInteractionRequired) {
            this.mIsUserInteractionRequired = isUserInteractionRequired;
            return this;
        }

        public Builder setPriority(int priority) {
            if (priority < 0) {
                throw new IllegalArgumentException("priority < 0");
            }
            this.mPriority = priority;
            return this;
        }

        public Builder setIsMetered(boolean isMetered) {
            this.mIsMetered = isMetered;
            return this;
        }

        public Builder setCredentialSharedWithUser(boolean isShared) {
            this.mIsCredentialSharedWithUser = isShared;
            return this;
        }

        public Builder setIsInitialAutojoinEnabled(boolean enabled) {
            this.mIsInitialAutojoinEnabled = enabled;
            return this;
        }

        public Builder setUntrusted(boolean isUntrusted) {
            this.mIsUntrusted = isUntrusted;
            return this;
        }

        public Builder setRestricted(boolean isRestricted) {
            this.mIsRestricted = isRestricted;
            return this;
        }

        public Builder setWifi7Enabled(boolean enabled) {
            this.mIsWifi7Enabled = enabled;
            return this;
        }

        public Builder setCarrierMerged(boolean isCarrierMerged) {
            this.mIsCarrierMerged = isCarrierMerged;
            return this;
        }

        public Builder setIsWpa3SaeH2eOnlyModeEnabled(boolean enable) {
            this.mIsWpa3SaeH2eOnlyModeEnabled = enable;
            return this;
        }

        public WifiNetworkSuggestion build() {
            if (mWifiSsid == null && mPasspointConfig == null) {
                throw new IllegalStateException("Either SSID or PasspointConfiguration required");
            }
            if (mWifiSsid != null && mPasspointConfig != null) {
                throw new IllegalStateException("Cannot specify both SSID and PasspointConfiguration");
            }
            return new WifiNetworkSuggestion(this);
        }
    }
}
