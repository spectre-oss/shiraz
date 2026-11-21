// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothGattService implements Parcelable {
    public static final int SERVICE_TYPE_PRIMARY = 0;
    public static final int SERVICE_TYPE_SECONDARY = 1;

    protected List<BluetoothGattCharacteristic> mCharacteristics;
    protected List<BluetoothGattService> mIncludedServices;

    private final UUID mUuid;
    private int mInstanceId;
    private final int mServiceType;

    public BluetoothGattService(UUID uuid, int serviceType) {
        mUuid = uuid;
        mServiceType = serviceType;
        mCharacteristics = new ArrayList<BluetoothGattCharacteristic>();
        mIncludedServices = new ArrayList<BluetoothGattService>();
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
        out.writeInt(mInstanceId);
        out.writeInt(mServiceType);
        out.writeTypedList(mCharacteristics);
        out.writeTypedList(mIncludedServices);
    }

    public static final Parcelable.Creator<BluetoothGattService> CREATOR = new Parcelable.Creator<BluetoothGattService>() {
        public BluetoothGattService createFromParcel(Parcel in) {
            UUID uuid = in.readInt() != 0 ? new UUID(in.readLong(), in.readLong()) : null;
            int inst = in.readInt();
            int type = in.readInt();
            ArrayList<BluetoothGattCharacteristic> chars = in.createTypedArrayList(BluetoothGattCharacteristic.CREATOR);
            ArrayList<BluetoothGattService> included = in.createTypedArrayList(BluetoothGattService.CREATOR);
            BluetoothGattService svc = new BluetoothGattService(uuid, type);
            svc.mInstanceId = inst;
            if (chars != null) {
                for (BluetoothGattCharacteristic c : chars) {
                    if (c != null) {
                        svc.addCharacteristic(c);
                    }
                }
            }
            if (included != null) {
                for (BluetoothGattService inc : included) {
                    if (inc != null) {
                        svc.addService(inc);
                    }
                }
            }
            return svc;
        }

        public BluetoothGattService[] newArray(int size) {
            return new BluetoothGattService[size];
        }
    };

    public boolean addService(BluetoothGattService service) {
        if (service == null) {
            return false;
        }
        mIncludedServices.add(service);
        return true;
    }

    public boolean addCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return false;
        }
        mCharacteristics.add(characteristic);
        characteristic.setService(this);
        return true;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public int getInstanceId() {
        return mInstanceId;
    }

    public int getType() {
        return mServiceType;
    }

    public List<BluetoothGattService> getIncludedServices() {
        return mIncludedServices;
    }

    public List<BluetoothGattCharacteristic> getCharacteristics() {
        return mCharacteristics;
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        for (BluetoothGattCharacteristic characteristic : mCharacteristics) {
            if (uuid != null && uuid.equals(characteristic.getUuid())) {
                return characteristic;
            }
        }
        return null;
    }
}
