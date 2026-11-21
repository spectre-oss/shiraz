// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ScanResult implements Parcelable {
    public static final int UNSPECIFIED = -1;

    public static final int WIFI_BAND_24_GHZ = 1;
    public static final int WIFI_BAND_5_GHZ = 2;
    public static final int WIFI_BAND_6_GHZ = 8;
    public static final int WIFI_BAND_60_GHZ = 16;

    public static final int CHANNEL_WIDTH_20MHZ = 0;
    public static final int CHANNEL_WIDTH_40MHZ = 1;
    public static final int CHANNEL_WIDTH_80MHZ = 2;
    public static final int CHANNEL_WIDTH_160MHZ = 3;
    public static final int CHANNEL_WIDTH_80MHZ_PLUS_MHZ = 4;
    public static final int CHANNEL_WIDTH_320MHZ = 5;

    public static final int PREAMBLE_LEGACY = 0;
    public static final int PREAMBLE_HT = 1;
    public static final int PREAMBLE_VHT = 2;
    public static final int PREAMBLE_HE = 3;
    public static final int PREAMBLE_EHT = 4;

    public static final int WIFI_STANDARD_UNKNOWN = 0;
    public static final int WIFI_STANDARD_LEGACY = 1;
    public static final int WIFI_STANDARD_11N = 4;
    public static final int WIFI_STANDARD_11AC = 5;
    public static final int WIFI_STANDARD_11AX = 6;
    @Deprecated
    public static final int WIFI_STANDARD_11AD = 7;
    public static final int WIFI_STANDARD_11BE = 8;

    @Deprecated
    public String SSID;
    public String BSSID;
    public String capabilities;
    public int level;
    public int frequency;
    public int channelWidth;
    public int centerFreq0;
    public int centerFreq1;
    public long timestamp;
    @Deprecated
    public CharSequence venueName;
    @Deprecated
    public CharSequence operatorFriendlyName;

    private WifiSsid mWifiSsid;
    private MacAddress mApMldMacAddress;
    private int mApMloLinkId = MloLink.INVALID_MLO_LINK_ID;
    private List<MloLink> mAffiliatedMloLinks = Collections.emptyList();
    private int mWifiStandard = WIFI_STANDARD_UNKNOWN;
    private List<InformationElement> mInformationElements = Collections.emptyList();

    public static class InformationElement implements Parcelable {
        private final int mId;
        private final int mIdExt;
        private final byte[] mBytes;

        public InformationElement(int id, int idExt, byte[] bytes) {
            mId = id;
            mIdExt = idExt;
            mBytes = Objects.requireNonNull(bytes, "bytes must not be null").clone();
        }

        public InformationElement(InformationElement rhs) {
            Objects.requireNonNull(rhs, "rhs must not be null");
            mId = rhs.mId;
            mIdExt = rhs.mIdExt;
            mBytes = rhs.mBytes.clone();
        }

        public int getId() {
            return mId;
        }

        public int getIdExt() {
            return mIdExt;
        }

        public ByteBuffer getBytes() {
            return ByteBuffer.wrap(mBytes).asReadOnlyBuffer();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mId);
            dest.writeInt(mIdExt);
            dest.writeByteArray(mBytes);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) return true;
            if (!(that instanceof InformationElement)) return false;
            InformationElement ie = (InformationElement) that;
            return mId == ie.mId && mIdExt == ie.mIdExt && Arrays.equals(mBytes, ie.mBytes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mId, mIdExt, Arrays.hashCode(mBytes));
        }

        public static final Creator<InformationElement> CREATOR = new Creator<InformationElement>() {
            @Override
            public InformationElement createFromParcel(Parcel in) {
                int id = in.readInt();
                int idExt = in.readInt();
                byte[] bytes = in.createByteArray();
                return new InformationElement(id, idExt, bytes != null ? bytes : new byte[0]);
            }

            @Override
            public InformationElement[] newArray(int size) {
                return new InformationElement[size];
            }
        };
    }

    public ScanResult() {
    }

    public ScanResult(ScanResult source) {
        if (source != null) {
            SSID = source.SSID;
            BSSID = source.BSSID;
            capabilities = source.capabilities;
            level = source.level;
            frequency = source.frequency;
            channelWidth = source.channelWidth;
            centerFreq0 = source.centerFreq0;
            centerFreq1 = source.centerFreq1;
            timestamp = source.timestamp;
            venueName = source.venueName;
            operatorFriendlyName = source.operatorFriendlyName;
            mWifiSsid = source.mWifiSsid;
            mApMldMacAddress = source.mApMldMacAddress;
            mApMloLinkId = source.mApMloLinkId;
            mAffiliatedMloLinks = new ArrayList<>(source.mAffiliatedMloLinks);
            mWifiStandard = source.mWifiStandard;
            mInformationElements = new ArrayList<>(source.mInformationElements);
        }
    }

    public WifiSsid getWifiSsid() {
        if (mWifiSsid != null) {
            return mWifiSsid;
        }
        if (SSID != null) {
            byte[] b = SSID.getBytes(StandardCharsets.UTF_8);
            if (b.length <= 32) {
                return WifiSsid.fromBytes(b);
            }
        }
        return null;
    }

    public MacAddress getApMldMacAddress() {
        return mApMldMacAddress;
    }

    public int getApMloLinkId() {
        return mApMloLinkId;
    }

    public List<MloLink> getAffiliatedMloLinks() {
        return Collections.unmodifiableList(mAffiliatedMloLinks);
    }

    public int getWifiStandard() {
        return mWifiStandard;
    }

    public boolean is80211mcResponder() {
        return false;
    }

    public boolean is80211azNtbResponder() {
        return false;
    }

    public boolean isPasspointNetwork() {
        return capabilities != null && capabilities.contains("EAP");
    }

    public boolean isTwtResponder() {
        return false;
    }

    public static int convertChannelToFrequencyMhzIfSupported(int channel, int band) {
        if (band == WIFI_BAND_24_GHZ) {
            if (channel == 14) return 2484;
            if (channel >= 1 && channel <= 13) return 2407 + channel * 5;
            return UNSPECIFIED;
        }
        if (band == WIFI_BAND_5_GHZ) {
            if (channel >= 32 && channel <= 177) return 5000 + channel * 5;
            return UNSPECIFIED;
        }
        if (band == WIFI_BAND_6_GHZ) {
            if (channel == 2) return 5935;
            if (channel >= 1 && channel <= 233) return 5950 + channel * 5;
            return UNSPECIFIED;
        }
        if (band == WIFI_BAND_60_GHZ) {
            if (channel >= 1 && channel <= 6) return 56160 + channel * 2160;
            return UNSPECIFIED;
        }
        return UNSPECIFIED;
    }

    public static int convertFrequencyMhzToChannelIfSupported(int freqMhz) {
        if (freqMhz == 2484) return 14;
        if (freqMhz >= 2412 && freqMhz <= 2472 && (freqMhz - 2407) % 5 == 0) {
            return (freqMhz - 2407) / 5;
        }
        if (freqMhz >= 5160 && freqMhz <= 5885 && (freqMhz - 5000) % 5 == 0) {
            return (freqMhz - 5000) / 5;
        }
        if (freqMhz == 5935) return 2;
        if (freqMhz >= 5955 && freqMhz <= 7115 && (freqMhz - 5950) % 5 == 0) {
            return (freqMhz - 5950) / 5;
        }
        if (freqMhz >= 58320 && freqMhz <= 70200 && (freqMhz - 56160) % 2160 == 0) {
            return (freqMhz - 56160) / 2160;
        }
        return UNSPECIFIED;
    }

    public List<InformationElement> getInformationElements() {
        return Collections.unmodifiableList(mInformationElements);
    }

    public int[] getSecurityTypes() {
        return new int[0];
    }

    @Override
    public String toString() {
        return "SSID: " + (SSID == null ? "<none>" : SSID)
                + ", BSSID: " + (BSSID == null ? "<none>" : BSSID)
                + ", capabilities: " + (capabilities == null ? "<none>" : capabilities)
                + ", level: " + level
                + ", frequency: " + frequency
                + ", timestamp: " + timestamp;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(SSID);
        dest.writeString(BSSID);
        dest.writeString(capabilities);
        dest.writeInt(level);
        dest.writeInt(frequency);
        dest.writeInt(channelWidth);
        dest.writeInt(centerFreq0);
        dest.writeInt(centerFreq1);
        dest.writeLong(timestamp);
        dest.writeTypedObject(mWifiSsid, flags);
    }

    public static final Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
        @Override
        public ScanResult createFromParcel(Parcel in) {
            ScanResult sr = new ScanResult();
            sr.SSID = in.readString();
            sr.BSSID = in.readString();
            sr.capabilities = in.readString();
            sr.level = in.readInt();
            sr.frequency = in.readInt();
            sr.channelWidth = in.readInt();
            sr.centerFreq0 = in.readInt();
            sr.centerFreq1 = in.readInt();
            sr.timestamp = in.readLong();
            sr.mWifiSsid = in.readTypedObject(WifiSsid.CREATOR);
            return sr;
        }

        @Override
        public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };
}
