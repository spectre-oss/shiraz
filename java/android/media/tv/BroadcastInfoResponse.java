// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class BroadcastInfoResponse implements Parcelable {
    public static final int RESPONSE_RESULT_ERROR = 1;
    public static final int RESPONSE_RESULT_OK = 2;
    public static final int RESPONSE_RESULT_CANCEL = 3;

    private final int mType;
    private final int mRequestId;
    private final int mSequence;
    private final int mResponseResult;

    BroadcastInfoResponse(int type, int requestId, int sequence, int responseResult) {
        mType = type;
        mRequestId = requestId;
        mSequence = sequence;
        mResponseResult = responseResult;
    }

    BroadcastInfoResponse(int type, Parcel source) {
        mType = type;
        mRequestId = source.readInt();
        mSequence = source.readInt();
        mResponseResult = source.readInt();
    }

    public int getType() {
        return mType;
    }

    public int getRequestId() {
        return mRequestId;
    }

    public int getSequence() {
        return mSequence;
    }

    public int getResponseResult() {
        return mResponseResult;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mRequestId);
        dest.writeInt(mSequence);
        dest.writeInt(mResponseResult);
    }

    public static final Parcelable.Creator<BroadcastInfoResponse> CREATOR =
            new Parcelable.Creator<BroadcastInfoResponse>() {
                @Override
                public BroadcastInfoResponse createFromParcel(Parcel source) {
                    int type = source.readInt();
                    switch (type) {
                        case TvInputManager.BROADCAST_INFO_TYPE_TS:
                            return TsResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_TABLE:
                            return TableResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_SECTION:
                            return SectionResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_PES:
                            return PesResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_STREAM_EVENT:
                            return StreamEventResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_DSMCC:
                            return DsmccResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_COMMAND:
                            return CommandResponse.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_TIMELINE:
                            return TimelineResponse.createFromParcelBody(source);
                        default:
                            throw new IllegalStateException(
                                    "Unexpected broadcast info response type (" + type
                                            + ") in parcel.");
                    }
                }

                @Override
                public BroadcastInfoResponse[] newArray(int size) {
                    return new BroadcastInfoResponse[size];
                }
            };
}
