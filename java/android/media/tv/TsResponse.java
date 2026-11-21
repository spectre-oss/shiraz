// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class TsResponse extends BroadcastInfoResponse implements Parcelable {
    private final String mSharedFilterToken;

    public TsResponse(
            int requestId, int sequence, int responseResult, String sharedFilterToken) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TS, requestId, sequence, responseResult);
        mSharedFilterToken = sharedFilterToken;
    }

    TsResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TS, source);
        mSharedFilterToken = source.readString();
    }

    static TsResponse createFromParcelBody(Parcel source) {
        return new TsResponse(source);
    }

    public String getSharedFilterToken() {
        return mSharedFilterToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mSharedFilterToken);
    }

    public static final Parcelable.Creator<TsResponse> CREATOR =
            new Parcelable.Creator<TsResponse>() {
                @Override
                public TsResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TsResponse[] newArray(int size) {
                    return new TsResponse[size];
                }
            };
}
