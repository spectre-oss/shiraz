// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DeviceProductInfo implements Parcelable {
    public static final int CONNECTION_TO_SINK_UNKNOWN = 0;
    public static final int CONNECTION_TO_SINK_BUILT_IN = 1;
    public static final int CONNECTION_TO_SINK_DIRECT = 2;
    public static final int CONNECTION_TO_SINK_TRANSITIVE = 3;

    private final String mName;
    private final String mManufacturerPnpId;
    private final String mProductId;
    private final int mModelYear;
    private final int mManufactureYear;
    private final int mManufactureWeek;
    private final int mConnectionToSinkType;

    public DeviceProductInfo(
            String name,
            String manufacturerPnpId,
            String productId,
            int modelYear,
            int connectionToSinkType) {
        this(name, manufacturerPnpId, productId, modelYear, -1, -1, connectionToSinkType);
    }

    private DeviceProductInfo(
            String name,
            String manufacturerPnpId,
            String productId,
            int modelYear,
            int manufactureYear,
            int manufactureWeek,
            int connectionToSinkType) {
        mName = name;
        mManufacturerPnpId = Objects.requireNonNull(manufacturerPnpId, "manufacturerPnpId");
        mProductId = Objects.requireNonNull(productId, "productId");
        mModelYear = modelYear;
        mManufactureYear = manufactureYear;
        mManufactureWeek = manufactureWeek;
        mConnectionToSinkType = connectionToSinkType;
    }

    public String getName() {
        return mName;
    }

    public String getManufacturerPnpId() {
        return mManufacturerPnpId;
    }

    public String getProductId() {
        return mProductId;
    }

    public int getModelYear() {
        return mModelYear;
    }

    public int getManufactureYear() {
        return mManufactureYear;
    }

    public int getManufactureWeek() {
        return mManufactureWeek;
    }

    public int getConnectionToSinkType() {
        return mConnectionToSinkType;
    }

    @Override
    public String toString() {
        return "DeviceProductInfo{name=" + mName
                + ", manufacturerPnpId=" + mManufacturerPnpId
                + ", productId=" + mProductId
                + ", modelYear=" + mModelYear
                + ", manufactureYear=" + mManufactureYear
                + ", manufactureWeek=" + mManufactureWeek
                + ", connectionToSinkType=" + mConnectionToSinkType + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceProductInfo that = (DeviceProductInfo) o;
        return Objects.equals(mName, that.mName)
                && Objects.equals(mManufacturerPnpId, that.mManufacturerPnpId)
                && Objects.equals(mProductId, that.mProductId)
                && mModelYear == that.mModelYear
                && mManufactureYear == that.mManufactureYear
                && mManufactureWeek == that.mManufactureWeek
                && mConnectionToSinkType == that.mConnectionToSinkType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mName,
                mManufacturerPnpId,
                mProductId,
                Integer.valueOf(mModelYear),
                Integer.valueOf(mManufactureYear),
                Integer.valueOf(mManufactureWeek),
                Integer.valueOf(mConnectionToSinkType));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mManufacturerPnpId);
        dest.writeString(mProductId);
        dest.writeInt(mModelYear);
        dest.writeInt(mManufactureYear);
        dest.writeInt(mManufactureWeek);
        dest.writeInt(mConnectionToSinkType);
    }

    public static final Parcelable.Creator<DeviceProductInfo> CREATOR =
            new Parcelable.Creator<DeviceProductInfo>() {
                @Override
                public DeviceProductInfo createFromParcel(Parcel in) {
                    return new DeviceProductInfo(
                            in.readString(),
                            in.readString(),
                            in.readString(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt());
                }

                @Override
                public DeviceProductInfo[] newArray(int size) {
                    return new DeviceProductInfo[size];
                }
            };
}
