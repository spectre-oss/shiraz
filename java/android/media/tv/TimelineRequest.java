// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class TimelineRequest extends BroadcastInfoRequest implements Parcelable {
    private final int mIntervalMillis;
    private final String mSelector;

    public TimelineRequest(int requestId, int option, int intervalMillis) {
        this(requestId, option, intervalMillis, null);
    }

    public TimelineRequest(int requestId, int option, int intervalMillis, String selector) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE, requestId, option);
        mIntervalMillis = intervalMillis;
        mSelector = selector;
    }

    TimelineRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE, source);
        mIntervalMillis = source.readInt();
        mSelector = source.readString();
    }

    static TimelineRequest createFromParcelBody(Parcel source) {
        return new TimelineRequest(source);
    }

    public int getIntervalMillis() {
        return mIntervalMillis;
    }

    public String getSelector() {
        return mSelector;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mIntervalMillis);
        dest.writeString(mSelector);
    }

    public static final Parcelable.Creator<TimelineRequest> CREATOR =
            new Parcelable.Creator<TimelineRequest>() {
                @Override
                public TimelineRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TimelineRequest[] newArray(int size) {
                    return new TimelineRequest[size];
                }
            };
}
