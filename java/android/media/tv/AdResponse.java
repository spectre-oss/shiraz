// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class AdResponse implements Parcelable {
    public static final int RESPONSE_TYPE_PLAYING = 1;
    public static final int RESPONSE_TYPE_FINISHED = 2;
    public static final int RESPONSE_TYPE_STOPPED = 3;
    public static final int RESPONSE_TYPE_ERROR = 4;
    public static final int RESPONSE_TYPE_BUFFERING = 5;

    private final int mId;
    private final int mResponseType;
    private final long mElapsedTime;

    public AdResponse(int id, int responseType, long elapsedTime) {
        mId = id;
        mResponseType = responseType;
        mElapsedTime = elapsedTime;
    }

    public int getId() {
        return mId;
    }

    public int getResponseType() {
        return mResponseType;
    }

    public long getElapsedTimeMillis() {
        return mElapsedTime;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mResponseType);
        dest.writeLong(mElapsedTime);
    }

    public static final Parcelable.Creator<AdResponse> CREATOR =
            new Parcelable.Creator<AdResponse>() {
                @Override
                public AdResponse createFromParcel(Parcel source) {
                    return new AdResponse(
                            source.readInt(),
                            source.readInt(),
                            source.readLong());
                }

                @Override
                public AdResponse[] newArray(int size) {
                    return new AdResponse[size];
                }
            };
}
