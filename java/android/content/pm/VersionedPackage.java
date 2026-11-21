// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Tuple of a package name and its 64-bit long version code. */
public final class VersionedPackage implements Parcelable {
    private final String mPackageName;
    private final long mVersionCode;

    public VersionedPackage(String packageName, int versionCode) {
        mPackageName = Objects.requireNonNull(packageName);
        mVersionCode = versionCode;
    }

    public VersionedPackage(String packageName, long versionCode) {
        mPackageName = Objects.requireNonNull(packageName);
        mVersionCode = versionCode;
    }

    private VersionedPackage(Parcel parcel) {
        mPackageName = parcel.readString();
        mVersionCode = parcel.readLong();
    }

    public String getPackageName() {
        return mPackageName;
    }

    @Deprecated
    public int getVersionCode() {
        return (int) (mVersionCode & 0x7fffffffL);
    }

    public long getLongVersionCode() {
        return mVersionCode;
    }

    @Override
    public String toString() {
        return mPackageName + "/" + mVersionCode;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof VersionedPackage)
                && Objects.equals(((VersionedPackage) o).mPackageName, mPackageName)
                && ((VersionedPackage) o).mVersionCode == mVersionCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, Long.valueOf(mVersionCode));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mPackageName);
        parcel.writeLong(mVersionCode);
    }

    public static final Creator<VersionedPackage> CREATOR = new Creator<VersionedPackage>() {
        @Override
        public VersionedPackage createFromParcel(Parcel source) {
            return new VersionedPackage(source);
        }

        @Override
        public VersionedPackage[] newArray(int size) {
            return new VersionedPackage[size];
        }
    };
}
