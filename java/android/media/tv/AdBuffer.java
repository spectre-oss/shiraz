// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import java.util.Objects;

public final class AdBuffer implements Parcelable {
    private final int mId;
    private final String mMimeType;
    private final SharedMemory mBuffer;
    private final int mOffset;
    private final int mLength;
    private final long mPresentationTimeUs;
    private final int mFlags;

    public AdBuffer(
            int id,
            String mimeType,
            SharedMemory buffer,
            int offset,
            int length,
            long presentationTimeUs,
            int flags) {
        mId = id;
        mMimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        mBuffer = Objects.requireNonNull(buffer, "buffer must not be null");
        mOffset = offset;
        mLength = length;
        mPresentationTimeUs = presentationTimeUs;
        mFlags = flags;
    }

    private AdBuffer(Parcel in) {
        mId = in.readInt();
        mMimeType = in.readString();
        mBuffer = in.readInt() != 0 ? SharedMemory.CREATOR.createFromParcel(in) : null;
        mOffset = in.readInt();
        mLength = in.readInt();
        mPresentationTimeUs = in.readLong();
        mFlags = in.readInt();
    }

    public int getId() {
        return mId;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public SharedMemory getSharedMemory() {
        return mBuffer;
    }

    public int getOffset() {
        return mOffset;
    }

    public int getLength() {
        return mLength;
    }

    public long getPresentationTimeUs() {
        return mPresentationTimeUs;
    }

    public int getFlags() {
        return mFlags;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mMimeType);
        if (mBuffer != null) {
            dest.writeInt(1);
            mBuffer.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mOffset);
        dest.writeInt(mLength);
        dest.writeLong(mPresentationTimeUs);
        dest.writeInt(mFlags);
    }

    @Override
    public int describeContents() {
        return mBuffer != null ? mBuffer.describeContents() : 0;
    }

    public static final Parcelable.Creator<AdBuffer> CREATOR =
            new Parcelable.Creator<AdBuffer>() {
                @Override
                public AdBuffer createFromParcel(Parcel in) {
                    return new AdBuffer(in);
                }

                @Override
                public AdBuffer[] newArray(int size) {
                    return new AdBuffer[size];
                }
            };
}
