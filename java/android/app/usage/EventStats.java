// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class EventStats implements Parcelable {
    public int mEventType;
    public long mBeginTimeStamp;
    public long mEndTimeStamp;
    public long mLastEventTime;
    public long mTotalTime;
    public int mCount;

    public EventStats() {
    }

    public EventStats(EventStats stats) {
        mEventType = stats.mEventType;
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastEventTime = stats.mLastEventTime;
        mTotalTime = stats.mTotalTime;
        mCount = stats.mCount;
    }

    EventStats(Parcel in) {
        mEventType = in.readInt();
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastEventTime = in.readLong();
        mTotalTime = in.readLong();
        mCount = in.readInt();
    }

    public int getEventType() {
        return mEventType;
    }

    public long getFirstTimeStamp() {
        return mBeginTimeStamp;
    }

    public long getLastTimeStamp() {
        return mEndTimeStamp;
    }

    public long getLastEventTime() {
        return mLastEventTime;
    }

    public int getCount() {
        return mCount;
    }

    public long getTotalTime() {
        return mTotalTime;
    }

    public void add(EventStats right) {
        if (mEventType != right.mEventType) {
            throw new IllegalArgumentException(
                    "Can't merge EventStats for event #" + mEventType
                            + " with EventStats for event #" + right.mEventType);
        }
        if (right.mBeginTimeStamp > mBeginTimeStamp) {
            mLastEventTime = Math.max(mLastEventTime, right.mLastEventTime);
        }
        mBeginTimeStamp = Math.min(mBeginTimeStamp, right.mBeginTimeStamp);
        mEndTimeStamp = Math.max(mEndTimeStamp, right.mEndTimeStamp);
        mTotalTime += right.mTotalTime;
        mCount += right.mCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastEventTime);
        dest.writeLong(mTotalTime);
        dest.writeInt(mCount);
    }

    public static final Parcelable.Creator<EventStats> CREATOR =
            new Parcelable.Creator<EventStats>() {
                @Override
                public EventStats createFromParcel(Parcel in) {
                    return new EventStats(in);
                }

                @Override
                public EventStats[] newArray(int size) {
                    return new EventStats[size];
                }
            };
}
