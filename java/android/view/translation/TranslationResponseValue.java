// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TranslationResponseValue implements Parcelable {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_ERROR = 1;
    public static final String EXTRA_DEFINITIONS = "android.view.translation.extra.DEFINITIONS";

    private final int mStatusCode;
    private final CharSequence mText;
    private final Bundle mExtras;
    private final CharSequence mTransliteration;

    TranslationResponseValue(
            int statusCode,
            CharSequence text,
            Bundle extras,
            CharSequence transliteration) {
        mStatusCode = statusCode;
        mText = text;
        mExtras = extras != null ? extras : Bundle.EMPTY;
        mTransliteration = transliteration;
    }

    private TranslationResponseValue(Parcel in) {
        mStatusCode = in.readInt();
        mText = in.readString();
        Bundle b = in.readBundle();
        mExtras = b != null ? b : Bundle.EMPTY;
        mTransliteration = in.readString();
    }

    public static TranslationResponseValue forError() {
        return new TranslationResponseValue(STATUS_ERROR, null, Bundle.EMPTY, null);
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    public CharSequence getText() {
        return mText;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public CharSequence getTransliteration() {
        return mTransliteration;
    }

    @Override
    public String toString() {
        return "TranslationResponseValue { statusCode = " + mStatusCode
                + ", text = " + mText
                + ", extras = " + mExtras
                + ", transliteration = " + mTransliteration + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranslationResponseValue)) return false;
        TranslationResponseValue that = (TranslationResponseValue) o;
        return mStatusCode == that.mStatusCode
                && Objects.equals(mText, that.mText)
                && Objects.equals(mTransliteration, that.mTransliteration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mStatusCode), mText, mTransliteration);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStatusCode);
        dest.writeString(mText != null ? mText.toString() : null);
        dest.writeBundle(mExtras);
        dest.writeString(mTransliteration != null ? mTransliteration.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationResponseValue> CREATOR =
            new Parcelable.Creator<TranslationResponseValue>() {
                @Override
                public TranslationResponseValue createFromParcel(Parcel in) {
                    return new TranslationResponseValue(in);
                }

                @Override
                public TranslationResponseValue[] newArray(int size) {
                    return new TranslationResponseValue[size];
                }
            };

    public static final class Builder {
        private final int mStatusCode;
        private CharSequence mText;
        private Bundle mExtras = Bundle.EMPTY;
        private CharSequence mTransliteration;

        public Builder(int statusCode) {
            if (statusCode != STATUS_SUCCESS && statusCode != STATUS_ERROR) {
                throw new IllegalArgumentException("statusCode wasn't valid: " + statusCode);
            }
            mStatusCode = statusCode;
        }

        public Builder setText(CharSequence value) {
            mText = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder setExtras(Bundle value) {
            mExtras = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder setTransliteration(CharSequence value) {
            mTransliteration = Objects.requireNonNull(value, "value");
            return this;
        }

        public TranslationResponseValue build() {
            return new TranslationResponseValue(
                    mStatusCode, mText, mExtras, mTransliteration);
        }
    }
}
