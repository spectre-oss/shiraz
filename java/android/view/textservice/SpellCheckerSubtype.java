// SPDX-License-Identifier: GPL-3.0-only

package android.view.textservice;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.Objects;

public final class SpellCheckerSubtype implements Parcelable {
    private final int mSubtypeNameResId;
    private final String mSubtypeLocale;
    private final String mSubtypeLanguageTag;
    private final String mSubtypeExtraValue;
    private HashMap<String, String> mExtraValueHashMapCache;

    public SpellCheckerSubtype(int nameId, String locale, String extraValue) {
        this(nameId, locale, "", extraValue);
    }

    SpellCheckerSubtype(
            int nameId, String locale, String languageTag, String extraValue) {
        mSubtypeNameResId = nameId;
        mSubtypeLocale = locale != null ? locale : "";
        mSubtypeLanguageTag = languageTag != null ? languageTag : "";
        mSubtypeExtraValue = extraValue != null ? extraValue : "";
    }

    private SpellCheckerSubtype(Parcel source) {
        mSubtypeNameResId = source.readInt();
        String s = source.readString();
        mSubtypeLocale = s != null ? s : "";
        s = source.readString();
        mSubtypeLanguageTag = s != null ? s : "";
        s = source.readString();
        mSubtypeExtraValue = s != null ? s : "";
    }

    public int getNameResId() {
        return mSubtypeNameResId;
    }

    @Deprecated
    public String getLocale() {
        return mSubtypeLocale;
    }

    public String getLanguageTag() {
        return mSubtypeLanguageTag;
    }

    public String getExtraValue() {
        return mSubtypeExtraValue;
    }

    private HashMap<String, String> getExtraValueHashMap() {
        if (mExtraValueHashMapCache == null) {
            mExtraValueHashMapCache = new HashMap<String, String>();
            String[] pairs = mSubtypeExtraValue.split(",");
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                if (eq < 0) {
                    mExtraValueHashMapCache.put(pair, null);
                } else if (eq > 0) {
                    mExtraValueHashMapCache.put(
                            pair.substring(0, eq),
                            eq + 1 < pair.length() ? pair.substring(eq + 1) : "");
                }
            }
        }
        return mExtraValueHashMapCache;
    }

    public boolean containsExtraValueKey(String key) {
        return getExtraValueHashMap().containsKey(key);
    }

    public String getExtraValueOf(String key) {
        return getExtraValueHashMap().get(key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mSubtypeNameResId),
                mSubtypeLocale,
                mSubtypeLanguageTag,
                mSubtypeExtraValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpellCheckerSubtype)) return false;
        SpellCheckerSubtype other = (SpellCheckerSubtype) o;
        if (!other.mSubtypeLanguageTag.isEmpty() || !mSubtypeLanguageTag.isEmpty()) {
            return other.mSubtypeNameResId == mSubtypeNameResId
                    && other.mSubtypeLanguageTag.equals(mSubtypeLanguageTag)
                    && other.mSubtypeExtraValue.equals(mSubtypeExtraValue);
        }
        return other.mSubtypeNameResId == mSubtypeNameResId
                && other.mSubtypeLocale.equals(mSubtypeLocale)
                && other.mSubtypeExtraValue.equals(mSubtypeExtraValue);
    }

    public CharSequence getDisplayName(
            Context context, String packageName, ApplicationInfo appInfo) {
        String fallback = !mSubtypeLanguageTag.isEmpty() ? mSubtypeLanguageTag : mSubtypeLocale;
        if (mSubtypeNameResId == 0 || context == null) {
            return fallback;
        }
        CharSequence t = context.getPackageManager().getText(packageName, mSubtypeNameResId, appInfo);
        return t != null ? t : fallback;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeInt(mSubtypeNameResId);
        dest.writeString(mSubtypeLocale);
        dest.writeString(mSubtypeLanguageTag);
        dest.writeString(mSubtypeExtraValue);
    }

    public static final Parcelable.Creator<SpellCheckerSubtype> CREATOR =
            new Parcelable.Creator<SpellCheckerSubtype>() {
                @Override
                public SpellCheckerSubtype createFromParcel(Parcel source) {
                    return new SpellCheckerSubtype(source);
                }

                @Override
                public SpellCheckerSubtype[] newArray(int size) {
                    return new SpellCheckerSubtype[size];
                }
            };
}
