// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class WifiAvailableChannel implements Parcelable {
    public static final int OP_MODE_STA = 1;
    public static final int OP_MODE_SAP = 2;
    public static final int OP_MODE_WIFI_DIRECT_CLI = 4;
    public static final int OP_MODE_WIFI_DIRECT_GO = 8;
    public static final int OP_MODE_WIFI_AWARE = 16;
    public static final int OP_MODE_TDLS = 32;

    public static final Creator<WifiAvailableChannel> CREATOR =
            new Creator<WifiAvailableChannel>() {
                @Override
                public WifiAvailableChannel createFromParcel(Parcel in) {
                    return new WifiAvailableChannel(in.readInt(), in.readInt());
                }

                @Override
                public WifiAvailableChannel[] newArray(int size) {
                    return new WifiAvailableChannel[size];
                }
            };

    private final int mFrequency;
    private final int mOpModes;

    public WifiAvailableChannel(int freq, int opModes) {
        this.mFrequency = freq;
        this.mOpModes = opModes;
    }

    public int getFrequencyMhz() {
        return mFrequency;
    }

    public int getOperationalModes() {
        return mOpModes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFrequency);
        dest.writeInt(mOpModes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WifiAvailableChannel)) {
            return false;
        }
        WifiAvailableChannel that = (WifiAvailableChannel) o;
        return mFrequency == that.mFrequency && mOpModes == that.mOpModes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mFrequency), Integer.valueOf(mOpModes));
    }

    @Override
    public String toString() {
        return "WifiAvailableChannel{mFrequency=" + mFrequency + ", mOpModes=" + mOpModes + "}";
    }
}
