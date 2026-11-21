// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TextSelection implements Parcelable {
    private final int mStartIndex;
    private final int mEndIndex;
    private final Map<String, Float> mEntityConfidence;
    private final List<String> mEntities;
    private final String mId;
    private final TextClassification mTextClassification;
    private final Bundle mExtras;

    private TextSelection(
            int startIndex,
            int endIndex,
            Map<String, Float> entityConfidence,
            String id,
            TextClassification textClassification,
            Bundle extras) {
        mStartIndex = startIndex;
        mEndIndex = endIndex;
        mEntityConfidence = new LinkedHashMap<String, Float>(entityConfidence);
        List<String> sorted = new ArrayList<String>(mEntityConfidence.keySet());
        Collections.sort(sorted, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Float.compare(
                        mEntityConfidence.get(b).floatValue(),
                        mEntityConfidence.get(a).floatValue());
            }
        });
        mEntities = Collections.unmodifiableList(sorted);
        mId = id;
        mTextClassification = textClassification;
        mExtras = extras;
    }

    private TextSelection(Parcel in) {
        mStartIndex = in.readInt();
        mEndIndex = in.readInt();
        int count = in.readInt();
        mEntityConfidence = new LinkedHashMap<String, Float>(count);
        List<String> ents = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String k = in.readString();
            float v = in.readFloat();
            mEntityConfidence.put(k, Float.valueOf(v));
            ents.add(k);
        }
        mEntities = Collections.unmodifiableList(ents);
        mId = in.readString();
        Bundle b = in.readBundle();
        mExtras = b != null ? b : Bundle.EMPTY;
        mTextClassification = in.readTypedObject(TextClassification.CREATOR);
    }

    public int getSelectionStartIndex() {
        return mStartIndex;
    }

    public int getSelectionEndIndex() {
        return mEndIndex;
    }

    public int getEntityCount() {
        return mEntities.size();
    }

    public String getEntity(int index) {
        return mEntities.get(index);
    }

    public float getConfidenceScore(String entity) {
        Float v = mEntityConfidence.get(entity);
        return v != null ? v.floatValue() : 0f;
    }

    public String getId() {
        return mId;
    }

    public TextClassification getTextClassification() {
        return mTextClassification;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public String toString() {
        return String.format(
                "TextSelection {id=%s, startIndex=%d, endIndex=%d, entities=%s}",
                mId, Integer.valueOf(mStartIndex), Integer.valueOf(mEndIndex), mEntityConfidence);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStartIndex);
        dest.writeInt(mEndIndex);
        dest.writeInt(mEntities.size());
        for (int i = 0; i < mEntities.size(); i++) {
            String k = mEntities.get(i);
            dest.writeString(k);
            dest.writeFloat(mEntityConfidence.get(k).floatValue());
        }
        dest.writeString(mId);
        dest.writeBundle(mExtras);
        dest.writeTypedObject(mTextClassification, flags);
    }

    public static final Parcelable.Creator<TextSelection> CREATOR =
            new Parcelable.Creator<TextSelection>() {
                @Override
                public TextSelection createFromParcel(Parcel in) {
                    return new TextSelection(in);
                }

                @Override
                public TextSelection[] newArray(int size) {
                    return new TextSelection[size];
                }
            };

    public static final class Builder {
        private final int mStartIndex;
        private final int mEndIndex;
        private final Map<String, Float> mEntityConfidence = new LinkedHashMap<String, Float>();
        private String mId;
        private TextClassification mTextClassification;
        private Bundle mExtras;

        public Builder(int startIndex, int endIndex) {
            if (startIndex < 0 || endIndex <= startIndex) {
                throw new IllegalArgumentException("Invalid selection indices");
            }
            mStartIndex = startIndex;
            mEndIndex = endIndex;
        }

        public Builder setEntityType(String type, float confidenceScore) {
            Objects.requireNonNull(type);
            float clamped = Math.max(0f, Math.min(1f, confidenceScore));
            mEntityConfidence.put(type, Float.valueOf(clamped));
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setTextClassification(TextClassification textClassification) {
            mTextClassification = textClassification;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public TextSelection build() {
            return new TextSelection(
                    mStartIndex,
                    mEndIndex,
                    mEntityConfidence,
                    mId,
                    mTextClassification,
                    mExtras == null ? Bundle.EMPTY : mExtras);
        }
    }

    public static final class Request implements Parcelable {
        private final CharSequence mText;
        private final int mStartIndex;
        private final int mEndIndex;
        private final LocaleList mDefaultLocales;
        private final String mCallingPackageName;
        private final boolean mIncludeTextClassification;
        private final Bundle mExtras;

        private Request(
                CharSequence text,
                int startIndex,
                int endIndex,
                LocaleList defaultLocales,
                String callingPackageName,
                boolean includeTextClassification,
                Bundle extras) {
            mText = text;
            mStartIndex = startIndex;
            mEndIndex = endIndex;
            mDefaultLocales = defaultLocales;
            mCallingPackageName = callingPackageName;
            mIncludeTextClassification = includeTextClassification;
            mExtras = extras;
        }

        private Request(Parcel in) {
            mText = in.readString();
            mStartIndex = in.readInt();
            mEndIndex = in.readInt();
            mDefaultLocales = in.readTypedObject(LocaleList.CREATOR);
            mCallingPackageName = in.readString();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
            mIncludeTextClassification = in.readBoolean();
        }

        public CharSequence getText() {
            return mText;
        }

        public int getStartIndex() {
            return mStartIndex;
        }

        public int getEndIndex() {
            return mEndIndex;
        }

        public LocaleList getDefaultLocales() {
            return mDefaultLocales;
        }

        public String getCallingPackageName() {
            return mCallingPackageName;
        }

        public boolean shouldIncludeTextClassification() {
            return mIncludeTextClassification;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mText != null ? mText.toString() : null);
            dest.writeInt(mStartIndex);
            dest.writeInt(mEndIndex);
            dest.writeTypedObject(mDefaultLocales, flags);
            dest.writeString(mCallingPackageName);
            dest.writeBundle(mExtras);
            dest.writeBoolean(mIncludeTextClassification);
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
            private final int mStartIndex;
            private final int mEndIndex;
            private LocaleList mDefaultLocales;
            private boolean mIncludeTextClassification;
            private Bundle mExtras;

            public Builder(CharSequence text, int startIndex, int endIndex) {
                Objects.requireNonNull(text);
                if (startIndex < 0 || endIndex > text.length() || startIndex >= endIndex) {
                    throw new IllegalArgumentException("Invalid range");
                }
                mText = text;
                mStartIndex = startIndex;
                mEndIndex = endIndex;
            }

            public Builder setDefaultLocales(LocaleList defaultLocales) {
                mDefaultLocales = defaultLocales;
                return this;
            }

            public Builder setIncludeTextClassification(boolean includeTextClassification) {
                mIncludeTextClassification = includeTextClassification;
                return this;
            }

            public Builder setExtras(Bundle extras) {
                mExtras = extras;
                return this;
            }

            public Request build() {
                return new Request(
                        mText,
                        mStartIndex,
                        mEndIndex,
                        mDefaultLocales,
                        null,
                        mIncludeTextClassification,
                        mExtras == null ? Bundle.EMPTY : mExtras);
            }
        }
    }
}
