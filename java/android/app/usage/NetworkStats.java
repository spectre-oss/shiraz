// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import java.util.Collections;
import java.util.List;

public final class NetworkStats implements AutoCloseable {
    private final List<Bucket> mBuckets;
    private int mIndex;
    private boolean mClosed;

    NetworkStats(List<Bucket> buckets) {
        mBuckets = buckets != null ? buckets : Collections.<Bucket>emptyList();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public boolean getNextBucket(Bucket bucketOut) {
        if (bucketOut == null) throw new NullPointerException("bucketOut");
        if (mClosed || mIndex >= mBuckets.size()) {
            return false;
        }
        Bucket src = mBuckets.get(mIndex++);
        bucketOut.mUid = src.mUid;
        bucketOut.mTag = src.mTag;
        bucketOut.mState = src.mState;
        bucketOut.mMetered = src.mMetered;
        bucketOut.mRoaming = src.mRoaming;
        bucketOut.mDefaultNetworkStatus = src.mDefaultNetworkStatus;
        bucketOut.mBeginTimeStamp = src.mBeginTimeStamp;
        bucketOut.mEndTimeStamp = src.mEndTimeStamp;
        bucketOut.mRxBytes = src.mRxBytes;
        bucketOut.mTxBytes = src.mTxBytes;
        bucketOut.mRxPackets = src.mRxPackets;
        bucketOut.mTxPackets = src.mTxPackets;
        return true;
    }

    public boolean hasNextBucket() {
        return !mClosed && mIndex < mBuckets.size();
    }

    @Override
    public void close() {
        mClosed = true;
    }

    public static class Bucket {
        public static final int STATE_ALL = -1;
        public static final int STATE_DEFAULT = 1;
        public static final int STATE_FOREGROUND = 2;

        public static final int UID_ALL = -1;
        public static final int UID_REMOVED = -4;
        public static final int UID_TETHERING = -5;

        public static final int METERED_ALL = -1;
        public static final int METERED_NO = 1;
        public static final int METERED_YES = 2;

        public static final int ROAMING_ALL = -1;
        public static final int ROAMING_NO = 1;
        public static final int ROAMING_YES = 2;

        public static final int DEFAULT_NETWORK_ALL = -1;
        public static final int DEFAULT_NETWORK_NO = 1;
        public static final int DEFAULT_NETWORK_YES = 2;

        public static final int TAG_NONE = 0;

        int mUid = UID_ALL;
        int mTag = TAG_NONE;
        int mState = STATE_ALL;
        int mMetered = METERED_ALL;
        int mRoaming = ROAMING_ALL;
        int mDefaultNetworkStatus = DEFAULT_NETWORK_ALL;
        long mBeginTimeStamp;
        long mEndTimeStamp;
        long mRxBytes;
        long mTxBytes;
        long mRxPackets;
        long mTxPackets;

        public Bucket() {
        }

        public int getUid() {
            return mUid;
        }

        public int getTag() {
            return mTag;
        }

        public int getState() {
            return mState;
        }

        public int getMetered() {
            return mMetered;
        }

        public int getRoaming() {
            return mRoaming;
        }

        public int getDefaultNetworkStatus() {
            return mDefaultNetworkStatus;
        }

        public long getStartTimeStamp() {
            return mBeginTimeStamp;
        }

        public long getEndTimeStamp() {
            return mEndTimeStamp;
        }

        public long getRxBytes() {
            return mRxBytes;
        }

        public long getTxBytes() {
            return mTxBytes;
        }

        public long getRxPackets() {
            return mRxPackets;
        }

        public long getTxPackets() {
            return mTxPackets;
        }
    }
}
