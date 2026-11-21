// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public class IccOpenLogicalChannelResponse implements Parcelable {
    public static final int STATUS_NO_ERROR = 1;
    public static final int STATUS_MISSING_RESOURCE = 2;
    public static final int STATUS_NO_SUCH_ELEMENT = 3;
    public static final int STATUS_UNKNOWN_ERROR = 4;
    public static final int INVALID_CHANNEL = -1;

    private final int mChannel;
    private final int mStatus;
    private final byte[] mSelectResponse;

    IccOpenLogicalChannelResponse(int channel, int status, byte[] selectResponse) {
        mChannel = channel;
        mStatus = status;
        mSelectResponse = selectResponse != null ? selectResponse.clone() : null;
    }

    private IccOpenLogicalChannelResponse(Parcel in) {
        mChannel = in.readInt();
        mStatus = in.readInt();
        int arrayLength = in.readInt();
        if (arrayLength >= 0) {
            mSelectResponse = new byte[arrayLength];
            in.readByteArray(mSelectResponse);
        } else {
            mSelectResponse = null;
        }
    }

    public int getChannel() {
        return mChannel;
    }

    public int getStatus() {
        return mStatus;
    }

    public byte[] getSelectResponse() {
        return mSelectResponse != null ? mSelectResponse.clone() : null;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mChannel);
        out.writeInt(mStatus);
        if (mSelectResponse != null) {
            out.writeInt(mSelectResponse.length);
            out.writeByteArray(mSelectResponse);
        } else {
            out.writeInt(-1);
        }
    }

    public String toString() {
        return "Channel: " + mChannel + " Status: " + mStatus
                + " Response: " + Arrays.toString(mSelectResponse);
    }

    public static final Parcelable.Creator<IccOpenLogicalChannelResponse> CREATOR =
            new Parcelable.Creator<IccOpenLogicalChannelResponse>() {
                public IccOpenLogicalChannelResponse createFromParcel(Parcel in) {
                    return new IccOpenLogicalChannelResponse(in);
                }

                public IccOpenLogicalChannelResponse[] newArray(int size) {
                    return new IccOpenLogicalChannelResponse[size];
                }
            };
}
