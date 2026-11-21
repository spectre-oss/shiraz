// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ScanFilter implements Parcelable {
    private final String mDeviceName;
    private final String mDeviceAddress;
    private final ParcelUuid mServiceUuid;
    private final ParcelUuid mServiceUuidMask;
    private final ParcelUuid mServiceSolicitationUuid;
    private final ParcelUuid mServiceSolicitationUuidMask;
    private final ParcelUuid mServiceDataUuid;
    private final byte[] mServiceData;
    private final byte[] mServiceDataMask;
    private final int mManufacturerId;
    private final byte[] mManufacturerData;
    private final byte[] mManufacturerDataMask;
    private final int mAdvertisingDataType;
    private final byte[] mAdvertisingData;
    private final byte[] mAdvertisingDataMask;

    ScanFilter(String name, String deviceAddress, ParcelUuid uuid, ParcelUuid uuidMask,
            ParcelUuid solicitationUuid, ParcelUuid solicitationUuidMask,
            ParcelUuid serviceDataUuid, byte[] serviceData, byte[] serviceDataMask,
            int manufacturerId, byte[] manufacturerData, byte[] manufacturerDataMask,
            int advertisingDataType, byte[] advertisingData, byte[] advertisingDataMask) {
        mDeviceName = name;
        mServiceUuid = uuid;
        mServiceUuidMask = uuidMask;
        mServiceSolicitationUuid = solicitationUuid;
        mServiceSolicitationUuidMask = solicitationUuidMask;
        mDeviceAddress = deviceAddress;
        mServiceDataUuid = serviceDataUuid;
        mServiceData = serviceData != null ? serviceData.clone() : null;
        mServiceDataMask = serviceDataMask != null ? serviceDataMask.clone() : null;
        mManufacturerId = manufacturerId;
        mManufacturerData = manufacturerData != null ? manufacturerData.clone() : null;
        mManufacturerDataMask = manufacturerDataMask != null ? manufacturerDataMask.clone() : null;
        mAdvertisingDataType = advertisingDataType;
        mAdvertisingData = advertisingData != null ? advertisingData.clone() : null;
        mAdvertisingDataMask = advertisingDataMask != null ? advertisingDataMask.clone() : null;
    }

    public int describeContents() {
        return 0;
    }

    private static void writeUuid(Parcel dest, ParcelUuid pu) {
        if (pu == null) {
            dest.writeInt(0);
        } else {
            dest.writeInt(1);
            UUID u = pu.getUuid();
            dest.writeLong(u.getMostSignificantBits());
            dest.writeLong(u.getLeastSignificantBits());
        }
    }

    private static ParcelUuid readUuid(Parcel in) {
        if (in.readInt() == 0) {
            return null;
        }
        return new ParcelUuid(new UUID(in.readLong(), in.readLong()));
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceName);
        dest.writeString(mDeviceAddress);
        writeUuid(dest, mServiceUuid);
        writeUuid(dest, mServiceUuidMask);
        writeUuid(dest, mServiceSolicitationUuid);
        writeUuid(dest, mServiceSolicitationUuidMask);
        writeUuid(dest, mServiceDataUuid);
        dest.writeByteArray(mServiceData);
        dest.writeByteArray(mServiceDataMask);
        dest.writeInt(mManufacturerId);
        dest.writeByteArray(mManufacturerData);
        dest.writeByteArray(mManufacturerDataMask);
        dest.writeInt(mAdvertisingDataType);
        dest.writeByteArray(mAdvertisingData);
        dest.writeByteArray(mAdvertisingDataMask);
    }

    public static final Parcelable.Creator<ScanFilter> CREATOR = new Parcelable.Creator<ScanFilter>() {
        public ScanFilter createFromParcel(Parcel in) {
            String name = in.readString();
            String addr = in.readString();
            ParcelUuid svcUuid = readUuid(in);
            ParcelUuid svcMask = readUuid(in);
            ParcelUuid solUuid = readUuid(in);
            ParcelUuid solMask = readUuid(in);
            ParcelUuid sdUuid = readUuid(in);
            byte[] sd = in.createByteArray();
            byte[] sdMask = in.createByteArray();
            int mfgId = in.readInt();
            byte[] mfgData = in.createByteArray();
            byte[] mfgMask = in.createByteArray();
            int adType = in.readInt();
            byte[] adData = in.createByteArray();
            byte[] adMask = in.createByteArray();
            return new ScanFilter(name, addr, svcUuid, svcMask, solUuid, solMask,
                    sdUuid, sd, sdMask, mfgId, mfgData, mfgMask, adType, adData, adMask);
        }

        public ScanFilter[] newArray(int size) {
            return new ScanFilter[size];
        }
    };

    public String getDeviceName() {
        return mDeviceName;
    }

    public ParcelUuid getServiceUuid() {
        return mServiceUuid;
    }

    public ParcelUuid getServiceUuidMask() {
        return mServiceUuidMask;
    }

    public ParcelUuid getServiceSolicitationUuid() {
        return mServiceSolicitationUuid;
    }

    public ParcelUuid getServiceSolicitationUuidMask() {
        return mServiceSolicitationUuidMask;
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public byte[] getServiceData() {
        return mServiceData != null ? mServiceData.clone() : null;
    }

    public byte[] getServiceDataMask() {
        return mServiceDataMask != null ? mServiceDataMask.clone() : null;
    }

    public ParcelUuid getServiceDataUuid() {
        return mServiceDataUuid;
    }

    public int getManufacturerId() {
        return mManufacturerId;
    }

    public byte[] getManufacturerData() {
        return mManufacturerData != null ? mManufacturerData.clone() : null;
    }

    public byte[] getManufacturerDataMask() {
        return mManufacturerDataMask != null ? mManufacturerDataMask.clone() : null;
    }

    public int getAdvertisingDataType() {
        return mAdvertisingDataType;
    }

    public byte[] getAdvertisingData() {
        return mAdvertisingData != null ? mAdvertisingData.clone() : null;
    }

    public byte[] getAdvertisingDataMask() {
        return mAdvertisingDataMask != null ? mAdvertisingDataMask.clone() : null;
    }

    public boolean matches(ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        BluetoothDevice device = scanResult.getDevice();
        if (mDeviceAddress != null && (device == null || !mDeviceAddress.equals(device.getAddress()))) {
            return false;
        }
        ScanRecord scanRecord = scanResult.getScanRecord();
        if (scanRecord == null
                && (mDeviceName != null || mServiceUuid != null || mManufacturerId >= 0
                || mServiceDataUuid != null || mServiceSolicitationUuid != null
                || mAdvertisingDataType >= 0)) {
            return false;
        }
        if (mDeviceName != null && !mDeviceName.equals(scanRecord.getDeviceName())) {
            return false;
        }
        if (mServiceUuid != null && !matchesServiceUuids(mServiceUuid, mServiceUuidMask, scanRecord.getServiceUuids())) {
            return false;
        }
        if (mServiceSolicitationUuid != null
                && !matchesServiceUuids(mServiceSolicitationUuid, mServiceSolicitationUuidMask, scanRecord.getServiceSolicitationUuids())) {
            return false;
        }
        if (mServiceDataUuid != null) {
            if (!matchesPartialData(mServiceData, mServiceDataMask, scanRecord.getServiceData(mServiceDataUuid))) {
                return false;
            }
        }
        if (mManufacturerId >= 0) {
            if (!matchesPartialData(mManufacturerData, mManufacturerDataMask, scanRecord.getManufacturerSpecificData(mManufacturerId))) {
                return false;
            }
        }
        if (mAdvertisingDataType >= 0) {
            byte[] advertisingData = scanRecord.getAdvertisingDataMap().get(mAdvertisingDataType);
            if (!matchesPartialData(mAdvertisingData, mAdvertisingDataMask, advertisingData)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesServiceUuids(ParcelUuid uuid, ParcelUuid parcelUuidMask, List<ParcelUuid> uuids) {
        if (uuid == null) {
            return true;
        }
        if (uuids == null) {
            return false;
        }
        for (ParcelUuid parcelUuid : uuids) {
            UUID uuidMask = parcelUuidMask == null ? null : parcelUuidMask.getUuid();
            if (matchesServiceUuid(uuid.getUuid(), uuidMask, parcelUuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesServiceUuid(UUID uuid, UUID mask, UUID data) {
        if (mask == null) {
            return Objects.equals(data, uuid);
        }
        return (uuid.getLeastSignificantBits() & mask.getLeastSignificantBits())
                == (data.getLeastSignificantBits() & mask.getLeastSignificantBits())
                && (uuid.getMostSignificantBits() & mask.getMostSignificantBits())
                == (data.getMostSignificantBits() & mask.getMostSignificantBits());
    }

    private static boolean matchesPartialData(byte[] data, byte[] dataMask, byte[] parsedData) {
        if (parsedData == null) {
            return false;
        }
        if (data == null) {
            return true;
        }
        if (parsedData.length < data.length) {
            return false;
        }
        if (dataMask == null) {
            for (int i = 0; i < data.length; i++) {
                if (parsedData[i] != data[i]) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < data.length; i++) {
            if ((dataMask[i] & parsedData[i]) != (dataMask[i] & data[i])) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "BluetoothLeScanFilter [mDeviceName=" + mDeviceName
                + ", mDeviceAddress=" + mDeviceAddress
                + ", mUuid=" + mServiceUuid + ", mUuidMask=" + mServiceUuidMask
                + ", mServiceSolicitationUuid=" + mServiceSolicitationUuid
                + ", mServiceSolicitationUuidMask=" + mServiceSolicitationUuidMask
                + ", mServiceDataUuid=" + mServiceDataUuid
                + ", mServiceData=" + Arrays.toString(mServiceData)
                + ", mServiceDataMask=" + Arrays.toString(mServiceDataMask)
                + ", mManufacturerId=" + mManufacturerId
                + ", mManufacturerData=" + Arrays.toString(mManufacturerData)
                + ", mManufacturerDataMask=" + Arrays.toString(mManufacturerDataMask)
                + ", mAdvertisingDataType=" + mAdvertisingDataType
                + ", mAdvertisingData=" + Arrays.toString(mAdvertisingData)
                + ", mAdvertisingDataMask=" + Arrays.toString(mAdvertisingDataMask) + "]";
    }

    public int hashCode() {
        return Objects.hash(
                mDeviceName, mDeviceAddress, mManufacturerId,
                Arrays.hashCode(mManufacturerData), Arrays.hashCode(mManufacturerDataMask),
                mServiceDataUuid, Arrays.hashCode(mServiceData), Arrays.hashCode(mServiceDataMask),
                mServiceUuid, mServiceUuidMask,
                mServiceSolicitationUuid, mServiceSolicitationUuidMask,
                mAdvertisingDataType, Arrays.hashCode(mAdvertisingData), Arrays.hashCode(mAdvertisingDataMask));
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ScanFilter)) {
            return false;
        }
        ScanFilter other = (ScanFilter) obj;
        return Objects.equals(mDeviceName, other.mDeviceName)
                && Objects.equals(mDeviceAddress, other.mDeviceAddress)
                && mManufacturerId == other.mManufacturerId
                && Arrays.equals(mManufacturerData, other.mManufacturerData)
                && Arrays.equals(mManufacturerDataMask, other.mManufacturerDataMask)
                && Objects.equals(mServiceDataUuid, other.mServiceDataUuid)
                && Arrays.equals(mServiceData, other.mServiceData)
                && Arrays.equals(mServiceDataMask, other.mServiceDataMask)
                && Objects.equals(mServiceUuid, other.mServiceUuid)
                && Objects.equals(mServiceUuidMask, other.mServiceUuidMask)
                && Objects.equals(mServiceSolicitationUuid, other.mServiceSolicitationUuid)
                && Objects.equals(mServiceSolicitationUuidMask, other.mServiceSolicitationUuidMask)
                && mAdvertisingDataType == other.mAdvertisingDataType
                && Arrays.equals(mAdvertisingData, other.mAdvertisingData)
                && Arrays.equals(mAdvertisingDataMask, other.mAdvertisingDataMask);
    }

    public static final class Builder {
        private String mDeviceName;
        private String mDeviceAddress;
        private ParcelUuid mServiceUuid;
        private ParcelUuid mUuidMask;
        private ParcelUuid mSolicitationUuid;
        private ParcelUuid mSolicitationUuidMask;
        private ParcelUuid mServiceDataUuid;
        private byte[] mServiceData;
        private byte[] mServiceDataMask;
        private int mManufacturerId = -1;
        private byte[] mManufacturerData;
        private byte[] mManufacturerDataMask;
        private int mAdvertisingDataType = ScanRecord.DATA_TYPE_NONE;
        private byte[] mAdvertisingData;
        private byte[] mAdvertisingDataMask;

        public Builder() {}

        public Builder setDeviceName(String deviceName) {
            mDeviceName = deviceName;
            return this;
        }

        public Builder setDeviceAddress(String deviceAddress) {
            if (deviceAddress != null && !BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                throw new IllegalArgumentException("invalid device address " + deviceAddress);
            }
            mDeviceAddress = deviceAddress;
            return this;
        }

        public Builder setServiceUuid(ParcelUuid serviceUuid) {
            mServiceUuid = serviceUuid;
            mUuidMask = null;
            return this;
        }

        public Builder setServiceUuid(ParcelUuid serviceUuid, ParcelUuid uuidMask) {
            if (uuidMask != null && serviceUuid == null) {
                throw new IllegalArgumentException("uuid is null while uuidMask is not null!");
            }
            mServiceUuid = serviceUuid;
            mUuidMask = uuidMask;
            return this;
        }

        public Builder setServiceSolicitationUuid(ParcelUuid serviceSolicitationUuid) {
            mSolicitationUuid = serviceSolicitationUuid;
            mSolicitationUuidMask = null;
            return this;
        }

        public Builder setServiceSolicitationUuid(ParcelUuid serviceSolicitationUuid, ParcelUuid solicitationUuidMask) {
            if (solicitationUuidMask != null && serviceSolicitationUuid == null) {
                throw new IllegalArgumentException("SolicitationUuid is null while SolicitationUuidMask is not null!");
            }
            mSolicitationUuid = serviceSolicitationUuid;
            mSolicitationUuidMask = solicitationUuidMask;
            return this;
        }

        public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData) {
            if (serviceDataUuid == null) {
                throw new IllegalArgumentException("serviceDataUuid is null");
            }
            mServiceDataUuid = serviceDataUuid;
            mServiceData = serviceData != null ? serviceData.clone() : null;
            mServiceDataMask = null;
            return this;
        }

        public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData, byte[] serviceDataMask) {
            if (serviceDataUuid == null) {
                throw new IllegalArgumentException("serviceDataUuid is null");
            }
            if (serviceDataMask != null) {
                if (serviceData == null) {
                    throw new IllegalArgumentException("serviceData is null while serviceDataMask is not null");
                }
                if (serviceData.length != serviceDataMask.length) {
                    throw new IllegalArgumentException("size mismatch for service data and service data mask");
                }
            }
            mServiceDataUuid = serviceDataUuid;
            mServiceData = serviceData != null ? serviceData.clone() : null;
            mServiceDataMask = serviceDataMask != null ? serviceDataMask.clone() : null;
            return this;
        }

        public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData) {
            if (manufacturerData != null && manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacture id");
            }
            mManufacturerId = manufacturerId;
            mManufacturerData = manufacturerData != null ? manufacturerData.clone() : null;
            mManufacturerDataMask = null;
            return this;
        }

        public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData, byte[] manufacturerDataMask) {
            if (manufacturerData != null && manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacture id");
            }
            if (manufacturerDataMask != null) {
                if (manufacturerData == null) {
                    throw new IllegalArgumentException("manufacturerData is null while manufacturerDataMask is not null");
                }
                if (manufacturerData.length != manufacturerDataMask.length) {
                    throw new IllegalArgumentException("size mismatch for manufacturerData and manufacturerDataMask");
                }
            }
            mManufacturerId = manufacturerId;
            mManufacturerData = manufacturerData != null ? manufacturerData.clone() : null;
            mManufacturerDataMask = manufacturerDataMask != null ? manufacturerDataMask.clone() : null;
            return this;
        }

        public Builder setAdvertisingDataTypeWithData(int advertisingDataType, byte[] advertisingData, byte[] advertisingDataMask) {
            if (advertisingDataType < 0) {
                throw new IllegalArgumentException("invalid advertising data type");
            }
            if (advertisingData == null) {
                throw new NullPointerException("advertisingData is null");
            }
            if (advertisingDataMask == null) {
                throw new NullPointerException("advertisingDataMask is null");
            }
            if (advertisingData.length != advertisingDataMask.length) {
                throw new IllegalArgumentException("size mismatch for advertisingData and advertisingDataMask");
            }
            mAdvertisingDataType = advertisingDataType;
            mAdvertisingData = advertisingData.clone();
            mAdvertisingDataMask = advertisingDataMask.clone();
            return this;
        }

        public Builder setAdvertisingDataType(int advertisingDataType) {
            if (advertisingDataType < 0) {
                throw new IllegalArgumentException("invalid advertising data type");
            }
            mAdvertisingDataType = advertisingDataType;
            mAdvertisingData = null;
            mAdvertisingDataMask = null;
            return this;
        }

        public ScanFilter build() {
            return new ScanFilter(mDeviceName, mDeviceAddress, mServiceUuid, mUuidMask,
                    mSolicitationUuid, mSolicitationUuidMask, mServiceDataUuid, mServiceData, mServiceDataMask,
                    mManufacturerId, mManufacturerData, mManufacturerDataMask,
                    mAdvertisingDataType, mAdvertisingData, mAdvertisingDataMask);
        }
    }
}
