// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import java.util.Arrays;
import java.util.Locale;

/** Span holding spell-checker or IME word replacement candidates and error underline styling. */
public class SuggestionSpan extends CharacterStyle implements ParcelableSpan {
    public static final int FLAG_EASY_CORRECT = 0x0001;
    public static final int FLAG_MISSPELLED = 0x0002;
    public static final int FLAG_AUTO_CORRECTION = 0x0004;
    public static final int FLAG_GRAMMAR_ERROR = 0x0008;

    public static final int SUGGESTIONS_MAX_SIZE = 5;

    @Deprecated
    public static final String ACTION_SUGGESTION_PICKED = "android.text.style.SUGGESTION_PICKED";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_AFTER = "after";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_BEFORE = "before";
    @Deprecated
    public static final String SUGGESTION_SPAN_PICKED_HASHCODE = "hashcode";

    public static final Parcelable.Creator<SuggestionSpan> CREATOR =
            new Parcelable.Creator<SuggestionSpan>() {
                @Override
                public SuggestionSpan createFromParcel(Parcel source) {
                    return new SuggestionSpan(source);
                }

                @Override
                public SuggestionSpan[] newArray(int size) {
                    return new SuggestionSpan[size];
                }
            };

    private int mFlags;
    private final String[] mSuggestions;
    private final String mLocaleStringForCompatibility;
    private final String mLanguageTag;
    private final int mHashCode;
    private int mEasyCorrectUnderlineColor = Color.BLACK;
    private int mMisspelledUnderlineColor = Color.RED;
    private int mAutoCorrectionUnderlineColor = Color.GRAY;
    private int mGrammarErrorUnderlineColor = Color.BLUE;

    public SuggestionSpan(Context context, String[] suggestions, int flags) {
        this(context, null, suggestions, flags, null);
    }

    public SuggestionSpan(Locale locale, String[] suggestions, int flags) {
        this(null, locale, suggestions, flags, null);
    }

    public SuggestionSpan(
            Context context,
            Locale locale,
            String[] suggestions,
            int flags,
            Class<?> notificationTargetClass) {
        int n = Math.min(SUGGESTIONS_MAX_SIZE, suggestions != null ? suggestions.length : 0);
        mSuggestions = new String[n];
        if (suggestions != null) {
            System.arraycopy(suggestions, 0, mSuggestions, 0, n);
        }
        mFlags = flags;
        Locale sourceLocale;
        if (locale != null) {
            sourceLocale = locale;
        } else if (context != null && context.getResources() != null
                && context.getResources().getConfiguration() != null
                && context.getResources().getConfiguration().locale != null) {
            sourceLocale = context.getResources().getConfiguration().locale;
        } else {
            sourceLocale = Locale.getDefault();
        }
        mLocaleStringForCompatibility = sourceLocale == null ? "" : sourceLocale.toString();
        mLanguageTag = sourceLocale == null ? "" : sourceLocale.toLanguageTag();
        mHashCode = hashCodeInternal(mSuggestions, mLanguageTag, mLocaleStringForCompatibility);
    }

    public SuggestionSpan(Parcel src) {
        mSuggestions = src.createStringArray();
        mFlags = src.readInt();
        mLocaleStringForCompatibility = src.readString();
        mLanguageTag = src.readString();
        mHashCode = src.readInt();
        mEasyCorrectUnderlineColor = src.readInt();
        mMisspelledUnderlineColor = src.readInt();
        mAutoCorrectionUnderlineColor = src.readInt();
        mGrammarErrorUnderlineColor = src.readInt();
    }

    private static int hashCodeInternal(
            String[] suggestions, String languageTag, String localeStringForCompatibility) {
        return Arrays.hashCode(new Object[] {
            Long.valueOf(SystemClock.uptimeMillis()),
            suggestions,
            languageTag,
            localeStringForCompatibility
        });
    }

    public String[] getSuggestions() {
        return mSuggestions;
    }

    @Deprecated
    public String getLocale() {
        return mLocaleStringForCompatibility;
    }

    public Locale getLocaleObject() {
        if (mLanguageTag != null && !mLanguageTag.isEmpty()) {
            return Locale.forLanguageTag(mLanguageTag);
        }
        return null;
    }

    public int getFlags() {
        return mFlags;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mSuggestions);
        dest.writeInt(mFlags);
        dest.writeString(mLocaleStringForCompatibility);
        dest.writeString(mLanguageTag);
        dest.writeInt(mHashCode);
        dest.writeInt(mEasyCorrectUnderlineColor);
        dest.writeInt(mMisspelledUnderlineColor);
        dest.writeInt(mAutoCorrectionUnderlineColor);
        dest.writeInt(mGrammarErrorUnderlineColor);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        writeToParcel(dest, flags);
    }

    @Override
    public int getSpanTypeId() {
        return 19;
    }

    @Override
    public int getSpanTypeIdInternal() {
        return getSpanTypeId();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SuggestionSpan) {
            return ((SuggestionSpan) o).hashCode() == mHashCode;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        int color = getUnderlineColor();
        if (color != 0) {
            tp.setUnderlineText(true);
        }
    }

    public int getUnderlineColor() {
        boolean misspelled = (mFlags & FLAG_MISSPELLED) != 0;
        boolean easy = (mFlags & FLAG_EASY_CORRECT) != 0;
        boolean autoCorrection = (mFlags & FLAG_AUTO_CORRECTION) != 0;
        boolean grammarError = (mFlags & FLAG_GRAMMAR_ERROR) != 0;
        if (easy) {
            if (!misspelled && !grammarError) {
                return mEasyCorrectUnderlineColor;
            } else if (misspelled) {
                return mMisspelledUnderlineColor;
            } else {
                return mGrammarErrorUnderlineColor;
            }
        } else if (autoCorrection) {
            return mAutoCorrectionUnderlineColor;
        } else if (misspelled) {
            return mMisspelledUnderlineColor;
        } else if (grammarError) {
            return mGrammarErrorUnderlineColor;
        }
        return 0;
    }
}
