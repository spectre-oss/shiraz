// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public final class StreamEventResponse extends BroadcastInfoResponse implements Parcelable {
    private final int mEventId;
    private final long mNptMillis;
    private final byte[] mData;

    public StreamEventResponse(
            int requestId,
            int sequence,
            int responseResult,
            int eventId,
            long nptMillis,
            byte[] data) {
        super(TvInputManager.BROADCAST_INFO_STREAM_EVENT, requestId, sequence, responseResult);
        mEventId = eventId;
        mNptMillis = nptMillis;
        mData = data != null ? Arrays.copyOf(data, data.length) : null;
    }

    StreamEventResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_STREAM_EVENT, source);
        mEventId = source.readInt();
        mNptMillis = source.readLong();
        mData = source.createByteArray();
    }

    static StreamEventResponse createFromParcelBody(Parcel source) {
        return new StreamEventResponse(source);
    }

    public int getEventId() {
        return mEventId;
    }

    public long getNptMillis() {
        return mNptMillis;
    }

    public byte[] getData() {
        return mData != null ? Arrays.copyOf(mData, mData.length) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mEventId);
        dest.writeLong(mNptMillis);
        dest.writeByteArray(mData);
    }

    public static final Parcelable.Creator<StreamEventResponse> CREATOR =
            new Parcelable.Creator<StreamEventResponse>() {
                @Override
                public StreamEventResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public StreamEventResponse[] newArray(int size) {
                    return new StreamEventResponse[size];
                }
            };
}
