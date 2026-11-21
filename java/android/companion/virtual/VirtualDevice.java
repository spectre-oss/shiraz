// SPDX-License-Identifier: GPL-3.0-only
package android.companion.virtual;

import android.os.Parcel;
import android.os.Parcelable;

public final class VirtualDevice implements Parcelable {
    private final int mId;
    private final String mPersistentId;
    private final String mName;
    private final CharSequence mDisplayName;
    private final int[] mDisplayIds;
    private final boolean mHasCustomSensorSupport;

    public VirtualDevice(int id, String persistentId, String name, CharSequence displayName,
            int[] displayIds, boolean hasCustomSensorSupport) {
        mId = id;
        mPersistentId = persistentId;
        mName = name;
        mDisplayName = displayName;
        mDisplayIds = displayIds != null ? displayIds.clone() : new int[0];
        mHasCustomSensorSupport = hasCustomSensorSupport;
    }

    public int getDeviceId() {
        return mId;
    }

    public String getPersistentDeviceId() {
        return mPersistentId;
    }

    public String getName() {
        return mName;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public int[] getDisplayIds() {
        return mDisplayIds.clone();
    }

    public boolean hasCustomSensorSupport() {
        return mHasCustomSensorSupport;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mPersistentId);
        dest.writeString(mName);
        dest.writeString(mDisplayName != null ? mDisplayName.toString() : null);
        dest.writeIntArray(mDisplayIds);
        dest.writeBoolean(mHasCustomSensorSupport);
    }

    @Override
    public String toString() {
        return "VirtualDevice(mId=" + mId + " mName=" + mName + ")";
    }

    public static final Creator<VirtualDevice> CREATOR = new Creator<VirtualDevice>() {
        @Override
        public VirtualDevice createFromParcel(Parcel in) {
            int id = in.readInt();
            String pid = in.readString();
            String name = in.readString();
            String displayName = in.readString();
            int[] displayIds = in.createIntArray();
            boolean sensors = in.readBoolean();
            return new VirtualDevice(id, pid, name, displayName, displayIds, sensors);
        }

        @Override
        public VirtualDevice[] newArray(int size) {
            return new VirtualDevice[size];
        }
    };
}
