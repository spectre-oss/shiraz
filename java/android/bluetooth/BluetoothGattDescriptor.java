// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.UUID;

public class BluetoothGattDescriptor implements Parcelable {
    public static final byte[] ENABLE_NOTIFICATION_VALUE = {0x01, 0x00};
    public static final byte[] ENABLE_INDICATION_VALUE = {0x02, 0x00};
    public static final byte[] DISABLE_NOTIFICATION_VALUE = {0x00, 0x00};

    public static final int PERMISSION_READ = 0x01;
    public static final int PERMISSION_READ_ENCRYPTED = 0x02;
    public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
    public static final int PERMISSION_WRITE = 0x10;
    public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
    public static final int PERMISSION_WRITE_SIGNED = 0x80;
    public static final int PERMISSION_WRITE_SIGNED_MITM = 0x100;

    private final UUID mUuid;
    private final int mPermissions;
    private int mInstance;
    private BluetoothGattCharacteristic mCharacteristic;
    private byte[] mValue;

    public BluetoothGattDescriptor(UUID uuid, int permissions) {
        mUuid = uuid;
        mPermissions = permissions;
    }

    BluetoothGattDescriptor(UUID uuid, int instance, int permissions) {
        mUuid = uuid;
        mInstance = instance;
        mPermissions = permissions;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        if (mUuid != null) {
            out.writeInt(1);
            out.writeLong(mUuid.getMostSignificantBits());
            out.writeLong(mUuid.getLeastSignificantBits());
        } else {
            out.writeInt(0);
        }
        out.writeInt(mInstance);
        out.writeInt(mPermissions);
        out.writeByteArray(mValue);
    }

    public static final Parcelable.Creator<BluetoothGattDescriptor> CREATOR = new Parcelable.Creator<BluetoothGattDescriptor>() {
        public BluetoothGattDescriptor createFromParcel(Parcel in) {
            UUID uuid = in.readInt() != 0 ? new UUID(in.readLong(), in.readLong()) : null;
            int instance = in.readInt();
            int permissions = in.readInt();
            byte[] value = in.createByteArray();
            BluetoothGattDescriptor desc = new BluetoothGattDescriptor(uuid, instance, permissions);
            desc.mValue = value;
            return desc;
        }

        public BluetoothGattDescriptor[] newArray(int size) {
            return new BluetoothGattDescriptor[size];
        }
    };

    public BluetoothGattCharacteristic getCharacteristic() {
        return mCharacteristic;
    }

    void setCharacteristic(BluetoothGattCharacteristic characteristic) {
        mCharacteristic = characteristic;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public int getPermissions() {
        return mPermissions;
    }

    public byte[] getValue() {
        return mValue;
    }

    public boolean setValue(byte[] value) {
        mValue = value;
        return true;
    }
}
