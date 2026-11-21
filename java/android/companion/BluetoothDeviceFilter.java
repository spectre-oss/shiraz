// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BluetoothDeviceFilter implements DeviceFilter<BluetoothDevice> {
    private final Pattern mNamePattern;
    private final String mAddress;
    private final List<ParcelUuid> mServiceUuids;
    private final List<ParcelUuid> mServiceUuidMasks;

    BluetoothDeviceFilter(Pattern namePattern, String address,
            List<ParcelUuid> serviceUuids, List<ParcelUuid> serviceUuidMasks) {
        mNamePattern = namePattern;
        mAddress = address;
        mServiceUuids = new ArrayList<>(serviceUuids);
        mServiceUuidMasks = new ArrayList<>(serviceUuidMasks);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern != null ? mNamePattern.pattern() : null);
        dest.writeString(mAddress);
        dest.writeInt(mServiceUuids.size());
        for (int i = 0; i < mServiceUuids.size(); i++) {
            writeUuid(dest, mServiceUuids.get(i));
            writeUuid(dest, mServiceUuidMasks.get(i));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BluetoothDeviceFilter)) return false;
        BluetoothDeviceFilter that = (BluetoothDeviceFilter) o;
        String p1 = mNamePattern != null ? mNamePattern.pattern() : null;
        String p2 = that.mNamePattern != null ? that.mNamePattern.pattern() : null;
        return Objects.equals(p1, p2)
                && Objects.equals(mAddress, that.mAddress)
                && Objects.equals(mServiceUuids, that.mServiceUuids)
                && Objects.equals(mServiceUuidMasks, that.mServiceUuidMasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNamePattern != null ? mNamePattern.pattern() : null,
                mAddress, mServiceUuids, mServiceUuidMasks);
    }

    @Override
    public String toString() {
        return "BluetoothDeviceFilter{namePattern=" + mNamePattern
                + ", address=" + mAddress + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private static void writeUuid(Parcel dest, ParcelUuid pu) {
        if (pu == null || pu.getUuid() == null) {
            dest.writeBoolean(false);
        } else {
            dest.writeBoolean(true);
            UUID u = pu.getUuid();
            dest.writeLong(u.getMostSignificantBits());
            dest.writeLong(u.getLeastSignificantBits());
        }
    }

    private static ParcelUuid readUuid(Parcel in) {
        if (!in.readBoolean()) return null;
        return new ParcelUuid(new UUID(in.readLong(), in.readLong()));
    }

    public static final Creator<BluetoothDeviceFilter> CREATOR = new Creator<BluetoothDeviceFilter>() {
        @Override
        public BluetoothDeviceFilter createFromParcel(Parcel in) {
            String pat = in.readString();
            String addr = in.readString();
            int count = in.readInt();
            List<ParcelUuid> uuids = new ArrayList<>(count);
            List<ParcelUuid> masks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                uuids.add(readUuid(in));
                masks.add(readUuid(in));
            }
            return new BluetoothDeviceFilter(
                    pat != null ? Pattern.compile(pat) : null, addr, uuids, masks);
        }

        @Override
        public BluetoothDeviceFilter[] newArray(int size) {
            return new BluetoothDeviceFilter[size];
        }
    };

    public static final class Builder {
        private Pattern mNamePattern;
        private String mAddress;
        private final List<ParcelUuid> mServiceUuids = new ArrayList<>();
        private final List<ParcelUuid> mServiceUuidMasks = new ArrayList<>();

        public Builder() {
        }

        public Builder setNamePattern(Pattern regex) {
            mNamePattern = regex;
            return this;
        }

        public Builder setAddress(String address) {
            mAddress = address;
            return this;
        }

        public Builder addServiceUuid(ParcelUuid serviceUuid, ParcelUuid serviceUuidMask) {
            mServiceUuids.add(serviceUuid);
            mServiceUuidMasks.add(serviceUuidMask);
            return this;
        }

        public BluetoothDeviceFilter build() {
            return new BluetoothDeviceFilter(mNamePattern, mAddress,
                    mServiceUuids, mServiceUuidMasks);
        }
    }
}
