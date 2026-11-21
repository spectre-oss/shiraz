// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AssociatedDevice implements Parcelable {
    private static final int CLASSIC_BLUETOOTH = 0;
    private static final int BLUETOOTH_LE = 1;
    private static final int WIFI = 2;

    private final Parcelable mDevice;

    public AssociatedDevice(Parcelable device) {
        mDevice = Objects.requireNonNull(device, "device must not be null");
    }

    public BluetoothDevice getBluetoothDevice() {
        return (mDevice instanceof BluetoothDevice) ? (BluetoothDevice) mDevice : null;
    }

    public ScanResult getBleDevice() {
        return (mDevice instanceof ScanResult) ? (ScanResult) mDevice : null;
    }

    public android.net.wifi.ScanResult getWifiDevice() {
        return (mDevice instanceof android.net.wifi.ScanResult)
                ? (android.net.wifi.ScanResult) mDevice : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mDevice instanceof BluetoothDevice) {
            dest.writeInt(CLASSIC_BLUETOOTH);
            mDevice.writeToParcel(dest, flags);
        } else if (mDevice instanceof ScanResult) {
            dest.writeInt(BLUETOOTH_LE);
            mDevice.writeToParcel(dest, flags);
        } else if (mDevice instanceof android.net.wifi.ScanResult) {
            dest.writeInt(WIFI);
            mDevice.writeToParcel(dest, flags);
        } else {
            dest.writeInt(-1);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "AssociatedDevice{device=" + mDevice + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssociatedDevice)) return false;
        return Objects.equals(mDevice, ((AssociatedDevice) o).mDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mDevice);
    }

    public static final Creator<AssociatedDevice> CREATOR = new Creator<AssociatedDevice>() {
        @Override
        public AssociatedDevice createFromParcel(Parcel in) {
            int type = in.readInt();
            if (type == CLASSIC_BLUETOOTH) {
                return new AssociatedDevice(BluetoothDevice.CREATOR.createFromParcel(in));
            } else if (type == BLUETOOTH_LE) {
                return new AssociatedDevice(ScanResult.CREATOR.createFromParcel(in));
            } else if (type == WIFI) {
                return new AssociatedDevice(android.net.wifi.ScanResult.CREATOR.createFromParcel(in));
            }
            throw new IllegalArgumentException("Unsupported AssociatedDevice type: " + type);
        }

        @Override
        public AssociatedDevice[] newArray(int size) {
            return new AssociatedDevice[size];
        }
    };
}
