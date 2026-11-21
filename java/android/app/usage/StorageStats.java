// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class StorageStats implements Parcelable {
    public static final int APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT = 0;
    public static final int APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE = 1;
    public static final int APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE = 2;
    public static final int APP_DATA_TYPE_FILE_TYPE_APK = 3;
    public static final int APP_DATA_TYPE_FILE_TYPE_DM = 4;
    public static final int APP_DATA_TYPE_LIB = 5;

    public long codeBytes;
    public long dataBytes;
    public long cacheBytes;
    public long dexoptBytes;
    public long refProfBytes;
    public long curProfBytes;
    public long apkBytes;
    public long libBytes;
    public long dmBytes;
    public long externalCacheBytes;

    public StorageStats() {
    }

    StorageStats(Parcel in) {
        codeBytes = in.readLong();
        dataBytes = in.readLong();
        cacheBytes = in.readLong();
        dexoptBytes = in.readLong();
        refProfBytes = in.readLong();
        curProfBytes = in.readLong();
        apkBytes = in.readLong();
        libBytes = in.readLong();
        dmBytes = in.readLong();
        externalCacheBytes = in.readLong();
    }

    public long getAppBytes() {
        return codeBytes;
    }

    public long getAppBytesByDataType(int dataType) {
        switch (dataType) {
            case APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT:
                return dexoptBytes;
            case APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE:
                return refProfBytes;
            case APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE:
                return curProfBytes;
            case APP_DATA_TYPE_FILE_TYPE_APK:
                return apkBytes;
            case APP_DATA_TYPE_LIB:
                return libBytes;
            case APP_DATA_TYPE_FILE_TYPE_DM:
                return dmBytes;
            default:
                return 0L;
        }
    }

    public long getDataBytes() {
        return dataBytes;
    }

    public long getCacheBytes() {
        return cacheBytes;
    }

    public long getExternalCacheBytes() {
        return externalCacheBytes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(codeBytes);
        dest.writeLong(dataBytes);
        dest.writeLong(cacheBytes);
        dest.writeLong(dexoptBytes);
        dest.writeLong(refProfBytes);
        dest.writeLong(curProfBytes);
        dest.writeLong(apkBytes);
        dest.writeLong(libBytes);
        dest.writeLong(dmBytes);
        dest.writeLong(externalCacheBytes);
    }

    public static final Parcelable.Creator<StorageStats> CREATOR =
            new Parcelable.Creator<StorageStats>() {
                @Override
                public StorageStats createFromParcel(Parcel in) {
                    return new StorageStats(in);
                }

                @Override
                public StorageStats[] newArray(int size) {
                    return new StorageStats[size];
                }
            };
}
