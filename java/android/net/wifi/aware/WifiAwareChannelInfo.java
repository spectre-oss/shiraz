// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class WifiAwareChannelInfo implements Parcelable {
    public static final Creator<WifiAwareChannelInfo> CREATOR =
            new Creator<WifiAwareChannelInfo>() {
                @Override
                public WifiAwareChannelInfo createFromParcel(Parcel in) {
                    return new WifiAwareChannelInfo(in.readInt(), in.readInt(), in.readInt());
                }

                @Override
                public WifiAwareChannelInfo[] newArray(int size) {
                    return new WifiAwareChannelInfo[size];
                }
            };

    private final int mChannelFrequencyMhz;
    private final int mChannelBandwidth;
    private final int mNumSpatialStreams;

    WifiAwareChannelInfo(int freq, int bw, int numSpatialStreams) {
        this.mChannelFrequencyMhz = freq;
        this.mChannelBandwidth = bw;
        this.mNumSpatialStreams = numSpatialStreams;
    }

    public int getChannelFrequencyMhz() {
        return mChannelFrequencyMhz;
    }

    public int getChannelBandwidth() {
        return mChannelBandwidth;
    }

    public int getSpatialStreamCount() {
        return mNumSpatialStreams;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mChannelFrequencyMhz);
        dest.writeInt(mChannelBandwidth);
        dest.writeInt(mNumSpatialStreams);
    }

    @Override
    public String toString() {
        return "WifiAwareChannelInfo{freq="
                + mChannelFrequencyMhz
                + ", bw="
                + mChannelBandwidth
                + ", ss="
                + mNumSpatialStreams
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiAwareChannelInfo)) {
            return false;
        }
        WifiAwareChannelInfo lhs = (WifiAwareChannelInfo) obj;
        return mChannelFrequencyMhz == lhs.mChannelFrequencyMhz
                && mChannelBandwidth == lhs.mChannelBandwidth
                && mNumSpatialStreams == lhs.mNumSpatialStreams;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mChannelFrequencyMhz),
                Integer.valueOf(mChannelBandwidth),
                Integer.valueOf(mNumSpatialStreams));
    }
}
