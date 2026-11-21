// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AssociationRequest implements Parcelable {
    public static final String DEVICE_PROFILE_WATCH = "android.app.role.COMPANION_DEVICE_WATCH";
    public static final String DEVICE_PROFILE_APP_STREAMING =
            "android.app.role.COMPANION_DEVICE_APP_STREAMING";
    public static final String DEVICE_PROFILE_AUTOMOTIVE_PROJECTION =
            "android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION";
    public static final String DEVICE_PROFILE_COMPUTER =
            "android.app.role.COMPANION_DEVICE_COMPUTER";
    public static final String DEVICE_PROFILE_GLASSES =
            "android.app.role.COMPANION_DEVICE_GLASSES";
    public static final String DEVICE_PROFILE_NEARBY_DEVICE_STREAMING =
            "android.app.role.COMPANION_DEVICE_NEARBY_DEVICE_STREAMING";

    private static final int FILTER_BT = 1;
    private static final int FILTER_BLE = 2;
    private static final int FILTER_WIFI = 3;

    private final boolean mSingleDevice;
    private final List<DeviceFilter<?>> mDeviceFilters;
    private final String mDeviceProfile;
    private final CharSequence mDisplayName;
    private final boolean mSelfManaged;
    private final boolean mForceConfirmation;

    AssociationRequest(boolean singleDevice, List<DeviceFilter<?>> deviceFilters,
            String deviceProfile, CharSequence displayName,
            boolean selfManaged, boolean forceConfirmation) {
        mSingleDevice = singleDevice;
        mDeviceFilters = new ArrayList<>(deviceFilters);
        mDeviceProfile = deviceProfile;
        mDisplayName = displayName;
        mSelfManaged = selfManaged;
        mForceConfirmation = forceConfirmation;
    }

    public String getDeviceProfile() {
        return mDeviceProfile;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public boolean isSelfManaged() {
        return mSelfManaged;
    }

    public boolean isForceConfirmation() {
        return mForceConfirmation;
    }

    public boolean isSingleDevice() {
        return mSingleDevice;
    }

    @Override
    public String toString() {
        return "AssociationRequest{singleDevice=" + mSingleDevice
                + ", deviceFilters=" + mDeviceFilters
                + ", deviceProfile=" + mDeviceProfile
                + ", displayName=" + mDisplayName
                + ", selfManaged=" + mSelfManaged
                + ", forceConfirmation=" + mForceConfirmation + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssociationRequest)) return false;
        AssociationRequest that = (AssociationRequest) o;
        return mSingleDevice == that.mSingleDevice
                && mSelfManaged == that.mSelfManaged
                && mForceConfirmation == that.mForceConfirmation
                && Objects.equals(mDeviceFilters, that.mDeviceFilters)
                && Objects.equals(mDeviceProfile, that.mDeviceProfile)
                && Objects.equals(mDisplayName != null ? mDisplayName.toString() : null,
                        that.mDisplayName != null ? that.mDisplayName.toString() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSingleDevice, mDeviceFilters, mDeviceProfile,
                mDisplayName != null ? mDisplayName.toString() : null,
                mSelfManaged, mForceConfirmation);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mSingleDevice);
        dest.writeString(mDeviceProfile);
        dest.writeString(mDisplayName != null ? mDisplayName.toString() : null);
        dest.writeBoolean(mSelfManaged);
        dest.writeBoolean(mForceConfirmation);
        dest.writeInt(mDeviceFilters.size());
        for (int i = 0; i < mDeviceFilters.size(); i++) {
            DeviceFilter<?> f = mDeviceFilters.get(i);
            if (f instanceof BluetoothDeviceFilter) {
                dest.writeInt(FILTER_BT);
                f.writeToParcel(dest, flags);
            } else if (f instanceof BluetoothLeDeviceFilter) {
                dest.writeInt(FILTER_BLE);
                f.writeToParcel(dest, flags);
            } else if (f instanceof WifiDeviceFilter) {
                dest.writeInt(FILTER_WIFI);
                f.writeToParcel(dest, flags);
            } else {
                dest.writeInt(0);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AssociationRequest> CREATOR = new Creator<AssociationRequest>() {
        @Override
        public AssociationRequest createFromParcel(Parcel in) {
            boolean single = in.readBoolean();
            String profile = in.readString();
            String displayName = in.readString();
            boolean selfManaged = in.readBoolean();
            boolean forceConfirm = in.readBoolean();
            int count = in.readInt();
            List<DeviceFilter<?>> filters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int tag = in.readInt();
                if (tag == FILTER_BT) {
                    filters.add(BluetoothDeviceFilter.CREATOR.createFromParcel(in));
                } else if (tag == FILTER_BLE) {
                    filters.add(BluetoothLeDeviceFilter.CREATOR.createFromParcel(in));
                } else if (tag == FILTER_WIFI) {
                    filters.add(WifiDeviceFilter.CREATOR.createFromParcel(in));
                }
            }
            return new AssociationRequest(single, filters, profile, displayName,
                    selfManaged, forceConfirm);
        }

        @Override
        public AssociationRequest[] newArray(int size) {
            return new AssociationRequest[size];
        }
    };

    public static final class Builder {
        private boolean mSingleDevice;
        private final List<DeviceFilter<?>> mDeviceFilters = new ArrayList<>();
        private String mDeviceProfile;
        private CharSequence mDisplayName;
        private boolean mSelfManaged;
        private boolean mForceConfirmation;

        public Builder() {
        }

        public Builder setSingleDevice(boolean singleDevice) {
            mSingleDevice = singleDevice;
            return this;
        }

        public Builder addDeviceFilter(DeviceFilter<?> deviceFilter) {
            if (deviceFilter != null) {
                mDeviceFilters.add(deviceFilter);
            }
            return this;
        }

        public Builder setDeviceProfile(String deviceProfile) {
            mDeviceProfile = deviceProfile;
            return this;
        }

        public Builder setDisplayName(CharSequence displayName) {
            mDisplayName = Objects.requireNonNull(displayName, "displayName must not be null");
            return this;
        }

        public Builder setSelfManaged(boolean selfManaged) {
            mSelfManaged = selfManaged;
            return this;
        }

        public Builder setForceConfirmation(boolean forceConfirmation) {
            mForceConfirmation = forceConfirmation;
            return this;
        }

        public AssociationRequest build() {
            if (mSelfManaged && mDisplayName == null) {
                throw new IllegalStateException(
                        "Requesting a self-managed association requires a non-null displayName");
            }
            if (mSelfManaged && !mDeviceFilters.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot provide device filters for a self-managed association");
            }
            return new AssociationRequest(mSingleDevice, mDeviceFilters, mDeviceProfile,
                    mDisplayName, mSelfManaged, mForceConfirmation);
        }
    }
}
