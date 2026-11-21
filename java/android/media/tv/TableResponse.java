// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import java.util.Arrays;

public final class TableResponse extends BroadcastInfoResponse implements Parcelable {
    public static final class Builder {
        private final int mRequestId;
        private final int mSequence;
        private final int mResponseResult;
        private final int mVersion;
        private final int mSize;
        private Uri mTableUri;
        private byte[] mTableByteArray;
        private SharedMemory mTableSharedMemory;

        public Builder(
                int requestId, int sequence, int responseResult, int version, int size) {
            mRequestId = requestId;
            mSequence = sequence;
            mResponseResult = responseResult;
            mVersion = version;
            mSize = size;
        }

        public Builder setTableUri(Uri uri) {
            mTableUri = uri;
            mTableByteArray = null;
            mTableSharedMemory = null;
            return this;
        }

        public Builder setTableByteArray(byte[] bytes) {
            mTableByteArray = bytes != null ? Arrays.copyOf(bytes, bytes.length) : null;
            mTableUri = null;
            mTableSharedMemory = null;
            return this;
        }

        public Builder setTableSharedMemory(SharedMemory sharedMemory) {
            mTableSharedMemory = sharedMemory;
            mTableUri = null;
            mTableByteArray = null;
            return this;
        }

        public TableResponse build() {
            return new TableResponse(
                    mRequestId, mSequence, mResponseResult,
                    mVersion, mSize, mTableUri, mTableByteArray, mTableSharedMemory);
        }
    }

    private final Uri mTableUri;
    private final int mVersion;
    private final int mSize;
    private final byte[] mTableByteArray;
    private final SharedMemory mTableSharedMemory;

    public TableResponse(
            int requestId,
            int sequence,
            int responseResult,
            Uri tableUri,
            int version,
            int size) {
        this(requestId, sequence, responseResult, version, size, tableUri, null, null);
    }

    private TableResponse(
            int requestId,
            int sequence,
            int responseResult,
            int version,
            int size,
            Uri tableUri,
            byte[] tableByteArray,
            SharedMemory tableSharedMemory) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TABLE, requestId, sequence, responseResult);
        mVersion = version;
        mSize = size;
        mTableUri = tableUri;
        mTableByteArray = tableByteArray;
        mTableSharedMemory = tableSharedMemory;
    }

    TableResponse(Parcel source) {
        super(TvInputManager.BROADCAST_INFO_TYPE_TABLE, source);
        String uriStr = source.readString();
        mTableUri = uriStr != null ? Uri.parse(uriStr) : null;
        mVersion = source.readInt();
        mSize = source.readInt();
        mTableByteArray = source.createByteArray();
        mTableSharedMemory = source.readInt() != 0
                ? SharedMemory.CREATOR.createFromParcel(source)
                : null;
    }

    static TableResponse createFromParcelBody(Parcel source) {
        return new TableResponse(source);
    }

    public Uri getTableUri() {
        return mTableUri;
    }

    public byte[] getTableByteArray() {
        return mTableByteArray != null
                ? Arrays.copyOf(mTableByteArray, mTableByteArray.length)
                : null;
    }

    public SharedMemory getTableSharedMemory() {
        return mTableSharedMemory;
    }

    public int getVersion() {
        return mVersion;
    }

    public int getSize() {
        return mSize;
    }

    @Override
    public int describeContents() {
        return mTableSharedMemory != null ? mTableSharedMemory.describeContents() : 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mTableUri != null ? mTableUri.toString() : null);
        dest.writeInt(mVersion);
        dest.writeInt(mSize);
        dest.writeByteArray(mTableByteArray);
        if (mTableSharedMemory != null) {
            dest.writeInt(1);
            mTableSharedMemory.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Parcelable.Creator<TableResponse> CREATOR =
            new Parcelable.Creator<TableResponse>() {
                @Override
                public TableResponse createFromParcel(Parcel source) {
                    source.readInt();
                    return createFromParcelBody(source);
                }

                @Override
                public TableResponse[] newArray(int size) {
                    return new TableResponse[size];
                }
            };
}
