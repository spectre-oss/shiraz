// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.icu.util.ULocale;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TranslationSpec implements Parcelable {
    public static final int DATA_FORMAT_TEXT = 1;

    private final ULocale mLocale;
    private final int mDataFormat;

    public TranslationSpec(ULocale locale, int dataFormat) {
        mLocale = Objects.requireNonNull(locale, "locale");
        mDataFormat = dataFormat;
    }

    private TranslationSpec(Parcel in) {
        String tag = in.readString();
        mLocale = tag != null ? new ULocale(tag) : ULocale.ROOT;
        mDataFormat = in.readInt();
    }

    public ULocale getLocale() {
        return mLocale;
    }

    public int getDataFormat() {
        return mDataFormat;
    }

    @Override
    public String toString() {
        return "TranslationSpec { locale = " + mLocale + ", dataFormat = " + mDataFormat + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranslationSpec)) return false;
        TranslationSpec that = (TranslationSpec) o;
        return mDataFormat == that.mDataFormat && Objects.equals(mLocale, that.mLocale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLocale, Integer.valueOf(mDataFormat));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mLocale != null ? mLocale.getName() : null);
        dest.writeInt(mDataFormat);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationSpec> CREATOR =
            new Parcelable.Creator<TranslationSpec>() {
                @Override
                public TranslationSpec createFromParcel(Parcel in) {
                    return new TranslationSpec(in);
                }

                @Override
                public TranslationSpec[] newArray(int size) {
                    return new TranslationSpec[size];
                }
            };
}
