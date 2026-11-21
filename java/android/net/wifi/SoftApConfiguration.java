// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class SoftApConfiguration implements Parcelable {
    public static final int SECURITY_TYPE_OPEN = 0;
    public static final int SECURITY_TYPE_WPA2_PSK = 1;
    public static final int SECURITY_TYPE_WPA3_SAE_TRANSITION = 2;
    public static final int SECURITY_TYPE_WPA3_SAE = 3;
    public static final int SECURITY_TYPE_WPA3_OWE_TRANSITION = 4;
    public static final int SECURITY_TYPE_WPA3_OWE = 5;

    public static final Creator<SoftApConfiguration> CREATOR =
            new Creator<SoftApConfiguration>() {
                @Override
                public SoftApConfiguration createFromParcel(Parcel in) {
                    WifiSsid ssid = in.readTypedObject(WifiSsid.CREATOR);
                    MacAddress bssid = in.readTypedObject(MacAddress.CREATOR);
                    String passphrase = in.readString();
                    boolean hidden = in.readInt() != 0;
                    int securityType = in.readInt();
                    return new SoftApConfiguration(ssid, bssid, passphrase, hidden, securityType);
                }

                @Override
                public SoftApConfiguration[] newArray(int size) {
                    return new SoftApConfiguration[size];
                }
            };

    private final WifiSsid mWifiSsid;
    private final MacAddress mBssid;
    private final String mPassphrase;
    private final boolean mHiddenSsid;
    private final int mSecurityType;

    SoftApConfiguration(
            WifiSsid wifiSsid,
            MacAddress bssid,
            String passphrase,
            boolean hiddenSsid,
            int securityType) {
        this.mWifiSsid = wifiSsid;
        this.mBssid = bssid;
        this.mPassphrase = passphrase;
        this.mHiddenSsid = hiddenSsid;
        this.mSecurityType = securityType;
    }

    @Deprecated
    public String getSsid() {
        return mWifiSsid == null ? null : new String(mWifiSsid.getBytes(), StandardCharsets.UTF_8);
    }

    public WifiSsid getWifiSsid() {
        return mWifiSsid;
    }

    public MacAddress getBssid() {
        return mBssid;
    }

    public String getPassphrase() {
        return mPassphrase;
    }

    public boolean isHiddenSsid() {
        return mHiddenSsid;
    }

    public int getSecurityType() {
        return mSecurityType;
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
        dest.writeInt(mHiddenSsid ? 1 : 0);
        dest.writeInt(mSecurityType);
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        }
        if (!(otherObj instanceof SoftApConfiguration)) {
            return false;
        }
        SoftApConfiguration other = (SoftApConfiguration) otherObj;
        return mHiddenSsid == other.mHiddenSsid
                && mSecurityType == other.mSecurityType
                && Objects.equals(mWifiSsid, other.mWifiSsid)
                && Objects.equals(mBssid, other.mBssid)
                && Objects.equals(mPassphrase, other.mPassphrase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mWifiSsid,
                mBssid,
                mPassphrase,
                Boolean.valueOf(mHiddenSsid),
                Integer.valueOf(mSecurityType));
    }

    @Override
    public String toString() {
        return "SoftApConfiguration{ssid="
                + mWifiSsid
                + ", bssid="
                + mBssid
                + ", securityType="
                + mSecurityType
                + "}";
    }
}
