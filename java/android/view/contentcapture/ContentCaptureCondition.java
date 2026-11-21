// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ContentCaptureCondition implements Parcelable {
    public static final int FLAG_IS_REGEX = 2;

    private final LocusId mLocusId;
    private final int mFlags;

    public ContentCaptureCondition(LocusId locusId, int flags) {
        mLocusId = Objects.requireNonNull(locusId, "locusId");
        mFlags = flags;
    }

    public LocusId getLocusId() {
        return mLocusId;
    }

    public int getFlags() {
        return mFlags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLocusId, Integer.valueOf(mFlags));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ContentCaptureCondition)) return false;
        ContentCaptureCondition other = (ContentCaptureCondition) obj;
        return mFlags == other.mFlags && Objects.equals(mLocusId, other.mLocusId);
    }

    @Override
    public String toString() {
        return mLocusId + " (flags=" + mFlags + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mLocusId, flags);
        parcel.writeInt(mFlags);
    }

    public static final Parcelable.Creator<ContentCaptureCondition> CREATOR =
            new Parcelable.Creator<ContentCaptureCondition>() {
                @Override
                public ContentCaptureCondition createFromParcel(Parcel parcel) {
                    LocusId locusId = parcel.readParcelable(LocusId.class.getClassLoader());
                    int flags = parcel.readInt();
                    return new ContentCaptureCondition(locusId, flags);
                }

                @Override
                public ContentCaptureCondition[] newArray(int size) {
                    return new ContentCaptureCondition[size];
                }
            };
}
