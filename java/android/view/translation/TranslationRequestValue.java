// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TranslationRequestValue implements Parcelable {
    private final CharSequence mText;

    private TranslationRequestValue(CharSequence text) {
        mText = text;
    }

    public static TranslationRequestValue forText(CharSequence text) {
        Objects.requireNonNull(text, "text must not be null");
        return new TranslationRequestValue(text);
    }

    public CharSequence getText() {
        return mText;
    }

    @Override
    public String toString() {
        return "TranslationRequestValue { text = " + mText + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranslationRequestValue)) return false;
        TranslationRequestValue that = (TranslationRequestValue) o;
        return Objects.equals(mText, that.mText);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mText);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mText != null ? mText.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationRequestValue> CREATOR =
            new Parcelable.Creator<TranslationRequestValue>() {
                @Override
                public TranslationRequestValue createFromParcel(Parcel in) {
                    String s = in.readString();
                    return new TranslationRequestValue(s != null ? s : "");
                }

                @Override
                public TranslationRequestValue[] newArray(int size) {
                    return new TranslationRequestValue[size];
                }
            };
}
