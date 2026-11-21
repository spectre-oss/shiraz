// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** An identifier for an unique state (locus) in the application. */
public final class LocusId implements Parcelable {
    private final String mId;

    public LocusId(String id) {
        if (TextUtils.isEmpty(id)) {
            throw new IllegalArgumentException("id cannot be empty");
        }
        mId = id;
    }

    public String getId() {
        return mId;
    }

    @Override
    public int hashCode() {
        return 31 + (mId == null ? 0 : mId.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LocusId)) return false;
        LocusId other = (LocusId) obj;
        return mId.equals(other.mId);
    }

    @Override
    public String toString() {
        return "LocusId[" + mId.length() + "_chars]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
    }

    public static final Parcelable.Creator<LocusId> CREATOR =
            new Parcelable.Creator<LocusId>() {
                @Override
                public LocusId createFromParcel(Parcel parcel) {
                    return new LocusId(parcel.readString());
                }

                @Override
                public LocusId[] newArray(int size) {
                    return new LocusId[size];
                }
            };
}
