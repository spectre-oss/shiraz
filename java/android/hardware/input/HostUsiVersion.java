// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.input;

import android.os.Parcel;
import android.os.Parcelable;

public final class HostUsiVersion implements Parcelable {
    private final int mMajorVersion;
    private final int mMinorVersion;

    public HostUsiVersion(int majorVersion, int minorVersion) {
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    public int getMajorVersion() {
        return mMajorVersion;
    }

    public int getMinorVersion() {
        return mMinorVersion;
    }

    @Override
    public String toString() {
        return "HostUsiVersion { majorVersion = " + mMajorVersion
                + ", minorVersion = " + mMinorVersion + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostUsiVersion that = (HostUsiVersion) o;
        return mMajorVersion == that.mMajorVersion && mMinorVersion == that.mMinorVersion;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + mMajorVersion;
        result = 31 * result + mMinorVersion;
        return result;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMajorVersion);
        dest.writeInt(mMinorVersion);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<HostUsiVersion> CREATOR =
            new Parcelable.Creator<HostUsiVersion>() {
                @Override
                public HostUsiVersion createFromParcel(Parcel in) {
                    return new HostUsiVersion(in.readInt(), in.readInt());
                }

                @Override
                public HostUsiVersion[] newArray(int size) {
                    return new HostUsiVersion[size];
                }
            };
}
