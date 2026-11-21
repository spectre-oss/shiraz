// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Manifest <attribution> declaration pairing an attribution tag with its label resource ID. */
public final class Attribution implements Parcelable {
    private final String mTag;
    private final int mLabel;

    public Attribution(String tag, int label) {
        mTag = Objects.requireNonNull(tag);
        mLabel = label;
    }

    Attribution(Parcel in) {
        mTag = in.readString();
        mLabel = in.readInt();
    }

    public String getTag() {
        return mTag;
    }

    public int getLabel() {
        return mLabel;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mTag);
        dest.writeInt(mLabel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Attribution> CREATOR = new Creator<Attribution>() {
        @Override
        public Attribution createFromParcel(Parcel in) {
            return new Attribution(in);
        }

        @Override
        public Attribution[] newArray(int size) {
            return new Attribution[size];
        }
    };
}
