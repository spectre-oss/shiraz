// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothHidDeviceAppQosSettings implements Parcelable {
    public static final int SERVICE_NO_TRAFFIC = 0x00;
    public static final int SERVICE_BEST_EFFORT = 0x01;
    public static final int SERVICE_GUARANTEED = 0x02;
    public static final int MAX = -1;

    private final int mServiceType;
    private final int mTokenRate;
    private final int mTokenBucketSize;
    private final int mPeakBandwidth;
    private final int mLatency;
    private final int mDelayVariation;

    public BluetoothHidDeviceAppQosSettings(int serviceType, int tokenRate, int tokenBucketSize,
            int peakBandwidth, int latency, int delayVariation) {
        mServiceType = serviceType;
        mTokenRate = tokenRate;
        mTokenBucketSize = tokenBucketSize;
        mPeakBandwidth = peakBandwidth;
        mLatency = latency;
        mDelayVariation = delayVariation;
    }

    public int getServiceType() {
        return mServiceType;
    }

    public int getTokenRate() {
        return mTokenRate;
    }

    public int getTokenBucketSize() {
        return mTokenBucketSize;
    }

    public int getPeakBandwidth() {
        return mPeakBandwidth;
    }

    public int getLatency() {
        return mLatency;
    }

    public int getDelayVariation() {
        return mDelayVariation;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mServiceType);
        out.writeInt(mTokenRate);
        out.writeInt(mTokenBucketSize);
        out.writeInt(mPeakBandwidth);
        out.writeInt(mLatency);
        out.writeInt(mDelayVariation);
    }

    public static final Parcelable.Creator<BluetoothHidDeviceAppQosSettings> CREATOR = new Parcelable.Creator<BluetoothHidDeviceAppQosSettings>() {
        public BluetoothHidDeviceAppQosSettings createFromParcel(Parcel in) {
            return new BluetoothHidDeviceAppQosSettings(
                    in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt());
        }

        public BluetoothHidDeviceAppQosSettings[] newArray(int size) {
            return new BluetoothHidDeviceAppQosSettings[size];
        }
    };
}
