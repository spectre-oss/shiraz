// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

public final class CorrectionInfo implements Parcelable {
    private final int mOffset;
    private final CharSequence mOldText;
    private final CharSequence mNewText;

    public CorrectionInfo(int offset, CharSequence oldText, CharSequence newText) {
        mOffset = offset;
        mOldText = oldText;
        mNewText = newText;
    }

    private CorrectionInfo(Parcel source) {
        mOffset = source.readInt();
        mOldText = source.readString();
        mNewText = source.readString();
    }

    public int getOffset() {
        return mOffset;
    }

    public CharSequence getOldText() {
        return mOldText;
    }

    public CharSequence getNewText() {
        return mNewText;
    }

    @Override
    public String toString() {
        return "CorrectionInfo{#" + mOffset + " \"" + mOldText + "\" -> \"" + mNewText + "\"}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOffset);
        dest.writeString(mOldText != null ? mOldText.toString() : null);
        dest.writeString(mNewText != null ? mNewText.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CorrectionInfo> CREATOR =
            new Parcelable.Creator<CorrectionInfo>() {
                @Override
                public CorrectionInfo createFromParcel(Parcel source) {
                    return new CorrectionInfo(source);
                }

                @Override
                public CorrectionInfo[] newArray(int size) {
                    return new CorrectionInfo[size];
                }
            };
}
