// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public final class CommandResponse extends BroadcastInfoResponse implements Parcelable {
    public static final String RESPONSE_TYPE_XML = "xml";
    public static final String RESPONSE_TYPE_JSON = "json";

    private final String mResponse;
    private final String mResponseType;

    public CommandResponse(
            int requestId,
            int sequence,
            int responseResult,
            String response,
            String responseType) {
        super(TvInputManager.BROADCAST_INFO_TYPE_COMMAND, requestId, sequence, responseResult);
        mResponse = response;
        mResponseType = responseType;
    }

    CommandResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_COMMAND, source);
        mResponse = source.readString();
        mResponseType = source.readString();
    }

    static CommandResponse createFromParcelBody(Parcel source) {
        return new CommandResponse(source);
    }

    public String getResponse() {
        return mResponse;
    }

    public String getResponseType() {
        return mResponseType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mResponse);
        dest.writeString(mResponseType);
    }

    public static final Parcelable.Creator<CommandResponse> CREATOR =
            new Parcelable.Creator<CommandResponse>() {
                @Override
                public CommandResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public CommandResponse[] newArray(int size) {
                    return new CommandResponse[size];
                }
            };
}
