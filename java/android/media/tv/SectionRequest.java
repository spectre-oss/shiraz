// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class SectionRequest extends BroadcastInfoRequest implements Parcelable {
    private final int mTsPid;
    private final int mTableId;
    private final int mVersion;

    public SectionRequest(int requestId, int option, int tsPid, int tableId, int version) {
        super(TvInputManager.BROADCAST_INFO_TYPE_SECTION, requestId, option);
        mTsPid = tsPid;
        mTableId = tableId;
        mVersion = version;
    }

    SectionRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_SECTION, source);
        mTsPid = source.readInt();
        mTableId = source.readInt();
        mVersion = source.readInt();
    }

    static SectionRequest createFromParcelBody(Parcel source) {
        return new SectionRequest(source);
    }

    public int getTsPid() {
        return mTsPid;
    }

    public int getTableId() {
        return mTableId;
    }

    public int getVersion() {
        return mVersion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mTsPid);
        dest.writeInt(mTableId);
        dest.writeInt(mVersion);
    }

    public static final Parcelable.Creator<SectionRequest> CREATOR =
            new Parcelable.Creator<SectionRequest>() {
                @Override
                public SectionRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public SectionRequest[] newArray(int size) {
                    return new SectionRequest[size];
                }
            };
}
