// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class ExternalStorageStats implements Parcelable {
    public long totalBytes;
    public long audioBytes;
    public long videoBytes;
    public long imageBytes;
    public long appBytes;

    public ExternalStorageStats() {
    }

    ExternalStorageStats(Parcel in) {
        totalBytes = in.readLong();
        audioBytes = in.readLong();
        videoBytes = in.readLong();
        imageBytes = in.readLong();
        appBytes = in.readLong();
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getAudioBytes() {
        return audioBytes;
    }

    public long getVideoBytes() {
        return videoBytes;
    }

    public long getImageBytes() {
        return imageBytes;
    }

    public long getAppBytes() {
        return appBytes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(totalBytes);
        dest.writeLong(audioBytes);
        dest.writeLong(videoBytes);
        dest.writeLong(imageBytes);
        dest.writeLong(appBytes);
    }

    public static final Parcelable.Creator<ExternalStorageStats> CREATOR =
            new Parcelable.Creator<ExternalStorageStats>() {
                @Override
                public ExternalStorageStats createFromParcel(Parcel in) {
                    return new ExternalStorageStats(in);
                }

                @Override
                public ExternalStorageStats[] newArray(int size) {
                    return new ExternalStorageStats[size];
                }
            };
}
