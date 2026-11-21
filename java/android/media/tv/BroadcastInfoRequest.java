// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class BroadcastInfoRequest implements Parcelable {
    public static final int REQUEST_OPTION_REPEAT = 0;
    public static final int REQUEST_OPTION_AUTO_UPDATE = 1;

    private final int mType;
    private final int mRequestId;
    private final int mOption;

    BroadcastInfoRequest(int type, int requestId, int option) {
        mType = type;
        mRequestId = requestId;
        mOption = option;
    }

    BroadcastInfoRequest(int type, Parcel source) {
        mType = type;
        mRequestId = source.readInt();
        mOption = source.readInt();
    }

    public int getType() {
        return mType;
    }

    public int getRequestId() {
        return mRequestId;
    }

    public int getOption() {
        return mOption;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mRequestId);
        dest.writeInt(mOption);
    }

    public static final Parcelable.Creator<BroadcastInfoRequest> CREATOR =
            new Parcelable.Creator<BroadcastInfoRequest>() {
                @Override
                public BroadcastInfoRequest createFromParcel(Parcel source) {
                    int type = source.readInt();
                    switch (type) {
                        case TvInputManager.BROADCAST_INFO_TYPE_TS:
                            return TsRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_TABLE:
                            return TableRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_SECTION:
                            return SectionRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_PES:
                            return PesRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_STREAM_EVENT:
                            return StreamEventRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_DSMCC:
                            return DsmccRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_COMMAND:
                            return CommandRequest.createFromParcelBody(source);
                        case TvInputManager.BROADCAST_INFO_TYPE_TIMELINE:
                            return TimelineRequest.createFromParcelBody(source);
                        default:
                            throw new IllegalStateException(
                                    "Unexpected broadcast info request type (" + type
                                            + ") in parcel.");
                    }
                }

                @Override
                public BroadcastInfoRequest[] newArray(int size) {
                    return new BroadcastInfoRequest[size];
                }
            };
}
