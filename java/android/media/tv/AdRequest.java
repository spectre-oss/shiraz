// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

public final class AdRequest implements Parcelable {
    public static final int REQUEST_TYPE_START = 1;
    public static final int REQUEST_TYPE_STOP = 2;

    private final int mId;
    private final int mRequestType;
    private final ParcelFileDescriptor mFileDescriptor;
    private final Uri mUri;
    private final long mStartTime;
    private final long mStopTime;
    private final long mEchoInterval;
    private final String mMediaFileType;
    private final Bundle mMetadata;

    public AdRequest(
            int id,
            int requestType,
            ParcelFileDescriptor fileDescriptor,
            long startTime,
            long stopTime,
            long echoInterval,
            String mediaFileType,
            Bundle metadata) {
        this(id, requestType, fileDescriptor, null, startTime, stopTime,
                echoInterval, mediaFileType, metadata);
    }

    public AdRequest(
            int id,
            int requestType,
            Uri uri,
            long startTime,
            long stopTime,
            long echoInterval,
            Bundle metadata) {
        this(id, requestType, null, uri, startTime, stopTime, echoInterval, null, metadata);
    }

    private AdRequest(
            int id,
            int requestType,
            ParcelFileDescriptor fileDescriptor,
            Uri uri,
            long startTime,
            long stopTime,
            long echoInterval,
            String mediaFileType,
            Bundle metadata) {
        mId = id;
        mRequestType = requestType;
        mFileDescriptor = fileDescriptor;
        mUri = uri;
        mStartTime = startTime;
        mStopTime = stopTime;
        mEchoInterval = echoInterval;
        mMediaFileType = mediaFileType;
        mMetadata = metadata != null ? new Bundle(metadata) : null;
    }

    public int getId() {
        return mId;
    }

    public int getRequestType() {
        return mRequestType;
    }

    public ParcelFileDescriptor getFileDescriptor() {
        return mFileDescriptor;
    }

    public Uri getUri() {
        return mUri;
    }

    public long getStartTimeMillis() {
        return mStartTime;
    }

    public long getStopTimeMillis() {
        return mStopTime;
    }

    public long getEchoIntervalMillis() {
        return mEchoInterval;
    }

    public String getMediaFileType() {
        return mMediaFileType;
    }

    public Bundle getMetadata() {
        return mMetadata != null ? new Bundle(mMetadata) : null;
    }

    @Override
    public int describeContents() {
        return mFileDescriptor != null ? CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mRequestType);
        if (mFileDescriptor != null) {
            dest.writeInt(1);
            mFileDescriptor.writeToParcel(dest, flags);
        } else if (mUri != null) {
            dest.writeInt(2);
            dest.writeString(mUri.toString());
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(mStartTime);
        dest.writeLong(mStopTime);
        dest.writeLong(mEchoInterval);
        dest.writeString(mMediaFileType);
        dest.writeBundle(mMetadata);
    }

    public static final Parcelable.Creator<AdRequest> CREATOR =
            new Parcelable.Creator<AdRequest>() {
                @Override
                public AdRequest createFromParcel(Parcel source) {
                    int id = source.readInt();
                    int requestType = source.readInt();
                    int readType = source.readInt();
                    ParcelFileDescriptor pfd = null;
                    Uri uri = null;
                    if (readType == 1) {
                        pfd = ParcelFileDescriptor.CREATOR.createFromParcel(source);
                    } else if (readType == 2) {
                        String u = source.readString();
                        uri = u != null ? Uri.parse(u) : null;
                    }
                    long startTime = source.readLong();
                    long stopTime = source.readLong();
                    long echoInterval = source.readLong();
                    String mediaFileType = source.readString();
                    Bundle metadata = source.readBundle();
                    return new AdRequest(
                            id, requestType, pfd, uri, startTime, stopTime,
                            echoInterval, mediaFileType, metadata);
                }

                @Override
                public AdRequest[] newArray(int size) {
                    return new AdRequest[size];
                }
            };
}
