// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class PesResponse extends BroadcastInfoResponse implements Parcelable {
    private final String mSharedFilterToken;

    public PesResponse(
            int requestId, int sequence, int responseResult, String sharedFilterToken) {
        super(TvInputManager.BROADCAST_INFO_TYPE_PES, requestId, sequence, responseResult);
        mSharedFilterToken = sharedFilterToken;
    }

    PesResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_PES, source);
        mSharedFilterToken = source.readString();
    }

    static PesResponse createFromParcelBody(Parcel source) {
        return new PesResponse(source);
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

    public static final Parcelable.Creator<PesResponse> CREATOR =
            new Parcelable.Creator<PesResponse>() {
                @Override
                public PesResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public PesResponse[] newArray(int size) {
                    return new PesResponse[size];
                }
            };
}
