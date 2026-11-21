// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothHidDeviceAppSdpSettings implements Parcelable {
    private final String mName;
    private final String mDescription;
    private final String mProvider;
    private final byte mSubclass;
    private final byte[] mDescriptors;

    public BluetoothHidDeviceAppSdpSettings(String name, String description, String provider,
            byte subclass, byte[] descriptors) {
        mName = name;
        mDescription = description;
        mProvider = provider;
        mSubclass = subclass;
        mDescriptors = descriptors != null ? descriptors.clone() : null;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getProvider() {
        return mProvider;
    }

    public byte getSubclass() {
        return mSubclass;
    }

    public byte[] getDescriptors() {
        return mDescriptors != null ? mDescriptors.clone() : null;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeString(mDescription);
        out.writeString(mProvider);
        out.writeByte(mSubclass);
        out.writeByteArray(mDescriptors);
    }

    public static final Parcelable.Creator<BluetoothHidDeviceAppSdpSettings> CREATOR = new Parcelable.Creator<BluetoothHidDeviceAppSdpSettings>() {
        public BluetoothHidDeviceAppSdpSettings createFromParcel(Parcel in) {
            return new BluetoothHidDeviceAppSdpSettings(
                    in.readString(), in.readString(), in.readString(),
                    in.readByte(), in.createByteArray());
        }

        public BluetoothHidDeviceAppSdpSettings[] newArray(int size) {
            return new BluetoothHidDeviceAppSdpSettings[size];
        }
    };
}
