// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PromptContentItemPlainText implements PromptContentItem, Parcelable {
    private final String mText;

    public PromptContentItemPlainText(String text) {
        mText = Objects.requireNonNull(text, "text");
    }

    String getText() {
        return mText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromptContentItemPlainText)) return false;
        return Objects.equals(mText, ((PromptContentItemPlainText) o).mText);
    }

    @Override
    public int hashCode() {
        return mText.hashCode();
    }

    public static final Parcelable.Creator<PromptContentItemPlainText> CREATOR =
            new Parcelable.Creator<PromptContentItemPlainText>() {
                @Override
                public PromptContentItemPlainText createFromParcel(Parcel in) {
                    String text = in.readString();
                    return new PromptContentItemPlainText(text == null ? "" : text);
                }

                @Override
                public PromptContentItemPlainText[] newArray(int size) {
                    return new PromptContentItemPlainText[size];
                }
            };
}
