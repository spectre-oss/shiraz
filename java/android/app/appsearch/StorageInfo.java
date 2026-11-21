// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;

public final class StorageInfo implements Parcelable {
    private final long mSizeBytes;
    private final int mAliveDocumentsCount;
    private final int mAliveNamespacesCount;

    private StorageInfo(long sizeBytes, int aliveDocumentsCount, int aliveNamespacesCount) {
        mSizeBytes = sizeBytes;
        mAliveDocumentsCount = aliveDocumentsCount;
        mAliveNamespacesCount = aliveNamespacesCount;
    }

    StorageInfo(Parcel in) {
        mSizeBytes = in.readLong();
        mAliveDocumentsCount = in.readInt();
        mAliveNamespacesCount = in.readInt();
    }

    public long getSizeBytes() {
        return mSizeBytes;
    }

    public int getAliveDocumentsCount() {
        return mAliveDocumentsCount;
    }

    public int getAliveNamespacesCount() {
        return mAliveNamespacesCount;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mSizeBytes);
        dest.writeInt(mAliveDocumentsCount);
        dest.writeInt(mAliveNamespacesCount);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<StorageInfo> CREATOR =
            new Parcelable.Creator<StorageInfo>() {
                @Override
                public StorageInfo createFromParcel(Parcel in) {
                    return new StorageInfo(in);
                }

                @Override
                public StorageInfo[] newArray(int size) {
                    return new StorageInfo[size];
                }
            };

    public static final class Builder {
        private long mSizeBytes;
        private int mAliveDocumentsCount;
        private int mAliveNamespacesCount;

        public Builder() {
        }

        public Builder setSizeBytes(long sizeBytes) {
            mSizeBytes = sizeBytes;
            return this;
        }

        public Builder setAliveDocumentsCount(int aliveDocumentsCount) {
            mAliveDocumentsCount = aliveDocumentsCount;
            return this;
        }

        public Builder setAliveNamespacesCount(int aliveNamespacesCount) {
            mAliveNamespacesCount = aliveNamespacesCount;
            return this;
        }

        public StorageInfo build() {
            return new StorageInfo(mSizeBytes, mAliveDocumentsCount, mAliveNamespacesCount);
        }
    }
}
