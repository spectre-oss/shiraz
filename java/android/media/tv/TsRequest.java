// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class TsRequest extends BroadcastInfoRequest implements Parcelable {
    private final int mTsPid;

    public TsRequest(int requestId, int option, int tsPid) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TS, requestId, option);
        mTsPid = tsPid;
    }

    TsRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TS, source);
        mTsPid = source.readInt();
    }

    static TsRequest createFromParcelBody(Parcel source) {
        return new TsRequest(source);
    }

    public int getTsPid() {
        return mTsPid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mTsPid);
    }

    public static final Parcelable.Creator<TsRequest> CREATOR =
            new Parcelable.Creator<TsRequest>() {
                @Override
                public TsRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TsRequest[] newArray(int size) {
                    return new TsRequest[size];
                }
            };
}
