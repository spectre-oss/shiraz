// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ApplicationStartInfo implements Parcelable {
    public static final int STARTUP_STATE_STARTED = 0;
    public static final int STARTUP_STATE_ERROR = 1;
    public static final int STARTUP_STATE_FIRST_FRAME_DRAWN = 2;

    public static final int START_REASON_ALARM = 0;
    public static final int START_REASON_BACKUP = 1;
    public static final int START_REASON_BOOT_COMPLETE = 2;
    public static final int START_REASON_BROADCAST = 3;
    public static final int START_REASON_CONTENT_PROVIDER = 4;
    public static final int START_REASON_JOB = 5;
    public static final int START_REASON_LAUNCHER = 6;
    public static final int START_REASON_LAUNCHER_RECENTS = 7;
    public static final int START_REASON_OTHER = 8;
    public static final int START_REASON_PUSH = 9;
    public static final int START_REASON_SERVICE = 10;
    public static final int START_REASON_START_ACTIVITY = 11;

    public static final int START_TYPE_UNSET = 0;
    public static final int START_TYPE_COLD = 1;
    public static final int START_TYPE_WARM = 2;
    public static final int START_TYPE_HOT = 3;

    public static final int LAUNCH_MODE_STANDARD = 0;
    public static final int LAUNCH_MODE_SINGLE_TOP = 1;
    public static final int LAUNCH_MODE_SINGLE_INSTANCE = 2;
    public static final int LAUNCH_MODE_SINGLE_TASK = 3;
    public static final int LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK = 4;

    public static final int START_TIMESTAMP_LAUNCH = 0;
    public static final int START_TIMESTAMP_FORK = 1;
    public static final int START_TIMESTAMP_APPLICATION_ONCREATE = 2;
    public static final int START_TIMESTAMP_BIND_APPLICATION = 3;
    public static final int START_TIMESTAMP_FIRST_FRAME = 4;
    public static final int START_TIMESTAMP_FULLY_DRAWN = 5;
    public static final int START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME = 6;
    public static final int START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE = 7;
    public static final int START_TIMESTAMP_RESERVED_RANGE_SYSTEM = 20;
    public static final int START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START = 21;
    public static final int START_TIMESTAMP_RESERVED_RANGE_DEVELOPER = 30;

    private int mStartupState;
    private int mPid;
    private int mRealUid;
    private int mPackageUid;
    private int mDefiningUid;
    private String mProcessName;
    private int mReason;
    private final LinkedHashMap<Integer, Long> mStartupTimestampsNs = new LinkedHashMap<Integer, Long>();
    private int mStartType;
    private Intent mStartIntent;
    private int mLaunchMode;
    private boolean mWasForceStopped;

    ApplicationStartInfo() {
        mPid = Process.myPid();
        mRealUid = Process.myUid();
        mPackageUid = mRealUid;
        mDefiningUid = mRealUid;
    }

    private ApplicationStartInfo(Parcel in) {
        mStartupState = in.readInt();
        mPid = in.readInt();
        mRealUid = in.readInt();
        mPackageUid = in.readInt();
        mDefiningUid = in.readInt();
        mProcessName = in.readString();
        mReason = in.readInt();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            int key = in.readInt();
            long val = in.readLong();
            mStartupTimestampsNs.put(Integer.valueOf(key), Long.valueOf(val));
        }
        mStartType = in.readInt();
        mStartIntent = in.readInt() != 0 ? Intent.CREATOR.createFromParcel(in) : null;
        mLaunchMode = in.readInt();
        mWasForceStopped = in.readInt() != 0;
    }

    public int getStartupState() {
        return mStartupState;
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

    public Map<Integer, Long> getStartupTimestamps() {
        return Collections.unmodifiableMap(new LinkedHashMap<Integer, Long>(mStartupTimestampsNs));
    }

    public int getStartType() {
        return mStartType;
    }

    public Intent getIntent() {
        return mStartIntent;
    }

    public int getLaunchMode() {
        return mLaunchMode;
    }

    public boolean wasForceStopped() {
        return mWasForceStopped;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStartupState);
        dest.writeInt(mPid);
        dest.writeInt(mRealUid);
        dest.writeInt(mPackageUid);
        dest.writeInt(mDefiningUid);
        dest.writeString(mProcessName);
        dest.writeInt(mReason);
        dest.writeInt(mStartupTimestampsNs.size());
        for (Map.Entry<Integer, Long> entry : mStartupTimestampsNs.entrySet()) {
            dest.writeInt(entry.getKey().intValue());
            dest.writeLong(entry.getValue().longValue());
        }
        dest.writeInt(mStartType);
        if (mStartIntent != null) {
            dest.writeInt(1);
            mStartIntent.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mLaunchMode);
        dest.writeInt(mWasForceStopped ? 1 : 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApplicationStartInfo)) {
            return false;
        }
        ApplicationStartInfo o = (ApplicationStartInfo) other;
        return mStartupState == o.mStartupState
                && mPid == o.mPid
                && mRealUid == o.mRealUid
                && mPackageUid == o.mPackageUid
                && mDefiningUid == o.mDefiningUid
                && mReason == o.mReason
                && mStartType == o.mStartType
                && mLaunchMode == o.mLaunchMode
                && mWasForceStopped == o.mWasForceStopped
                && Objects.equals(mProcessName, o.mProcessName)
                && Objects.equals(mStartupTimestampsNs, o.mStartupTimestampsNs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartupState, mPid, mRealUid, mPackageUid, mDefiningUid,
                mProcessName, mReason, mStartupTimestampsNs, mStartType, mLaunchMode,
                Boolean.valueOf(mWasForceStopped));
    }

    public static final Parcelable.Creator<ApplicationStartInfo> CREATOR =
            new Parcelable.Creator<ApplicationStartInfo>() {
                @Override
                public ApplicationStartInfo createFromParcel(Parcel in) {
                    return new ApplicationStartInfo(in);
                }

                @Override
                public ApplicationStartInfo[] newArray(int size) {
                    return new ApplicationStartInfo[size];
                }
            };
}
