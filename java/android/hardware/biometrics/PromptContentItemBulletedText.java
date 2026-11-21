// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.biometrics;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PromptContentItemBulletedText implements PromptContentItem, Parcelable {
    private final String mText;

    public PromptContentItemBulletedText(String text) {
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
        if (!(o instanceof PromptContentItemBulletedText)) return false;
        return Objects.equals(mText, ((PromptContentItemBulletedText) o).mText);
    }

    @Override
    public int hashCode() {
        return mText.hashCode();
    }

    public static final Parcelable.Creator<PromptContentItemBulletedText> CREATOR =
            new Parcelable.Creator<PromptContentItemBulletedText>() {
                @Override
                public PromptContentItemBulletedText createFromParcel(Parcel in) {
                    String text = in.readString();
                    return new PromptContentItemBulletedText(text == null ? "" : text);
                }

                @Override
                public PromptContentItemBulletedText[] newArray(int size) {
                    return new PromptContentItemBulletedText[size];
                }
            };
}
