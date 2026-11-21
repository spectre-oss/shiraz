// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class FileInfo implements Parcelable {
    private final Uri uri;
    private final String mimeType;

    FileInfo(Uri uri, String mimeType) {
        this.uri = uri;
        this.mimeType = mimeType;
    }

    private FileInfo(Parcel in) {
        String u = in.readString();
        uri = u != null ? Uri.parse(u) : null;
        mimeType = in.readString();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri != null ? uri.toString() : null);
        dest.writeString(mimeType);
    }

    public int describeContents() {
        return 0;
    }

    public Uri getUri() {
        return uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileInfo)) return false;
        FileInfo fileInfo = (FileInfo) o;
        return Objects.equals(uri, fileInfo.uri)
                && Objects.equals(mimeType, fileInfo.mimeType);
    }

    public int hashCode() {
        return Objects.hash(uri, mimeType);
    }

    public static final Parcelable.Creator<FileInfo> CREATOR =
            new Parcelable.Creator<FileInfo>() {
                public FileInfo createFromParcel(Parcel in) {
                    return new FileInfo(in);
                }

                public FileInfo[] newArray(int size) {
                    return new FileInfo[size];
                }
            };
}
