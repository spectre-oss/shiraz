// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AssociationInfo implements Parcelable {
    private final int mId;
    private final MacAddress mDeviceMacAddress;
    private final CharSequence mDisplayName;
    private final String mDeviceProfile;
    private final AssociatedDevice mAssociatedDevice;
    private final boolean mSelfManaged;
    private final int mSystemDataSyncFlags;

    public AssociationInfo(int id, MacAddress deviceMacAddress, CharSequence displayName,
            String deviceProfile, AssociatedDevice associatedDevice,
            boolean selfManaged, int systemDataSyncFlags) {
        mId = id;
        mDeviceMacAddress = deviceMacAddress;
        mDisplayName = displayName;
        mDeviceProfile = deviceProfile;
        mAssociatedDevice = associatedDevice;
        mSelfManaged = selfManaged;
        mSystemDataSyncFlags = systemDataSyncFlags;
    }

    public int getId() {
        return mId;
    }

    public MacAddress getDeviceMacAddress() {
        return mDeviceMacAddress;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public String getDeviceProfile() {
        return mDeviceProfile;
    }

    public AssociatedDevice getAssociatedDevice() {
        return mAssociatedDevice;
    }

    public boolean isSelfManaged() {
        return mSelfManaged;
    }

    public int getSystemDataSyncFlags() {
        return mSystemDataSyncFlags;
    }

    @Override
    public String toString() {
        return "AssociationInfo{id=" + mId
                + ", mac=" + mDeviceMacAddress
                + ", displayName=" + mDisplayName
                + ", profile=" + mDeviceProfile
                + ", selfManaged=" + mSelfManaged + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssociationInfo)) return false;
        AssociationInfo that = (AssociationInfo) o;
        return mId == that.mId
                && mSelfManaged == that.mSelfManaged
                && mSystemDataSyncFlags == that.mSystemDataSyncFlags
                && Objects.equals(mDeviceMacAddress, that.mDeviceMacAddress)
                && Objects.equals(mDisplayName != null ? mDisplayName.toString() : null,
                        that.mDisplayName != null ? that.mDisplayName.toString() : null)
                && Objects.equals(mDeviceProfile, that.mDeviceProfile)
                && Objects.equals(mAssociatedDevice, that.mAssociatedDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mDeviceMacAddress,
                mDisplayName != null ? mDisplayName.toString() : null,
                mDeviceProfile, mAssociatedDevice, mSelfManaged, mSystemDataSyncFlags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeTypedObject(mDeviceMacAddress, flags);
        dest.writeString(mDisplayName != null ? mDisplayName.toString() : null);
        dest.writeString(mDeviceProfile);
        dest.writeTypedObject(mAssociatedDevice, flags);
        dest.writeBoolean(mSelfManaged);
        dest.writeInt(mSystemDataSyncFlags);
    }

    public static final Creator<AssociationInfo> CREATOR = new Creator<AssociationInfo>() {
        @Override
        public AssociationInfo createFromParcel(Parcel in) {
            int id = in.readInt();
            MacAddress mac = in.readTypedObject(MacAddress.CREATOR);
            String displayName = in.readString();
            String profile = in.readString();
            AssociatedDevice assoc = in.readTypedObject(AssociatedDevice.CREATOR);
            boolean selfManaged = in.readBoolean();
            int syncFlags = in.readInt();
            return new AssociationInfo(id, mac, displayName, profile, assoc, selfManaged, syncFlags);
        }

        @Override
        public AssociationInfo[] newArray(int size) {
            return new AssociationInfo[size];
        }
    };
}
