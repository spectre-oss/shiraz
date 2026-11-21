// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;
import java.util.regex.Pattern;

public final class WifiDeviceFilter implements DeviceFilter<ScanResult> {
    private final Pattern mNamePattern;
    private final MacAddress mBssid;
    private final MacAddress mBssidMask;

    WifiDeviceFilter(Pattern namePattern, MacAddress bssid, MacAddress bssidMask) {
        mNamePattern = namePattern;
        mBssid = bssid;
        mBssidMask = bssidMask != null ? bssidMask : MacAddress.BROADCAST_ADDRESS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WifiDeviceFilter)) return false;
        WifiDeviceFilter that = (WifiDeviceFilter) o;
        String p1 = mNamePattern != null ? mNamePattern.pattern() : null;
        String p2 = that.mNamePattern != null ? that.mNamePattern.pattern() : null;
        return Objects.equals(p1, p2)
                && Objects.equals(mBssid, that.mBssid)
                && Objects.equals(mBssidMask, that.mBssidMask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNamePattern != null ? mNamePattern.pattern() : null,
                mBssid, mBssidMask);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern != null ? mNamePattern.pattern() : null);
        dest.writeTypedObject(mBssid, flags);
        dest.writeTypedObject(mBssidMask, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WifiDeviceFilter> CREATOR = new Creator<WifiDeviceFilter>() {
        @Override
        public WifiDeviceFilter createFromParcel(Parcel in) {
            String pat = in.readString();
            MacAddress bssid = in.readTypedObject(MacAddress.CREATOR);
            MacAddress mask = in.readTypedObject(MacAddress.CREATOR);
            return new WifiDeviceFilter(
                    pat != null ? Pattern.compile(pat) : null, bssid, mask);
        }

        @Override
        public WifiDeviceFilter[] newArray(int size) {
            return new WifiDeviceFilter[size];
        }
    };

    public static final class Builder {
        private Pattern mNamePattern;
        private MacAddress mBssid;
        private MacAddress mBssidMask = MacAddress.BROADCAST_ADDRESS;

        public Builder() {
        }

        public Builder setNamePattern(Pattern value) {
            mNamePattern = value;
            return this;
        }

        public Builder setBssid(MacAddress value) {
            mBssid = value;
            return this;
        }

        public Builder setBssidMask(MacAddress value) {
            mBssidMask = Objects.requireNonNull(value, "value must not be null");
            return this;
        }

        public WifiDeviceFilter build() {
            return new WifiDeviceFilter(mNamePattern, mBssid, mBssidMask);
        }
    }
}
