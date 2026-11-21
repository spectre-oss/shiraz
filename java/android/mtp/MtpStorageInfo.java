// SPDX-License-Identifier: GPL-3.0-only
package android.mtp;

public final class MtpStorageInfo {
    private int mStorageId;
    private long mMaxCapacity;
    private long mFreeSpace;
    private String mDescription;
    private String mVolumeIdentifier;

    private MtpStorageInfo() {
    }

    /** @hide */
    public MtpStorageInfo(
            int storageId,
            long maxCapacity,
            long freeSpace,
            String description,
            String volumeIdentifier) {
        mStorageId = storageId;
        mMaxCapacity = maxCapacity;
        mFreeSpace = freeSpace;
        mDescription = description;
        mVolumeIdentifier = volumeIdentifier;
    }

    public int getStorageId() {
        return mStorageId;
    }

    public long getMaxCapacity() {
        return mMaxCapacity;
    }

    public long getFreeSpace() {
        return mFreeSpace;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getVolumeIdentifier() {
        return mVolumeIdentifier;
    }
}
