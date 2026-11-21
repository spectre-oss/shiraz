// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public final class CompletionInfo implements Parcelable {
    private final long mId;
    private final int mPosition;
    private final CharSequence mText;
    private final CharSequence mLabel;

    public CompletionInfo(long id, int index, CharSequence text) {
        mId = id;
        mPosition = index;
        mText = text;
        mLabel = null;
    }

    public CompletionInfo(long id, int index, CharSequence text, CharSequence label) {
        mId = id;
        mPosition = index;
        mText = text;
        mLabel = label;
    }

    CompletionInfo(Parcel in) {
        mId = in.readLong();
        mPosition = in.readInt();
        mText = in.readString();
        mLabel = in.readString();
    }

    public long getId() {
        return mId;
    }

    public int getPosition() {
        return mPosition;
    }

    public CharSequence getText() {
        return mText;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    @Override
    public String toString() {
        return "CompletionInfo{#" + mPosition + " \"" + mText
                + "\" id=" + mId + " label=" + mLabel + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeInt(mPosition);
        dest.writeString(mText != null ? mText.toString() : null);
        dest.writeString(mLabel != null ? mLabel.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CompletionInfo> CREATOR = new Creator<CompletionInfo>() {
        @Override
        public CompletionInfo createFromParcel(Parcel in) {
            return new CompletionInfo(in);
        }

        @Override
        public CompletionInfo[] newArray(int size) {
            return new CompletionInfo[size];
        }
    };
}
