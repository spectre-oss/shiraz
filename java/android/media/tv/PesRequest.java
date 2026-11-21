// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class PesRequest extends BroadcastInfoRequest implements Parcelable {
    private final int mTsPid;
    private final int mStreamId;

    public PesRequest(int requestId, int option, int tsPid, int streamId) {
        super(TvInputManager.BROADCAST_INFO_TYPE_PES, requestId, option);
        mTsPid = tsPid;
        mStreamId = streamId;
    }

    PesRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_PES, source);
        mTsPid = source.readInt();
        mStreamId = source.readInt();
    }

    static PesRequest createFromParcelBody(Parcel source) {
        return new PesRequest(source);
    }

    public int getTsPid() {
        return mTsPid;
    }

    public int getStreamId() {
        return mStreamId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mTsPid);
        dest.writeInt(mStreamId);
    }

    public static final Parcelable.Creator<PesRequest> CREATOR =
            new Parcelable.Creator<PesRequest>() {
                @Override
                public PesRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public PesRequest[] newArray(int size) {
                    return new PesRequest[size];
                }
            };
}
