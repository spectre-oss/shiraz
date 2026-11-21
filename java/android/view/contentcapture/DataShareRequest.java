// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DataShareRequest implements Parcelable {
    private final String mPackageName;
    private final LocusId mLocusId;
    private final String mMimeType;

    public DataShareRequest(LocusId locusId, String mimeType) {
        Objects.requireNonNull(mimeType, "mimeType");
        mPackageName = "android";
        mLocusId = locusId;
        mMimeType = mimeType;
    }

    private DataShareRequest(Parcel in) {
        mPackageName = in.readString();
        mLocusId = in.readParcelable(LocusId.class.getClassLoader());
        mMimeType = in.readString();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public LocusId getLocusId() {
        return mLocusId;
    }

    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public String toString() {
        return "DataShareRequest { packageName = " + mPackageName
                + ", locusId = " + mLocusId
                + ", mimeType = " + mMimeType + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataShareRequest)) return false;
        DataShareRequest that = (DataShareRequest) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mLocusId, that.mLocusId)
                && Objects.equals(mMimeType, that.mMimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPackageName, mLocusId, mMimeType);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeParcelable(mLocusId, flags);
        dest.writeString(mMimeType);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<DataShareRequest> CREATOR =
            new Parcelable.Creator<DataShareRequest>() {
                @Override
                public DataShareRequest createFromParcel(Parcel in) {
                    return new DataShareRequest(in);
                }

                @Override
                public DataShareRequest[] newArray(int size) {
                    return new DataShareRequest[size];
                }
            };
}
