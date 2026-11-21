// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class DsmccRequest extends BroadcastInfoRequest implements Parcelable {
    private final Uri mUri;

    public DsmccRequest(int requestId, int option, Uri uri) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, requestId, option);
        mUri = uri;
    }

    DsmccRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, source);
        String u = source.readString();
        mUri = u != null ? Uri.parse(u) : null;
    }

    static DsmccRequest createFromParcelBody(Parcel source) {
        return new DsmccRequest(source);
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mUri != null ? mUri.toString() : null);
    }

    public static final Parcelable.Creator<DsmccRequest> CREATOR =
            new Parcelable.Creator<DsmccRequest>() {
                @Override
                public DsmccRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public DsmccRequest[] newArray(int size) {
                    return new DsmccRequest[size];
                }
            };
}
