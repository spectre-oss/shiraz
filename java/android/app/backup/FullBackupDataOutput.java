// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;

import android.os.ParcelFileDescriptor;

public class FullBackupDataOutput {
    private final ParcelFileDescriptor mData;
    private final long mQuota;
    private final int mTransportFlags;

    public FullBackupDataOutput(ParcelFileDescriptor fd) {
        this(fd, -1L, 0);
    }

    public FullBackupDataOutput(ParcelFileDescriptor fd, long quota) {
        this(fd, quota, 0);
    }

    public FullBackupDataOutput(ParcelFileDescriptor fd, long quota, int transportFlags) {
        mData = fd;
        mQuota = quota;
        mTransportFlags = transportFlags;
    }

    ParcelFileDescriptor getData() {
        return mData;
    }

    public long getQuota() {
        return mQuota;
    }

    public int getTransportFlags() {
        return mTransportFlags;
    }
}
