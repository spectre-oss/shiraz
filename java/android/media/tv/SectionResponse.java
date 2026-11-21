// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public final class SectionResponse extends BroadcastInfoResponse implements Parcelable {
    private final int mSessionId;
    private final int mVersion;
    private final Bundle mSessionData;

    public SectionResponse(
            int requestId,
            int sequence,
            int responseResult,
            int sessionId,
            int version,
            Bundle sessionData) {
        super(TvInputManager.BROADCAST_INFO_TYPE_SECTION, requestId, sequence, responseResult);
        mSessionId = sessionId;
        mVersion = version;
        mSessionData = sessionData != null ? new Bundle(sessionData) : null;
    }

    SectionResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_SECTION, source);
        mSessionId = source.readInt();
        mVersion = source.readInt();
        mSessionData = source.readBundle();
    }

    static SectionResponse createFromParcelBody(Parcel source) {
        return new SectionResponse(source);
    }

    public int getSessionId() {
        return mSessionId;
    }

    public int getVersion() {
        return mVersion;
    }

    public Bundle getSessionData() {
        return mSessionData != null ? new Bundle(mSessionData) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mSessionId);
        dest.writeInt(mVersion);
        dest.writeBundle(mSessionData);
    }

    public static final Parcelable.Creator<SectionResponse> CREATOR =
            new Parcelable.Creator<SectionResponse>() {
                @Override
                public SectionResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public SectionResponse[] newArray(int size) {
                    return new SectionResponse[size];
                }
            };
}
