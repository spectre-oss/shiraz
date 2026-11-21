// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

public final class ApplicationExitInfo implements Parcelable {
    public static final int REASON_UNKNOWN = 0;
    public static final int REASON_EXIT_SELF = 1;
    public static final int REASON_SIGNALED = 2;
    public static final int REASON_LOW_MEMORY = 3;
    public static final int REASON_CRASH = 4;
    public static final int REASON_CRASH_NATIVE = 5;
    public static final int REASON_ANR = 6;
    public static final int REASON_INITIALIZATION_FAILURE = 7;
    public static final int REASON_PERMISSION_CHANGE = 8;
    public static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;
    public static final int REASON_USER_REQUESTED = 10;
    public static final int REASON_USER_STOPPED = 11;
    public static final int REASON_DEPENDENCY_DIED = 12;
    public static final int REASON_OTHER = 13;
    public static final int REASON_FREEZER = 14;
    public static final int REASON_PACKAGE_STATE_CHANGE = 15;
    public static final int REASON_PACKAGE_UPDATED = 16;

    private int mPid;
    private int mRealUid;
    private int mPackageUid;
    private int mDefiningUid;
    private String mProcessName;
    private int mReason;
    private int mStatus;
    private int mImportance;
    private long mPss;
    private long mRss;
    private long mTimestamp;
    private String mDescription;
    private byte[] mState;

    ApplicationExitInfo() {
        mRealUid = Process.myUid();
        mPackageUid = mRealUid;
        mDefiningUid = mRealUid;
    }

    private ApplicationExitInfo(Parcel in) {
        mPid = in.readInt();
        mRealUid = in.readInt();
        mPackageUid = in.readInt();
        mDefiningUid = in.readInt();
        mProcessName = in.readString();
        mReason = in.readInt();
        mStatus = in.readInt();
        mImportance = in.readInt();
        mPss = in.readLong();
        mRss = in.readLong();
        mTimestamp = in.readLong();
        mDescription = in.readString();
        mState = in.createByteArray();
    }

    public int getPid() {
        return mPid;
    }

    public int getRealUid() {
        return mRealUid;
    }

    public int getPackageUid() {
        return mPackageUid;
    }

    public int getDefiningUid() {
        return mDefiningUid;
    }

    public String getProcessName() {
        return mProcessName;
    }

    public int getReason() {
        return mReason;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getImportance() {
        return mImportance;
    }

    public long getPss() {
        return mPss;
    }

    public long getRss() {
        return mRss;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getDescription() {
        return mDescription;
    }

    public UserHandle getUserHandle() {
        return UserHandle.getUserHandleForUid(mRealUid);
    }

    public byte[] getProcessStateSummary() {
        return mState == null ? null : mState.clone();
    }

    public InputStream getTraceInputStream() throws IOException {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPid);
        dest.writeInt(mRealUid);
        dest.writeInt(mPackageUid);
        dest.writeInt(mDefiningUid);
        dest.writeString(mProcessName);
        dest.writeInt(mReason);
        dest.writeInt(mStatus);
        dest.writeInt(mImportance);
        dest.writeLong(mPss);
        dest.writeLong(mRss);
        dest.writeLong(mTimestamp);
        dest.writeString(mDescription);
        dest.writeByteArray(mState);
    }

    @Override
    public String toString() {
        return "ApplicationExitInfo(timestamp=" + mTimestamp
                + " pid=" + mPid
                + " process=" + mProcessName
                + " reason=" + mReason
                + " status=" + mStatus + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApplicationExitInfo)) {
            return false;
        }
        ApplicationExitInfo o = (ApplicationExitInfo) other;
        return mPid == o.mPid
                && mRealUid == o.mRealUid
                && mPackageUid == o.mPackageUid
                && mDefiningUid == o.mDefiningUid
                && mReason == o.mReason
                && mStatus == o.mStatus
                && mImportance == o.mImportance
                && mPss == o.mPss
                && mRss == o.mRss
                && mTimestamp == o.mTimestamp
                && Objects.equals(mProcessName, o.mProcessName)
                && Objects.equals(mDescription, o.mDescription)
                && Arrays.equals(mState, o.mState);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mPid, mRealUid, mPackageUid, mDefiningUid, mProcessName,
                mReason, mStatus, mImportance, mPss, mRss, mTimestamp, mDescription);
        return 31 * result + Arrays.hashCode(mState);
    }

    public static final Parcelable.Creator<ApplicationExitInfo> CREATOR =
            new Parcelable.Creator<ApplicationExitInfo>() {
                @Override
                public ApplicationExitInfo createFromParcel(Parcel in) {
                    return new ApplicationExitInfo(in);
                }

                @Override
                public ApplicationExitInfo[] newArray(int size) {
                    return new ApplicationExitInfo[size];
                }
            };
}
