// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TextClassification implements Parcelable {
    static final TextClassification EMPTY = new TextClassification.Builder().build();

    private final String mText;
    private final Drawable mLegacyIcon;
    private final String mLegacyLabel;
    private final Intent mLegacyIntent;
    private final View.OnClickListener mLegacyOnClickListener;
    private final List<RemoteAction> mActions;
    private final List<String> mEntities;
    private final Map<String, Float> mEntityConfidence;
    private final String mId;
    private final Bundle mExtras;

    private TextClassification(
            String text,
            Drawable legacyIcon,
            String legacyLabel,
            Intent legacyIntent,
            View.OnClickListener legacyOnClickListener,
            List<RemoteAction> actions,
            Map<String, Float> entityConfidence,
            String id,
            Bundle extras) {
        mText = text;
        mLegacyIcon = legacyIcon;
        mLegacyLabel = legacyLabel;
        mLegacyIntent = legacyIntent;
        mLegacyOnClickListener = legacyOnClickListener;
        mActions = Collections.unmodifiableList(new ArrayList<RemoteAction>(actions));
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
        mExtras = extras;
    }

    private TextClassification(Parcel in) {
        mText = in.readString();
        List<RemoteAction> acts = in.createTypedArrayList(RemoteAction.CREATOR);
        mActions = acts != null
                ? Collections.unmodifiableList(acts)
                : Collections.<RemoteAction>emptyList();
        mLegacyIcon = null;
        mLegacyLabel = in.readString();
        mLegacyIntent = in.readTypedObject(Intent.CREATOR);
        mLegacyOnClickListener = null;
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
    }

    public String getText() {
        return mText;
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

    public List<RemoteAction> getActions() {
        return mActions;
    }

    @Deprecated
    public Drawable getIcon() {
        return mLegacyIcon;
    }

    @Deprecated
    public CharSequence getLabel() {
        return mLegacyLabel;
    }

    @Deprecated
    public Intent getIntent() {
        return mLegacyIntent;
    }

    @Deprecated
    public View.OnClickListener getOnClickListener() {
        return mLegacyOnClickListener;
    }

    public String getId() {
        return mId;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public String toString() {
        return String.format(
                "TextClassification {text=%s, entities=%s, actions=%s, id=%s}",
                mText, mEntityConfidence, mActions, mId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mText);
        dest.writeTypedList(mActions);
        dest.writeString(mLegacyLabel);
        dest.writeTypedObject(mLegacyIntent, flags);
        dest.writeInt(mEntities.size());
        for (int i = 0; i < mEntities.size(); i++) {
            String k = mEntities.get(i);
            dest.writeString(k);
            dest.writeFloat(mEntityConfidence.get(k).floatValue());
        }
        dest.writeString(mId);
        dest.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<TextClassification> CREATOR =
            new Parcelable.Creator<TextClassification>() {
                @Override
                public TextClassification createFromParcel(Parcel in) {
                    return new TextClassification(in);
                }

                @Override
                public TextClassification[] newArray(int size) {
                    return new TextClassification[size];
                }
            };

    public static final class Builder {
        private String mText;
        private final List<RemoteAction> mActions = new ArrayList<RemoteAction>();
        private final Map<String, Float> mTypeScoreMap = new LinkedHashMap<String, Float>();
        private Drawable mLegacyIcon;
        private String mLegacyLabel;
        private Intent mLegacyIntent;
        private View.OnClickListener mLegacyOnClickListener;
        private String mId;
        private Bundle mExtras;

        public Builder() {
        }

        public Builder setText(String text) {
            mText = text;
            return this;
        }

        public Builder setEntityType(String type, float confidenceScore) {
            Objects.requireNonNull(type);
            float clamped = Math.max(0f, Math.min(1f, confidenceScore));
            mTypeScoreMap.put(type, Float.valueOf(clamped));
            return this;
        }

        public Builder addAction(RemoteAction action) {
            Objects.requireNonNull(action);
            mActions.add(action);
            return this;
        }

        @Deprecated
        public Builder setIcon(Drawable icon) {
            mLegacyIcon = icon;
            return this;
        }

        @Deprecated
        public Builder setLabel(String label) {
            mLegacyLabel = label;
            return this;
        }

        @Deprecated
        public Builder setIntent(Intent intent) {
            mLegacyIntent = intent;
            return this;
        }

        @Deprecated
        public Builder setOnClickListener(View.OnClickListener onClickListener) {
            mLegacyOnClickListener = onClickListener;
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public TextClassification build() {
            return new TextClassification(
                    mText,
                    mLegacyIcon,
                    mLegacyLabel,
                    mLegacyIntent,
                    mLegacyOnClickListener,
                    mActions,
                    mTypeScoreMap,
                    mId,
                    mExtras == null ? Bundle.EMPTY : mExtras);
        }
    }

    public static final class Request implements Parcelable {
        private final CharSequence mText;
        private final int mStartIndex;
        private final int mEndIndex;
        private final LocaleList mDefaultLocales;
        private final ZonedDateTime mReferenceTime;
        private final String mCallingPackageName;
        private final Bundle mExtras;

        private Request(
                CharSequence text,
                int startIndex,
                int endIndex,
                LocaleList defaultLocales,
                ZonedDateTime referenceTime,
                String callingPackageName,
                Bundle extras) {
            mText = text;
            mStartIndex = startIndex;
            mEndIndex = endIndex;
            mDefaultLocales = defaultLocales;
            mReferenceTime = referenceTime;
            mCallingPackageName = callingPackageName;
            mExtras = extras;
        }

        private Request(Parcel in) {
            mText = in.readString();
            mStartIndex = in.readInt();
            mEndIndex = in.readInt();
            mDefaultLocales = in.readTypedObject(LocaleList.CREATOR);
            String refStr = in.readString();
            mReferenceTime = refStr != null
                    ? ZonedDateTime.parse(refStr, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    : null;
            mCallingPackageName = in.readString();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
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

        public ZonedDateTime getReferenceTime() {
            return mReferenceTime;
        }

        public String getCallingPackageName() {
            return mCallingPackageName;
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
            dest.writeString(mReferenceTime != null
                    ? mReferenceTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : null);
            dest.writeString(mCallingPackageName);
            dest.writeBundle(mExtras);
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
            private ZonedDateTime mReferenceTime;
            private Bundle mExtras;

            public Builder(CharSequence text, int startIndex, int endIndex) {
                Objects.requireNonNull(text);
                if (startIndex < 0 || endIndex > text.length() || startIndex >= endIndex) {
                    throw new IllegalArgumentException("Invalid range: " + startIndex + ".." + endIndex);
                }
                mText = text;
                mStartIndex = startIndex;
                mEndIndex = endIndex;
            }

            public Builder setDefaultLocales(LocaleList defaultLocales) {
                mDefaultLocales = defaultLocales;
                return this;
            }

            public Builder setReferenceTime(ZonedDateTime referenceTime) {
                mReferenceTime = referenceTime;
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
                        mReferenceTime,
                        null,
                        mExtras == null ? Bundle.EMPTY : mExtras);
            }
        }
    }
}
