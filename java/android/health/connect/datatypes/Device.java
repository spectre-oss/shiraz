// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.util.Objects;

public final class Device {
    public static final int DEVICE_TYPE_UNKNOWN = 0;
    public static final int DEVICE_TYPE_WATCH = 1;
    public static final int DEVICE_TYPE_PHONE = 2;
    public static final int DEVICE_TYPE_SCALE = 3;
    public static final int DEVICE_TYPE_RING = 4;
    public static final int DEVICE_TYPE_HEAD_MOUNTED = 5;
    public static final int DEVICE_TYPE_FITNESS_BAND = 6;
    public static final int DEVICE_TYPE_CHEST_STRAP = 7;
    public static final int DEVICE_TYPE_SMART_DISPLAY = 8;

    private final String mManufacturer;
    private final String mModel;
    private final int mType;

    Device(String manufacturer, String model, int type) {
        mManufacturer = manufacturer;
        mModel = model;
        mType = type;
    }

    public String getManufacturer() {
        return mManufacturer;
    }

    public String getModel() {
        return mModel;
    }

    public int getType() {
        return mType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Device)) return false;
        Device that = (Device) object;
        return mType == that.mType
                && Objects.equals(mManufacturer, that.mManufacturer)
                && Objects.equals(mModel, that.mModel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mManufacturer, mModel, mType);
    }

    public static final class Builder {
        private String mManufacturer;
        private String mModel;
        private int mType = DEVICE_TYPE_UNKNOWN;

        public Builder() {
        }

        public Builder setManufacturer(String manufacturer) {
            mManufacturer = manufacturer;
            return this;
        }

        public Builder setModel(String model) {
            mModel = model;
            return this;
        }

        public Builder setType(int type) {
            mType = type;
            return this;
        }

        public Device build() {
            return new Device(mManufacturer, mModel, mType);
        }
    }
}
