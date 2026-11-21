// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.ParcelablePeerHandle;
import android.net.wifi.aware.PeerHandle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ResponderConfig implements Parcelable {
    public static final int RESPONDER_AP = 0;
    public static final int RESPONDER_STA = 1;
    static final int RESPONDER_AWARE = 4;

    public static final Creator<ResponderConfig> CREATOR =
            new Creator<ResponderConfig>() {
                @Override
                public ResponderConfig createFromParcel(Parcel in) {
                    MacAddress macAddress = in.readTypedObject(MacAddress.CREATOR);
                    ParcelablePeerHandle peerHandle =
                            in.readTypedObject(ParcelablePeerHandle.CREATOR);
                    int responderType = in.readInt();
                    boolean supports80211mc = in.readInt() == 1;
                    boolean supports80211azNtb = in.readInt() == 1;
                    int channelWidth = in.readInt();
                    int frequency = in.readInt();
                    int centerFreq0 = in.readInt();
                    int centerFreq1 = in.readInt();
                    int preamble = in.readInt();
                    return new ResponderConfig(
                            macAddress,
                            peerHandle,
                            responderType,
                            supports80211mc,
                            supports80211azNtb,
                            channelWidth,
                            frequency,
                            centerFreq0,
                            centerFreq1,
                            preamble);
                }

                @Override
                public ResponderConfig[] newArray(int size) {
                    return new ResponderConfig[size];
                }
            };

    final MacAddress mMacAddress;
    final PeerHandle mPeerHandle;
    final int mResponderType;
    final boolean mSupports80211mc;
    final boolean mSupports80211azNtb;
    final int mChannelWidth;
    final int mFrequency;
    final int mCenterFreq0;
    final int mCenterFreq1;
    final int mPreamble;

    ResponderConfig(
            MacAddress macAddress,
            PeerHandle peerHandle,
            int responderType,
            boolean supports80211mc,
            boolean supports80211azNtb,
            int channelWidth,
            int frequency,
            int centerFreq0,
            int centerFreq1,
            int preamble) {
        mMacAddress = macAddress;
        mPeerHandle = peerHandle;
        mResponderType = responderType;
        mSupports80211mc = supports80211mc;
        mSupports80211azNtb = supports80211azNtb;
        mChannelWidth = channelWidth;
        mFrequency = frequency;
        mCenterFreq0 = centerFreq0;
        mCenterFreq1 = centerFreq1;
        mPreamble = preamble;
    }

    public static ResponderConfig fromScanResult(ScanResult scanResult) {
        Objects.requireNonNull(scanResult, "scanResult cannot be null");
        if (scanResult.BSSID == null) {
            throw new IllegalArgumentException("Scan Result does not have a BSSID");
        }
        MacAddress mac = MacAddress.fromString(scanResult.BSSID);
        int preamble;
        switch (scanResult.getWifiStandard()) {
            case ScanResult.WIFI_STANDARD_11BE:
                preamble = ScanResult.PREAMBLE_EHT;
                break;
            case ScanResult.WIFI_STANDARD_11AX:
                preamble = ScanResult.PREAMBLE_HE;
                break;
            case ScanResult.WIFI_STANDARD_11AC:
                preamble = ScanResult.PREAMBLE_VHT;
                break;
            case ScanResult.WIFI_STANDARD_11N:
                preamble = ScanResult.PREAMBLE_HT;
                break;
            default:
                preamble = ScanResult.PREAMBLE_LEGACY;
                break;
        }
        return new ResponderConfig(
                mac,
                null,
                RESPONDER_AP,
                scanResult.is80211mcResponder(),
                scanResult.is80211azNtbResponder(),
                scanResult.channelWidth,
                scanResult.frequency,
                scanResult.centerFreq0,
                scanResult.centerFreq1,
                preamble);
    }

    public MacAddress getMacAddress() {
        return mMacAddress;
    }

    public boolean is80211mcSupported() {
        return mSupports80211mc;
    }

    public boolean is80211azNtbSupported() {
        return mSupports80211azNtb;
    }

    public int getChannelWidth() {
        return mChannelWidth;
    }

    public int getFrequencyMhz() {
        return mFrequency;
    }

    public int getCenterFreq0Mhz() {
        return mCenterFreq0;
    }

    public int getCenterFreq1Mhz() {
        return mCenterFreq1;
    }

    public int getPreamble() {
        return mPreamble;
    }

    public int getResponderType() {
        return mResponderType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mMacAddress, flags);
        dest.writeTypedObject(
                mPeerHandle == null ? null : new ParcelablePeerHandle(mPeerHandle), flags);
        dest.writeInt(mResponderType);
        dest.writeInt(mSupports80211mc ? 1 : 0);
        dest.writeInt(mSupports80211azNtb ? 1 : 0);
        dest.writeInt(mChannelWidth);
        dest.writeInt(mFrequency);
        dest.writeInt(mCenterFreq0);
        dest.writeInt(mCenterFreq1);
        dest.writeInt(mPreamble);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResponderConfig)) {
            return false;
        }
        ResponderConfig lhs = (ResponderConfig) o;
        return Objects.equals(mMacAddress, lhs.mMacAddress)
                && Objects.equals(mPeerHandle, lhs.mPeerHandle)
                && mResponderType == lhs.mResponderType
                && mSupports80211mc == lhs.mSupports80211mc
                && mSupports80211azNtb == lhs.mSupports80211azNtb
                && mChannelWidth == lhs.mChannelWidth
                && mFrequency == lhs.mFrequency
                && mCenterFreq0 == lhs.mCenterFreq0
                && mCenterFreq1 == lhs.mCenterFreq1
                && mPreamble == lhs.mPreamble;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mMacAddress,
                mPeerHandle,
                Integer.valueOf(mResponderType),
                Boolean.valueOf(mSupports80211mc),
                Boolean.valueOf(mSupports80211azNtb),
                Integer.valueOf(mChannelWidth),
                Integer.valueOf(mFrequency),
                Integer.valueOf(mCenterFreq0),
                Integer.valueOf(mCenterFreq1),
                Integer.valueOf(mPreamble));
    }

    @Override
    public String toString() {
        return "ResponderConfig: macAddress="
                + mMacAddress
                + ", responderType="
                + mResponderType
                + ", supports80211mc="
                + mSupports80211mc
                + ", supports80211azNtb="
                + mSupports80211azNtb
                + ", channelWidth="
                + mChannelWidth
                + ", frequency="
                + mFrequency
                + ", centerFreq0="
                + mCenterFreq0
                + ", centerFreq1="
                + mCenterFreq1
                + ", preamble="
                + mPreamble;
    }

    public static final class Builder {
        private MacAddress mMacAddress;
        private int mResponderType = RESPONDER_AP;
        private boolean mSupports80211mc = true;
        private boolean mSupports80211azNtb = false;
        private int mChannelWidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        private int mFrequency = 0;
        private int mCenterFreq0 = 0;
        private int mCenterFreq1 = 0;
        private int mPreamble = ScanResult.PREAMBLE_LEGACY;

        public Builder() {}

        public Builder setMacAddress(MacAddress macAddress) {
            mMacAddress = Objects.requireNonNull(macAddress, "macAddress");
            return this;
        }

        public Builder set80211mcSupported(boolean supports80211mc) {
            mSupports80211mc = supports80211mc;
            return this;
        }

        public Builder set80211azNtbSupported(boolean supports80211azNtb) {
            mSupports80211azNtb = supports80211azNtb;
            return this;
        }

        public Builder setChannelWidth(int channelWidth) {
            mChannelWidth = channelWidth;
            return this;
        }

        public Builder setFrequencyMhz(int frequency) {
            mFrequency = frequency;
            return this;
        }

        public Builder setCenterFreq0Mhz(int centerFreq0) {
            mCenterFreq0 = centerFreq0;
            return this;
        }

        public Builder setCenterFreq1Mhz(int centerFreq1) {
            mCenterFreq1 = centerFreq1;
            return this;
        }

        public Builder setPreamble(int preamble) {
            mPreamble = preamble;
            return this;
        }

        public Builder setResponderType(int responderType) {
            if (responderType != RESPONDER_AP && responderType != RESPONDER_STA) {
                throw new IllegalArgumentException("invalid responder type " + responderType);
            }
            mResponderType = responderType;
            return this;
        }

        public ResponderConfig build() {
            if (mMacAddress == null) {
                throw new IllegalArgumentException("MAC address must be specified");
            }
            return new ResponderConfig(
                    mMacAddress,
                    null,
                    mResponderType,
                    mSupports80211mc,
                    mSupports80211azNtb,
                    mChannelWidth,
                    mFrequency,
                    mCenterFreq0,
                    mCenterFreq1,
                    mPreamble);
        }
    }
}
