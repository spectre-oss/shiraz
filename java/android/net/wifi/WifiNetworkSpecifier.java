// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.net.NetworkSpecifier;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PatternMatcher;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class WifiNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    public static final Creator<WifiNetworkSpecifier> CREATOR =
            new Creator<WifiNetworkSpecifier>() {
                @Override
                public WifiNetworkSpecifier createFromParcel(Parcel in) {
                    PatternMatcher ssidPattern = in.readTypedObject(PatternMatcher.CREATOR);
                    MacAddress base = in.readTypedObject(MacAddress.CREATOR);
                    MacAddress mask = in.readTypedObject(MacAddress.CREATOR);
                    int band = in.readInt();
                    int[] channels = in.createIntArray();
                    boolean hidden = in.readInt() != 0;
                    String passphrase = in.readString();
                    WifiEnterpriseConfig enterprise =
                            in.readTypedObject(WifiEnterpriseConfig.CREATOR);
                    return new WifiNetworkSpecifier(
                            ssidPattern,
                            base,
                            mask,
                            band,
                            channels == null ? new int[0] : channels,
                            hidden,
                            passphrase,
                            enterprise);
                }

                @Override
                public WifiNetworkSpecifier[] newArray(int size) {
                    return new WifiNetworkSpecifier[size];
                }
            };

    private final PatternMatcher mSsidPatternMatcher;
    private final MacAddress mBssidBase;
    private final MacAddress mBssidMask;
    private final int mBand;
    private final int[] mPreferredChannels;
    private final boolean mIsHiddenSsid;
    private final String mPassphrase;
    private final WifiEnterpriseConfig mEnterpriseConfig;

    WifiNetworkSpecifier(
            PatternMatcher ssidPatternMatcher,
            MacAddress bssidBase,
            MacAddress bssidMask,
            int band,
            int[] preferredChannels,
            boolean isHiddenSsid,
            String passphrase,
            WifiEnterpriseConfig enterpriseConfig) {
        this.mSsidPatternMatcher = ssidPatternMatcher;
        this.mBssidBase = bssidBase;
        this.mBssidMask = bssidMask;
        this.mBand = band;
        this.mPreferredChannels = preferredChannels.clone();
        this.mIsHiddenSsid = isHiddenSsid;
        this.mPassphrase = passphrase;
        this.mEnterpriseConfig = enterpriseConfig;
    }

    public int getBand() {
        return mBand;
    }

    public int[] getPreferredChannelFrequenciesMhz() {
        return mPreferredChannels.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSsidPatternMatcher, flags);
        dest.writeTypedObject(mBssidBase, flags);
        dest.writeTypedObject(mBssidMask, flags);
        dest.writeInt(mBand);
        dest.writeIntArray(mPreferredChannels);
        dest.writeInt(mIsHiddenSsid ? 1 : 0);
        dest.writeString(mPassphrase);
        dest.writeTypedObject(mEnterpriseConfig, flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSsidPatternMatcher == null ? null : mSsidPatternMatcher.getPath(),
                mSsidPatternMatcher == null
                        ? null
                        : Integer.valueOf(mSsidPatternMatcher.getType()),
                mBssidBase,
                mBssidMask,
                Integer.valueOf(mBand),
                Integer.valueOf(Arrays.hashCode(mPreferredChannels)),
                Boolean.valueOf(mIsHiddenSsid),
                mPassphrase);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiNetworkSpecifier)) {
            return false;
        }
        WifiNetworkSpecifier lhs = (WifiNetworkSpecifier) obj;
        boolean ssidEq =
                (mSsidPatternMatcher == null && lhs.mSsidPatternMatcher == null)
                        || (mSsidPatternMatcher != null
                                && lhs.mSsidPatternMatcher != null
                                && Objects.equals(
                                        mSsidPatternMatcher.getPath(),
                                        lhs.mSsidPatternMatcher.getPath())
                                && mSsidPatternMatcher.getType()
                                        == lhs.mSsidPatternMatcher.getType());
        return ssidEq
                && Objects.equals(mBssidBase, lhs.mBssidBase)
                && Objects.equals(mBssidMask, lhs.mBssidMask)
                && mBand == lhs.mBand
                && Arrays.equals(mPreferredChannels, lhs.mPreferredChannels)
                && mIsHiddenSsid == lhs.mIsHiddenSsid
                && Objects.equals(mPassphrase, lhs.mPassphrase);
    }

    @Override
    public String toString() {
        return "WifiNetworkSpecifier{ssid="
                + (mSsidPatternMatcher == null ? "*" : mSsidPatternMatcher.getPath())
                + ", band="
                + mBand
                + "}";
    }

    public boolean canBeSatisfiedBy(NetworkSpecifier other) {
        return equals(other);
    }

    public NetworkSpecifier redact() {
        return new WifiNetworkSpecifier(
                new PatternMatcher(".*", PatternMatcher.PATTERN_SIMPLE_GLOB),
                ALL_ZEROS_MAC,
                ALL_ZEROS_MAC,
                mBand,
                mPreferredChannels,
                false,
                null,
                null);
    }

    static final MacAddress ALL_ZEROS_MAC = MacAddress.fromString("00:00:00:00:00:00");

    public static final class Builder {
        private static final MacAddress MATCH_EXACT_BSSID_PATTERN_MASK =
                MacAddress.BROADCAST_ADDRESS;

        private PatternMatcher mSsidPatternMatcher;
        private MacAddress mBssidBase;
        private MacAddress mBssidMask;
        private boolean mIsEnhancedOpen;
        private String mPassphrase;
        private WifiEnterpriseConfig mEnterpriseConfig;
        private boolean mIsHiddenSsid;
        private int mBand = ScanResult.UNSPECIFIED;
        private int[] mPreferredChannels = new int[0];

        public Builder() {}

        public Builder setSsidPattern(PatternMatcher ssidPattern) {
            this.mSsidPatternMatcher = Objects.requireNonNull(ssidPattern, "ssidPattern");
            return this;
        }

        public Builder setSsid(String ssid) {
            Objects.requireNonNull(ssid, "ssid");
            if (ssid.getBytes(StandardCharsets.UTF_8).length > 32) {
                throw new IllegalArgumentException("SSID UTF-8 bytes exceed 32 bytes");
            }
            this.mSsidPatternMatcher = new PatternMatcher(ssid, PatternMatcher.PATTERN_LITERAL);
            return this;
        }

        public Builder setBssidPattern(MacAddress baseAddress, MacAddress mask) {
            this.mBssidBase = Objects.requireNonNull(baseAddress, "baseAddress");
            this.mBssidMask = Objects.requireNonNull(mask, "mask");
            return this;
        }

        public Builder setBssid(MacAddress bssid) {
            Objects.requireNonNull(bssid, "bssid");
            this.mBssidBase = bssid;
            this.mBssidMask = MATCH_EXACT_BSSID_PATTERN_MASK;
            return this;
        }

        public Builder setIsEnhancedOpen(boolean isEnhancedOpen) {
            this.mIsEnhancedOpen = isEnhancedOpen;
            return this;
        }

        public Builder setWpa2Passphrase(String passphrase) {
            validateAsciiPassphrase(passphrase);
            this.mPassphrase = passphrase;
            return this;
        }

        public Builder setWpa3Passphrase(String passphrase) {
            validateAsciiPassphrase(passphrase);
            this.mPassphrase = passphrase;
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

        public Builder setIsHiddenSsid(boolean isHiddenSsid) {
            this.mIsHiddenSsid = isHiddenSsid;
            return this;
        }

        public Builder setBand(int band) {
            if (band != ScanResult.UNSPECIFIED
                    && band != ScanResult.WIFI_BAND_24_GHZ
                    && band != ScanResult.WIFI_BAND_5_GHZ
                    && band != ScanResult.WIFI_BAND_6_GHZ
                    && band != ScanResult.WIFI_BAND_60_GHZ) {
                throw new IllegalArgumentException("Invalid band: " + band);
            }
            this.mBand = band;
            return this;
        }

        public Builder setPreferredChannelsFrequenciesMhz(int[] channelFreqs) {
            Objects.requireNonNull(channelFreqs, "channelFreqs");
            this.mPreferredChannels = channelFreqs.clone();
            return this;
        }

        private static void validateAsciiPassphrase(String passphrase) {
            Objects.requireNonNull(passphrase, "passphrase");
            if (!StandardCharsets.US_ASCII.newEncoder().canEncode(passphrase)) {
                throw new IllegalArgumentException("Passphrase must be ASCII encodable");
            }
        }

        public WifiNetworkSpecifier build() {
            PatternMatcher ssid =
                    mSsidPatternMatcher != null
                            ? mSsidPatternMatcher
                            : new PatternMatcher(".*", PatternMatcher.PATTERN_SIMPLE_GLOB);
            MacAddress base = mBssidBase != null ? mBssidBase : ALL_ZEROS_MAC;
            MacAddress mask = mBssidMask != null ? mBssidMask : ALL_ZEROS_MAC;
            if (mIsHiddenSsid && ssid.getType() != PatternMatcher.PATTERN_LITERAL) {
                throw new IllegalStateException("Hidden network must specify literal SSID");
            }
            return new WifiNetworkSpecifier(
                    ssid,
                    base,
                    mask,
                    mBand,
                    mPreferredChannels,
                    mIsHiddenSsid,
                    mPassphrase,
                    mEnterpriseConfig);
        }
    }
}
