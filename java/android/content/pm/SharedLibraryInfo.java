// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metadata describing a shared library (built-in, dynamic, static, or SDK package) on the system. */
public final class SharedLibraryInfo implements Parcelable {
    public static final int TYPE_BUILTIN = 0;
    public static final int TYPE_DYNAMIC = 1;
    public static final int TYPE_STATIC = 2;
    public static final int TYPE_SDK_PACKAGE = 3;

    public static final int VERSION_UNDEFINED = -1;

    private final int mType;
    private final String mName;
    private final long mVersion;
    private final VersionedPackage mDeclaringPackage;
    private final List<VersionedPackage> mDependentPackages;

    public SharedLibraryInfo(
            int type,
            String name,
            long version,
            VersionedPackage declaringPackage,
            List<VersionedPackage> dependentPackages) {
        mType = type;
        mName = name;
        mVersion = version;
        mDeclaringPackage = declaringPackage;
        mDependentPackages = dependentPackages == null
                ? Collections.<VersionedPackage>emptyList()
                : Collections.unmodifiableList(new ArrayList<VersionedPackage>(dependentPackages));
    }

    private SharedLibraryInfo(Parcel parcel) {
        mType = parcel.readInt();
        mName = parcel.readString();
        mVersion = parcel.readLong();
        mDeclaringPackage = parcel.readParcelable(VersionedPackage.class.getClassLoader());
        List<VersionedPackage> deps = parcel.createTypedArrayList(VersionedPackage.CREATOR);
        mDependentPackages = deps == null
                ? Collections.<VersionedPackage>emptyList()
                : Collections.unmodifiableList(deps);
    }

    public int getType() {
        return mType;
    }

    public String getName() {
        return mName;
    }

    @Deprecated
    public int getVersion() {
        return mVersion < 0 ? (int) mVersion : (int) (mVersion & 0x7fffffffL);
    }

    public long getLongVersion() {
        return mVersion;
    }

    public VersionedPackage getDeclaringPackage() {
        return mDeclaringPackage;
    }

    public List<VersionedPackage> getDependentPackages() {
        return mDependentPackages;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "SharedLibraryInfo{name=" + mName + ", type=" + mType
                + ", version=" + mVersion + "}";
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeString(mName);
        parcel.writeLong(mVersion);
        parcel.writeParcelable(mDeclaringPackage, flags);
        parcel.writeTypedList(mDependentPackages);
    }

    public static final Creator<SharedLibraryInfo> CREATOR = new Creator<SharedLibraryInfo>() {
        @Override
        public SharedLibraryInfo createFromParcel(Parcel source) {
            return new SharedLibraryInfo(source);
        }

        @Override
        public SharedLibraryInfo[] newArray(int size) {
            return new SharedLibraryInfo[size];
        }
    };
}
