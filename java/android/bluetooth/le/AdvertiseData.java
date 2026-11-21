// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.SparseArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AdvertiseData implements Parcelable {
    private final List<ParcelUuid> mServiceUuids;
    private final List<ParcelUuid> mServiceSolicitationUuids;
    private final List<TransportDiscoveryData> mTransportDiscoveryData;
    private final SparseArray<byte[]> mManufacturerSpecificData;
    private final Map<ParcelUuid, byte[]> mServiceData;
    private final boolean mIncludeTxPowerLevel;
    private final boolean mIncludeDeviceName;

    AdvertiseData(
            List<ParcelUuid> serviceUuids,
            List<ParcelUuid> serviceSolicitationUuids,
            List<TransportDiscoveryData> transportDiscoveryData,
            SparseArray<byte[]> manufacturerData,
            Map<ParcelUuid, byte[]> serviceData,
            boolean includeTxPowerLevel,
            boolean includeDeviceName) {
        mServiceUuids = serviceUuids != null
                ? Collections.unmodifiableList(new ArrayList<ParcelUuid>(serviceUuids))
                : Collections.<ParcelUuid>emptyList();
        mServiceSolicitationUuids = serviceSolicitationUuids != null
                ? Collections.unmodifiableList(new ArrayList<ParcelUuid>(serviceSolicitationUuids))
                : Collections.<ParcelUuid>emptyList();
        mTransportDiscoveryData = transportDiscoveryData != null
                ? Collections.unmodifiableList(new ArrayList<TransportDiscoveryData>(transportDiscoveryData))
                : Collections.<TransportDiscoveryData>emptyList();
        mManufacturerSpecificData = new SparseArray<byte[]>();
        if (manufacturerData != null) {
            for (int i = 0; i < manufacturerData.size(); i++) {
                byte[] v = manufacturerData.valueAt(i);
                mManufacturerSpecificData.put(manufacturerData.keyAt(i), v != null ? v.clone() : null);
            }
        }
        Map<ParcelUuid, byte[]> sdCopy = new LinkedHashMap<ParcelUuid, byte[]>();
        if (serviceData != null) {
            for (Map.Entry<ParcelUuid, byte[]> e : serviceData.entrySet()) {
                byte[] v = e.getValue();
                sdCopy.put(e.getKey(), v != null ? v.clone() : null);
            }
        }
        mServiceData = Collections.unmodifiableMap(sdCopy);
        mIncludeTxPowerLevel = includeTxPowerLevel;
        mIncludeDeviceName = includeDeviceName;
    }

    public List<ParcelUuid> getServiceUuids() {
        return mServiceUuids;
    }

    public List<ParcelUuid> getServiceSolicitationUuids() {
        return mServiceSolicitationUuids;
    }

    public List<TransportDiscoveryData> getTransportDiscoveryData() {
        return mTransportDiscoveryData;
    }

    public SparseArray<byte[]> getManufacturerSpecificData() {
        return mManufacturerSpecificData;
    }

    public Map<ParcelUuid, byte[]> getServiceData() {
        return mServiceData;
    }

    public boolean getIncludeTxPowerLevel() {
        return mIncludeTxPowerLevel;
    }

    public boolean getIncludeDeviceName() {
        return mIncludeDeviceName;
    }

    public int hashCode() {
        int h = Objects.hash(mServiceUuids, mServiceSolicitationUuids, mTransportDiscoveryData,
                mIncludeDeviceName, mIncludeTxPowerLevel);
        for (int i = 0; i < mManufacturerSpecificData.size(); i++) {
            h = 31 * h + mManufacturerSpecificData.keyAt(i);
            h = 31 * h + Arrays.hashCode(mManufacturerSpecificData.valueAt(i));
        }
        for (Map.Entry<ParcelUuid, byte[]> e : mServiceData.entrySet()) {
            h = 31 * h + Objects.hashCode(e.getKey());
            h = 31 * h + Arrays.hashCode(e.getValue());
        }
        return h;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AdvertiseData)) {
            return false;
        }
        AdvertiseData other = (AdvertiseData) obj;
        if (!Objects.equals(mServiceUuids, other.mServiceUuids)
                || !Objects.equals(mServiceSolicitationUuids, other.mServiceSolicitationUuids)
                || !Objects.equals(mTransportDiscoveryData, other.mTransportDiscoveryData)
                || mIncludeDeviceName != other.mIncludeDeviceName
                || mIncludeTxPowerLevel != other.mIncludeTxPowerLevel) {
            return false;
        }
        if (mManufacturerSpecificData.size() != other.mManufacturerSpecificData.size()) {
            return false;
        }
        for (int i = 0; i < mManufacturerSpecificData.size(); i++) {
            if (mManufacturerSpecificData.keyAt(i) != other.mManufacturerSpecificData.keyAt(i)
                    || !Arrays.equals(mManufacturerSpecificData.valueAt(i), other.mManufacturerSpecificData.valueAt(i))) {
                return false;
            }
        }
        if (mServiceData.size() != other.mServiceData.size()) {
            return false;
        }
        for (Map.Entry<ParcelUuid, byte[]> e : mServiceData.entrySet()) {
            if (!other.mServiceData.containsKey(e.getKey())
                    || !Arrays.equals(e.getValue(), other.mServiceData.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "AdvertiseData [mServiceUuids=" + mServiceUuids
                + ", mServiceSolicitationUuids=" + mServiceSolicitationUuids
                + ", mTransportDiscoveryData=" + mTransportDiscoveryData
                + ", mIncludeTxPowerLevel=" + mIncludeTxPowerLevel
                + ", mIncludeDeviceName=" + mIncludeDeviceName + "]";
    }

    public int describeContents() {
        return 0;
    }

    private static void writeUuidList(Parcel dest, List<ParcelUuid> uuids) {
        if (uuids == null) {
            dest.writeInt(-1);
            return;
        }
        dest.writeInt(uuids.size());
        for (ParcelUuid pu : uuids) {
            if (pu == null) {
                dest.writeInt(0);
            } else {
                dest.writeInt(1);
                UUID u = pu.getUuid();
                dest.writeLong(u.getMostSignificantBits());
                dest.writeLong(u.getLeastSignificantBits());
            }
        }
    }

    private static List<ParcelUuid> readUuidList(Parcel in) {
        int n = in.readInt();
        if (n < 0) {
            return Collections.emptyList();
        }
        List<ParcelUuid> list = new ArrayList<ParcelUuid>(n);
        for (int i = 0; i < n; i++) {
            if (in.readInt() != 0) {
                list.add(new ParcelUuid(new UUID(in.readLong(), in.readLong())));
            } else {
                list.add(null);
            }
        }
        return list;
    }

    public void writeToParcel(Parcel dest, int flags) {
        writeUuidList(dest, mServiceUuids);
        writeUuidList(dest, mServiceSolicitationUuids);
        dest.writeTypedList(mTransportDiscoveryData);
        dest.writeInt(mManufacturerSpecificData.size());
        for (int i = 0; i < mManufacturerSpecificData.size(); i++) {
            dest.writeInt(mManufacturerSpecificData.keyAt(i));
            dest.writeByteArray(mManufacturerSpecificData.valueAt(i));
        }
        dest.writeInt(mServiceData.size());
        for (Map.Entry<ParcelUuid, byte[]> entry : mServiceData.entrySet()) {
            ParcelUuid pu = entry.getKey();
            UUID u = pu.getUuid();
            dest.writeLong(u.getMostSignificantBits());
            dest.writeLong(u.getLeastSignificantBits());
            dest.writeByteArray(entry.getValue());
        }
        dest.writeByte((byte) (mIncludeTxPowerLevel ? 1 : 0));
        dest.writeByte((byte) (mIncludeDeviceName ? 1 : 0));
    }

    public static final Parcelable.Creator<AdvertiseData> CREATOR = new Parcelable.Creator<AdvertiseData>() {
        public AdvertiseData createFromParcel(Parcel in) {
            List<ParcelUuid> serviceUuids = readUuidList(in);
            List<ParcelUuid> solicitationUuids = readUuidList(in);
            List<TransportDiscoveryData> tdd = in.createTypedArrayList(TransportDiscoveryData.CREATOR);
            int mfgSize = in.readInt();
            SparseArray<byte[]> mfg = new SparseArray<byte[]>(mfgSize);
            for (int i = 0; i < mfgSize; i++) {
                int id = in.readInt();
                byte[] data = in.createByteArray();
                mfg.put(id, data);
            }
            int sdSize = in.readInt();
            Map<ParcelUuid, byte[]> sd = new LinkedHashMap<ParcelUuid, byte[]>();
            for (int i = 0; i < sdSize; i++) {
                ParcelUuid pu = new ParcelUuid(new UUID(in.readLong(), in.readLong()));
                byte[] data = in.createByteArray();
                sd.put(pu, data);
            }
            boolean txPower = in.readByte() == 1;
            boolean devName = in.readByte() == 1;
            return new AdvertiseData(serviceUuids, solicitationUuids, tdd, mfg, sd, txPower, devName);
        }

        public AdvertiseData[] newArray(int size) {
            return new AdvertiseData[size];
        }
    };

    public static final class Builder {
        private final List<ParcelUuid> mServiceUuids = new ArrayList<ParcelUuid>();
        private final List<ParcelUuid> mServiceSolicitationUuids = new ArrayList<ParcelUuid>();
        private final List<TransportDiscoveryData> mTransportDiscoveryData = new ArrayList<TransportDiscoveryData>();
        private final SparseArray<byte[]> mManufacturerSpecificData = new SparseArray<byte[]>();
        private final Map<ParcelUuid, byte[]> mServiceData = new LinkedHashMap<ParcelUuid, byte[]>();
        private boolean mIncludeTxPowerLevel;
        private boolean mIncludeDeviceName;

        public Builder() {}

        public Builder addServiceUuid(ParcelUuid serviceUuid) {
            if (serviceUuid == null) {
                throw new IllegalArgumentException("serviceUuid is null");
            }
            mServiceUuids.add(serviceUuid);
            return this;
        }

        public Builder addServiceSolicitationUuid(ParcelUuid serviceSolicitationUuid) {
            if (serviceSolicitationUuid == null) {
                throw new IllegalArgumentException("serviceSolicitationUuid is null");
            }
            mServiceSolicitationUuids.add(serviceSolicitationUuid);
            return this;
        }

        public Builder addServiceData(ParcelUuid serviceDataUuid, byte[] serviceData) {
            if (serviceDataUuid == null || serviceData == null) {
                throw new IllegalArgumentException("serviceDataUuid or serviceDataUuid is null");
            }
            mServiceData.put(serviceDataUuid, serviceData.clone());
            return this;
        }

        public Builder addTransportDiscoveryData(TransportDiscoveryData transportDiscoveryData) {
            if (transportDiscoveryData == null) {
                throw new IllegalArgumentException("transportDiscoveryData is null");
            }
            mTransportDiscoveryData.add(transportDiscoveryData);
            return this;
        }

        public Builder addManufacturerData(int manufacturerId, byte[] manufacturerSpecificData) {
            if (manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacturerId - " + manufacturerId);
            }
            if (manufacturerSpecificData == null) {
                throw new IllegalArgumentException("manufacturerSpecificData is null");
            }
            mManufacturerSpecificData.put(manufacturerId, manufacturerSpecificData.clone());
            return this;
        }

        public Builder setIncludeTxPowerLevel(boolean includeTxPowerLevel) {
            mIncludeTxPowerLevel = includeTxPowerLevel;
            return this;
        }

        public Builder setIncludeDeviceName(boolean includeDeviceName) {
            mIncludeDeviceName = includeDeviceName;
            return this;
        }

        public AdvertiseData build() {
            return new AdvertiseData(mServiceUuids, mServiceSolicitationUuids, mTransportDiscoveryData,
                    mManufacturerSpecificData, mServiceData, mIncludeTxPowerLevel, mIncludeDeviceName);
        }
    }
}
