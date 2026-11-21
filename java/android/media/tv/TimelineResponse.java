// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class TimelineResponse extends BroadcastInfoResponse implements Parcelable {
    private final String mSelector;
    private final int mUnitsPerTick;
    private final int mUnitsPerSecond;
    private final long mWallClock;
    private final long mTicks;

    public TimelineResponse(
            int requestId,
            int sequence,
            int responseResult,
            String selector,
            int unitsPerTick,
            int unitsPerSecond,
            long wallClock,
            long ticks) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE, requestId, sequence, responseResult);
        mSelector = selector;
        mUnitsPerTick = unitsPerTick;
        mUnitsPerSecond = unitsPerSecond;
        mWallClock = wallClock;
        mTicks = ticks;
    }

    TimelineResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE, source);
        mSelector = source.readString();
        mUnitsPerTick = source.readInt();
        mUnitsPerSecond = source.readInt();
        mWallClock = source.readLong();
        mTicks = source.readLong();
    }

    static TimelineResponse createFromParcelBody(Parcel source) {
        return new TimelineResponse(source);
    }

    public Uri getSelector() {
        return mSelector != null ? Uri.parse(mSelector) : null;
    }

    public int getUnitsPerTick() {
        return mUnitsPerTick;
    }

    public int getUnitsPerSecond() {
        return mUnitsPerSecond;
    }

    public long getWallClock() {
        return mWallClock;
    }

    public long getTicks() {
        return mTicks;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mSelector);
        dest.writeInt(mUnitsPerTick);
        dest.writeInt(mUnitsPerSecond);
        dest.writeLong(mWallClock);
        dest.writeLong(mTicks);
    }

    public static final Parcelable.Creator<TimelineResponse> CREATOR =
            new Parcelable.Creator<TimelineResponse>() {
                @Override
                public TimelineResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TimelineResponse[] newArray(int size) {
                    return new TimelineResponse[size];
                }
            };
}
