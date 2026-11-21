// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class UsageStats implements Parcelable {
    public String mPackageName;
    public long mBeginTimeStamp;
    public long mEndTimeStamp;
    public long mLastTimeUsed;
    public long mLastTimeVisible;
    public long mTotalTimeInForeground;
    public long mTotalTimeVisible;
    public long mLastTimeForegroundServiceUsed;
    public long mTotalTimeForegroundServiceUsed;

    public UsageStats() {
    }

    public UsageStats(UsageStats stats) {
        mPackageName = stats.mPackageName;
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastTimeUsed = stats.mLastTimeUsed;
        mLastTimeVisible = stats.mLastTimeVisible;
        mTotalTimeInForeground = stats.mTotalTimeInForeground;
        mTotalTimeVisible = stats.mTotalTimeVisible;
        mLastTimeForegroundServiceUsed = stats.mLastTimeForegroundServiceUsed;
        mTotalTimeForegroundServiceUsed = stats.mTotalTimeForegroundServiceUsed;
    }

    UsageStats(Parcel in) {
        mPackageName = in.readString();
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastTimeUsed = in.readLong();
        mLastTimeVisible = in.readLong();
        mTotalTimeInForeground = in.readLong();
        mTotalTimeVisible = in.readLong();
        mLastTimeForegroundServiceUsed = in.readLong();
        mTotalTimeForegroundServiceUsed = in.readLong();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public long getFirstTimeStamp() {
        return mBeginTimeStamp;
    }

    public long getLastTimeStamp() {
        return mEndTimeStamp;
    }

    public long getLastTimeUsed() {
        return mLastTimeUsed;
    }

    public long getLastTimeVisible() {
        return mLastTimeVisible;
    }

    public long getTotalTimeInForeground() {
        return mTotalTimeInForeground;
    }

    public long getTotalTimeVisible() {
        return mTotalTimeVisible;
    }

    public long getLastTimeForegroundServiceUsed() {
        return mLastTimeForegroundServiceUsed;
    }

    public long getTotalTimeForegroundServiceUsed() {
        return mTotalTimeForegroundServiceUsed;
    }

    public void add(UsageStats right) {
        if (!Objects.equals(mPackageName, right.mPackageName)) {
            throw new IllegalArgumentException(
                    "Can't merge UsageStats for package '" + mPackageName
                            + "' with UsageStats for package '" + right.mPackageName + "'.");
        }
        if (right.mBeginTimeStamp > mBeginTimeStamp) {
            mLastTimeUsed = Math.max(mLastTimeUsed, right.mLastTimeUsed);
            mLastTimeVisible = Math.max(mLastTimeVisible, right.mLastTimeVisible);
            mLastTimeForegroundServiceUsed =
                    Math.max(mLastTimeForegroundServiceUsed, right.mLastTimeForegroundServiceUsed);
        }
        mBeginTimeStamp = Math.min(mBeginTimeStamp, right.mBeginTimeStamp);
        mEndTimeStamp = Math.max(mEndTimeStamp, right.mEndTimeStamp);
        mTotalTimeInForeground += right.mTotalTimeInForeground;
        mTotalTimeVisible += right.mTotalTimeVisible;
        mTotalTimeForegroundServiceUsed += right.mTotalTimeForegroundServiceUsed;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastTimeUsed);
        dest.writeLong(mLastTimeVisible);
        dest.writeLong(mTotalTimeInForeground);
        dest.writeLong(mTotalTimeVisible);
        dest.writeLong(mLastTimeForegroundServiceUsed);
        dest.writeLong(mTotalTimeForegroundServiceUsed);
    }

    public static final Parcelable.Creator<UsageStats> CREATOR =
            new Parcelable.Creator<UsageStats>() {
                @Override
                public UsageStats createFromParcel(Parcel in) {
                    return new UsageStats(in);
                }

                @Override
                public UsageStats[] newArray(int size) {
                    return new UsageStats[size];
                }
            };
}
