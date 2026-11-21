// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DsmccResponse extends BroadcastInfoResponse implements Parcelable {
    public static final String BIOP_MESSAGE_TYPE_DIRECTORY = "directory";
    public static final String BIOP_MESSAGE_TYPE_FILE = "file";
    public static final String BIOP_MESSAGE_TYPE_SERVICE_GATEWAY = "service_gateway";
    public static final String BIOP_MESSAGE_TYPE_STREAM = "stream";

    private final String mBiopMessageType;
    private final ParcelFileDescriptor mFileDescriptor;
    private final List<String> mChildList;
    private final int[] mEventIds;
    private final String[] mEventNames;

    public DsmccResponse(
            int requestId,
            int sequence,
            int responseResult,
            ParcelFileDescriptor file) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, requestId, sequence, responseResult);
        mBiopMessageType = BIOP_MESSAGE_TYPE_FILE;
        mFileDescriptor = file;
        mChildList = null;
        mEventIds = null;
        mEventNames = null;
    }

    public DsmccResponse(
            int requestId,
            int sequence,
            int responseResult,
            boolean isServiceGateway,
            List<String> childList) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, requestId, sequence, responseResult);
        mBiopMessageType = isServiceGateway
                ? BIOP_MESSAGE_TYPE_SERVICE_GATEWAY
                : BIOP_MESSAGE_TYPE_DIRECTORY;
        mFileDescriptor = null;
        mChildList = childList != null ? new ArrayList<String>(childList) : null;
        mEventIds = null;
        mEventNames = null;
    }

    public DsmccResponse(
            int requestId,
            int sequence,
            int responseResult,
            int[] eventIds,
            String[] eventNames) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, requestId, sequence, responseResult);
        mBiopMessageType = BIOP_MESSAGE_TYPE_STREAM;
        mFileDescriptor = null;
        mChildList = null;
        mEventIds = eventIds != null ? Arrays.copyOf(eventIds, eventIds.length) : null;
        mEventNames = eventNames != null ? Arrays.copyOf(eventNames, eventNames.length) : null;
    }

    DsmccResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_DSMCC, source);
        mBiopMessageType = source.readString();
        if (BIOP_MESSAGE_TYPE_SERVICE_GATEWAY.equals(mBiopMessageType)
                || BIOP_MESSAGE_TYPE_DIRECTORY.equals(mBiopMessageType)) {
            int childNum = source.readInt();
            if (childNum > 0) {
                mChildList = new ArrayList<String>(childNum);
                for (int i = 0; i < childNum; i++) {
                    mChildList.add(source.readString());
                }
            } else {
                mChildList = null;
            }
            mFileDescriptor = null;
            mEventIds = null;
            mEventNames = null;
        } else if (BIOP_MESSAGE_TYPE_FILE.equals(mBiopMessageType)) {
            mFileDescriptor = source.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(source) : null;
            mChildList = null;
            mEventIds = null;
            mEventNames = null;
        } else if (BIOP_MESSAGE_TYPE_STREAM.equals(mBiopMessageType)) {
            int eventNum = source.readInt();
            if (eventNum > 0) {
                mEventIds = new int[eventNum];
                mEventNames = new String[eventNum];
                for (int i = 0; i < eventNum; i++) {
                    mEventIds[i] = source.readInt();
                    mEventNames[i] = source.readString();
                }
            } else {
                mEventIds = null;
                mEventNames = null;
            }
            mFileDescriptor = null;
            mChildList = null;
        } else {
            throw new IllegalStateException("unexpected BIOP message type");
        }
    }

    static DsmccResponse createFromParcelBody(Parcel source) {
        return new DsmccResponse(source);
    }

    public String getBiopMessageType() {
        return mBiopMessageType;
    }

    public ParcelFileDescriptor getFile() {
        if (!BIOP_MESSAGE_TYPE_FILE.equals(mBiopMessageType)) {
            throw new IllegalStateException("Not file object");
        }
        return mFileDescriptor;
    }

    public List<String> getChildList() {
        if (!BIOP_MESSAGE_TYPE_DIRECTORY.equals(mBiopMessageType)
                && !BIOP_MESSAGE_TYPE_SERVICE_GATEWAY.equals(mBiopMessageType)) {
            throw new IllegalStateException("Not directory object");
        }
        return mChildList != null ? new ArrayList<String>(mChildList) : new ArrayList<String>();
    }

    public int[] getStreamEventIds() {
        if (!BIOP_MESSAGE_TYPE_STREAM.equals(mBiopMessageType)) {
            throw new IllegalStateException("Not stream event object");
        }
        return mEventIds != null ? Arrays.copyOf(mEventIds, mEventIds.length) : new int[0];
    }

    public String[] getStreamEventNames() {
        if (!BIOP_MESSAGE_TYPE_STREAM.equals(mBiopMessageType)) {
            throw new IllegalStateException("Not stream event object");
        }
        return mEventNames != null
                ? Arrays.copyOf(mEventNames, mEventNames.length)
                : new String[0];
    }

    @Override
    public int describeContents() {
        return mFileDescriptor != null ? CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mBiopMessageType);
        if (BIOP_MESSAGE_TYPE_SERVICE_GATEWAY.equals(mBiopMessageType)
                || BIOP_MESSAGE_TYPE_DIRECTORY.equals(mBiopMessageType)) {
            if (mChildList != null && !mChildList.isEmpty()) {
                dest.writeInt(mChildList.size());
                for (int i = 0; i < mChildList.size(); i++) {
                    dest.writeString(mChildList.get(i));
                }
            } else {
                dest.writeInt(0);
            }
        } else if (BIOP_MESSAGE_TYPE_FILE.equals(mBiopMessageType)) {
            if (mFileDescriptor != null) {
                dest.writeInt(1);
                mFileDescriptor.writeToParcel(dest, flags);
            } else {
                dest.writeInt(0);
            }
        } else if (BIOP_MESSAGE_TYPE_STREAM.equals(mBiopMessageType)) {
            if (mEventIds != null && mEventIds.length > 0) {
                dest.writeInt(mEventIds.length);
                for (int i = 0; i < mEventIds.length; i++) {
                    dest.writeInt(mEventIds[i]);
                    dest.writeString(mEventNames != null && i < mEventNames.length
                            ? mEventNames[i] : null);
                }
            } else {
                dest.writeInt(0);
            }
        }
    }

    public static final Parcelable.Creator<DsmccResponse> CREATOR =
            new Parcelable.Creator<DsmccResponse>() {
                @Override
                public DsmccResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public DsmccResponse[] newArray(int size) {
                    return new DsmccResponse[size];
                }
            };
}
