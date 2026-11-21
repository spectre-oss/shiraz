// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothHealthAppConfiguration implements Parcelable {
    private final String mName;
    private final int mDataType;
    private final int mRole;

    BluetoothHealthAppConfiguration(String name, int dataType, int role) {
        mName = name;
        mDataType = dataType;
        mRole = role;
    }

    public int describeContents() {
        return 0;
    }

    public int getDataType() {
        return mDataType;
    }

    public String getName() {
        return mName;
    }

    public int getRole() {
        return mRole;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeInt(mDataType);
        out.writeInt(mRole);
    }

    public static final Parcelable.Creator<BluetoothHealthAppConfiguration> CREATOR = new Parcelable.Creator<BluetoothHealthAppConfiguration>() {
        public BluetoothHealthAppConfiguration createFromParcel(Parcel in) {
            return new BluetoothHealthAppConfiguration(in.readString(), in.readInt(), in.readInt());
        }

        public BluetoothHealthAppConfiguration[] newArray(int size) {
            return new BluetoothHealthAppConfiguration[size];
        }
    };
}
