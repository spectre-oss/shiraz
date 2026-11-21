// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.ParcelableSpan;
import android.text.TextPaint;

/** CharacterStyle highlighting an active spellcheck/IME suggestion target range. */
public final class SuggestionRangeSpan extends CharacterStyle implements ParcelableSpan {
    public static final Parcelable.Creator<SuggestionRangeSpan> CREATOR =
            new Parcelable.Creator<SuggestionRangeSpan>() {
                @Override
                public SuggestionRangeSpan createFromParcel(Parcel source) {
                    return new SuggestionRangeSpan(source);
                }

                @Override
                public SuggestionRangeSpan[] newArray(int size) {
                    return new SuggestionRangeSpan[size];
                }
            };

    private int mBackgroundColor;

    public SuggestionRangeSpan() {
        mBackgroundColor = 0;
    }

    SuggestionRangeSpan(Parcel src) {
        mBackgroundColor = src.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mBackgroundColor);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    @Override
    public int getSpanTypeId() {
        return 21;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }

    public void setBackgroundColor(int backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.bgColor = mBackgroundColor;
    }
}
