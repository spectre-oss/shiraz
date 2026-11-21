// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TextLanguage implements Parcelable {
    static final TextLanguage EMPTY = new TextLanguage.Builder().build();

    private final String mId;
    private final List<ULocale> mLocales;
    private final Map<ULocale, Float> mScores;
    private final Bundle mBundle;

    private TextLanguage(
            String id, Map<ULocale, Float> scores, Bundle bundle) {
        mId = id;
        mScores = new LinkedHashMap<ULocale, Float>(scores);
        List<ULocale> list = new ArrayList<ULocale>(mScores.keySet());
        Collections.sort(list, new java.util.Comparator<ULocale>() {
            @Override
            public int compare(ULocale a, ULocale b) {
                return Float.compare(
                        mScores.get(b).floatValue(),
                        mScores.get(a).floatValue());
            }
        });
        mLocales = Collections.unmodifiableList(list);
        mBundle = bundle;
    }

    private TextLanguage(Parcel in) {
        mId = in.readString();
        int count = in.readInt();
        mScores = new LinkedHashMap<ULocale, Float>(count);
        List<ULocale> list = new ArrayList<ULocale>(count);
        for (int i = 0; i < count; i++) {
            ULocale loc = new ULocale(in.readString());
            float score = in.readFloat();
            mScores.put(loc, Float.valueOf(score));
            list.add(loc);
        }
        mLocales = Collections.unmodifiableList(list);
        Bundle b = in.readBundle();
        mBundle = b != null ? b : Bundle.EMPTY;
    }

    public String getId() {
        return mId;
    }

    public int getLocaleHypothesisCount() {
        return mLocales.size();
    }

    public ULocale getLocale(int index) {
        return mLocales.get(index);
    }

    public float getConfidenceScore(ULocale locale) {
        Float v = mScores.get(locale);
        return v != null ? v.floatValue() : 0f;
    }

    public Bundle getExtras() {
        return mBundle;
    }

    @Override
    public String toString() {
        return "TextLanguage {id=" + mId + ", locales=" + mScores + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeInt(mLocales.size());
        for (int i = 0; i < mLocales.size(); i++) {
            ULocale loc = mLocales.get(i);
            dest.writeString(loc.getName());
            dest.writeFloat(mScores.get(loc).floatValue());
        }
        dest.writeBundle(mBundle);
    }

    public static final Parcelable.Creator<TextLanguage> CREATOR =
            new Parcelable.Creator<TextLanguage>() {
                @Override
                public TextLanguage createFromParcel(Parcel in) {
                    return new TextLanguage(in);
                }

                @Override
                public TextLanguage[] newArray(int size) {
                    return new TextLanguage[size];
                }
            };

    public static final class Builder {
        private String mId;
        private final Map<ULocale, Float> mEntityConfidenceMap =
                new LinkedHashMap<ULocale, Float>();
        private Bundle mBundle;

        public Builder() {
        }

        public Builder putLocale(ULocale locale, float confidenceScore) {
            Objects.requireNonNull(locale);
            float clamped = Math.max(0f, Math.min(1f, confidenceScore));
            mEntityConfidenceMap.put(locale, Float.valueOf(clamped));
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setExtras(Bundle bundle) {
            mBundle = Objects.requireNonNull(bundle);
            return this;
        }

        public TextLanguage build() {
            return new TextLanguage(
                    mId,
                    mEntityConfidenceMap,
                    mBundle == null ? Bundle.EMPTY : mBundle);
        }
    }

    public static final class Request implements Parcelable {
        private final CharSequence mText;
        private final String mCallingPackageName;
        private final Bundle mExtra;

        private Request(CharSequence text, String callingPackageName, Bundle extra) {
            mText = text;
            mCallingPackageName = callingPackageName;
            mExtra = extra;
        }

        private Request(Parcel in) {
            mText = in.readString();
            mCallingPackageName = in.readString();
            Bundle b = in.readBundle();
            mExtra = b != null ? b : Bundle.EMPTY;
        }

        public CharSequence getText() {
            return mText;
        }

        public String getCallingPackageName() {
            return mCallingPackageName;
        }

        public Bundle getExtras() {
            return mExtra;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mText != null ? mText.toString() : null);
            dest.writeString(mCallingPackageName);
            dest.writeBundle(mExtra);
        }

        public static final Parcelable.Creator<Request> CREATOR =
                new Parcelable.Creator<Request>() {
                    @Override
                    public Request createFromParcel(Parcel in) {
                        return new Request(in);
                    }

                    @Override
                    public Request[] newArray(int size) {
                        return new Request[size];
                    }
                };

        public static final class Builder {
            private final CharSequence mText;
            private Bundle mBundle;

            public Builder(CharSequence text) {
                mText = Objects.requireNonNull(text);
            }

            public Builder setExtras(Bundle bundle) {
                mBundle = Objects.requireNonNull(bundle);
                return this;
            }

            public Request build() {
                return new Request(
                        mText.toString(),
                        null,
                        mBundle == null ? Bundle.EMPTY : mBundle);
            }
        }
    }
}
