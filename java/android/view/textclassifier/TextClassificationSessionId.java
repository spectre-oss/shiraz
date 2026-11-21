// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TextClassificationSessionId implements Parcelable {
    private final String mValue;

    TextClassificationSessionId(String value) {
        mValue = Objects.requireNonNull(value);
    }

    private TextClassificationSessionId(Parcel in) {
        mValue = in.readString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextClassificationSessionId)) return false;
        TextClassificationSessionId that = (TextClassificationSessionId) o;
        return Objects.equals(mValue, that.mValue);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mValue);
    }

    @Override
    public String toString() {
        return "TextClassificationSessionId{" + mValue + "}";
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getValue() {
        return mValue;
    }

    public static final Parcelable.Creator<TextClassificationSessionId> CREATOR =
            new Parcelable.Creator<TextClassificationSessionId>() {
                @Override
                public TextClassificationSessionId createFromParcel(Parcel parcel) {
                    return new TextClassificationSessionId(parcel);
                }

                @Override
                public TextClassificationSessionId[] newArray(int size) {
                    return new TextClassificationSessionId[size];
                }
            };
}
