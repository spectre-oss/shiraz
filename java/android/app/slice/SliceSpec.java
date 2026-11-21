// SPDX-License-Identifier: GPL-3.0-only
package android.app.slice;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Specification and revision version of a Slice template. */
public final class SliceSpec implements Parcelable {
    private final String mType;
    private final int mRevision;

    public SliceSpec(String type, int revision) {
        this.mType = Objects.requireNonNull(type, "type");
        this.mRevision = revision;
    }

    private SliceSpec(Parcel source) {
        this.mType = Objects.requireNonNull(source.readString(), "type");
        this.mRevision = source.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeInt(mRevision);
    }

    public String getType() {
        return mType;
    }

    public int getRevision() {
        return mRevision;
    }

    public boolean canRender(SliceSpec candidate) {
        if (candidate == null || !mType.equals(candidate.mType)) {
            return false;
        }
        return mRevision >= candidate.mRevision;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SliceSpec)) {
            return false;
        }
        SliceSpec other = (SliceSpec) obj;
        return mType.equals(other.mType) && mRevision == other.mRevision;
    }

    @Override
    public int hashCode() {
        return mType.hashCode() + mRevision;
    }

    @Override
    public String toString() {
        return "SliceSpec{" + mType + "," + mRevision + "}";
    }

    public static final Parcelable.Creator<SliceSpec> CREATOR =
            new Parcelable.Creator<SliceSpec>() {
                @Override
                public SliceSpec createFromParcel(Parcel source) {
                    return new SliceSpec(source);
                }

                @Override
                public SliceSpec[] newArray(int size) {
                    return new SliceSpec[size];
                }
            };
}
