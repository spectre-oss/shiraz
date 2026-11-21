// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class StreamEventRequest extends BroadcastInfoRequest implements Parcelable {
    private final Uri mTargetUri;
    private final String mEventName;

    public StreamEventRequest(int requestId, int option, Uri targetUri, String eventName) {
        super(TvInputManager.BROADCAST_INFO_STREAM_EVENT, requestId, option);
        mTargetUri = targetUri;
        mEventName = eventName;
    }

    StreamEventRequest(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_STREAM_EVENT, source);
        String uriStr = source.readString();
        mTargetUri = uriStr != null ? Uri.parse(uriStr) : null;
        mEventName = source.readString();
    }

    static StreamEventRequest createFromParcelBody(Parcel source) {
        return new StreamEventRequest(source);
    }

    public Uri getTargetUri() {
        return mTargetUri;
    }

    public String getEventName() {
        return mEventName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mTargetUri != null ? mTargetUri.toString() : null);
        dest.writeString(mEventName);
    }

    public static final Parcelable.Creator<StreamEventRequest> CREATOR =
            new Parcelable.Creator<StreamEventRequest>() {
                @Override
                public StreamEventRequest createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public StreamEventRequest[] newArray(int size) {
                    return new StreamEventRequest[size];
                }
            };
}
