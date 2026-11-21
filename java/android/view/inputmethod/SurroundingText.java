// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SurroundingText implements Parcelable {
    private final CharSequence mText;
    private final int mSelectionStart;
    private final int mSelectionEnd;
    private final int mOffset;

    public SurroundingText(
            CharSequence text, int selectionStart, int selectionEnd, int offset) {
        Objects.requireNonNull(text, "text cannot be null");
        mText = copyCharSequence(text);
        mSelectionStart = selectionStart;
        mSelectionEnd = selectionEnd;
        mOffset = offset;
    }

    public CharSequence getText() {
        return mText;
    }

    public int getSelectionStart() {
        return mSelectionStart;
    }

    public int getSelectionEnd() {
        return mSelectionEnd;
    }

    public int getOffset() {
        return mOffset;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeString(mText.toString());
        out.writeInt(mSelectionStart);
        out.writeInt(mSelectionEnd);
        out.writeInt(mOffset);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private static CharSequence copyCharSequence(CharSequence source) {
        return source.subSequence(0, source.length()).toString();
    }

    public static final Parcelable.Creator<SurroundingText> CREATOR =
            new Parcelable.Creator<SurroundingText>() {
                @Override
                public SurroundingText createFromParcel(Parcel in) {
                    String text = in.readString();
                    int selStart = in.readInt();
                    int selEnd = in.readInt();
                    int offset = in.readInt();
                    return new SurroundingText(
                            text == null ? "" : text, selStart, selEnd, offset);
                }

                @Override
                public SurroundingText[] newArray(int size) {
                    return new SurroundingText[size];
                }
            };
}
