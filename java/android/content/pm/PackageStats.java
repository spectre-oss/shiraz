// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Storage usage metrics for an application package. */
@Deprecated
public class PackageStats implements Parcelable {
    public String packageName;
    public long codeSize;
    public long dataSize;
    public long cacheSize;
    public long externalCodeSize;
    public long externalDataSize;
    public long externalCacheSize;
    public long externalMediaSize;
    public long externalObbSize;

    public PackageStats(String pkgName) {
        packageName = pkgName;
    }

    public PackageStats(Parcel source) {
        packageName = source.readString();
        codeSize = source.readLong();
        dataSize = source.readLong();
        cacheSize = source.readLong();
        externalCodeSize = source.readLong();
        externalDataSize = source.readLong();
        externalCacheSize = source.readLong();
        externalMediaSize = source.readLong();
        externalObbSize = source.readLong();
    }

    public PackageStats(PackageStats pStats) {
        packageName = pStats.packageName;
        codeSize = pStats.codeSize;
        dataSize = pStats.dataSize;
        cacheSize = pStats.cacheSize;
        externalCodeSize = pStats.externalCodeSize;
        externalDataSize = pStats.externalDataSize;
        externalCacheSize = pStats.externalCacheSize;
        externalMediaSize = pStats.externalMediaSize;
        externalObbSize = pStats.externalObbSize;
    }

    @Override
    public String toString() {
        return "PackageStats{" + Integer.toHexString(System.identityHashCode(this))
                + " " + packageName + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(packageName);
        dest.writeLong(codeSize);
        dest.writeLong(dataSize);
        dest.writeLong(cacheSize);
        dest.writeLong(externalCodeSize);
        dest.writeLong(externalDataSize);
        dest.writeLong(externalCacheSize);
        dest.writeLong(externalMediaSize);
        dest.writeLong(externalObbSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PackageStats)) {
            return false;
        }
        final PackageStats otherStats = (PackageStats) obj;
        return Objects.equals(packageName, otherStats.packageName)
                && codeSize == otherStats.codeSize
                && dataSize == otherStats.dataSize
                && cacheSize == otherStats.cacheSize
                && externalCodeSize == otherStats.externalCodeSize
                && externalDataSize == otherStats.externalDataSize
                && externalCacheSize == otherStats.externalCacheSize
                && externalMediaSize == otherStats.externalMediaSize
                && externalObbSize == otherStats.externalObbSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                packageName,
                Long.valueOf(codeSize),
                Long.valueOf(dataSize),
                Long.valueOf(cacheSize),
                Long.valueOf(externalCodeSize),
                Long.valueOf(externalDataSize),
                Long.valueOf(externalCacheSize),
                Long.valueOf(externalMediaSize),
                Long.valueOf(externalObbSize));
    }

    public static final Parcelable.Creator<PackageStats> CREATOR =
            new Parcelable.Creator<PackageStats>() {
                @Override
                public PackageStats createFromParcel(Parcel in) {
                    return new PackageStats(in);
                }

                @Override
                public PackageStats[] newArray(int size) {
                    return new PackageStats[size];
                }
            };
}
