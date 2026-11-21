// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class TextLinks implements Parcelable {
    public static final int STATUS_LINKS_APPLIED = 0;
    public static final int STATUS_NO_LINKS_FOUND = 1;
    public static final int STATUS_NO_LINKS_APPLIED = 2;
    public static final int STATUS_DIFFERENT_TEXT = 3;
    public static final int STATUS_UNSUPPORTED_CHARACTER = 4;

    public static final int APPLY_STRATEGY_IGNORE = 0;
    public static final int APPLY_STRATEGY_REPLACE = 1;

    private final String mFullText;
    private final List<TextLink> mLinks;
    private final Bundle mExtras;

    private TextLinks(String fullText, List<TextLink> links, Bundle extras) {
        mFullText = fullText;
        mLinks = Collections.unmodifiableList(new ArrayList<TextLink>(links));
        mExtras = extras;
    }

    private TextLinks(Parcel in) {
        mFullText = in.readString();
        List<TextLink> list = in.createTypedArrayList(TextLink.CREATOR);
        mLinks = list != null
                ? Collections.unmodifiableList(list)
                : Collections.<TextLink>emptyList();
        Bundle b = in.readBundle();
        mExtras = b != null ? b : Bundle.EMPTY;
    }

    public CharSequence getText() {
        return mFullText;
    }

    public Collection<TextLink> getLinks() {
        return mLinks;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public int apply(
            Spannable text,
            int applyStrategy,
            Function<TextLink, TextLinkSpan> spanFactory) {
        Objects.requireNonNull(text, "text");
        if (!text.toString().startsWith(mFullText)) {
            return STATUS_DIFFERENT_TEXT;
        }
        if (mLinks.isEmpty()) {
            return STATUS_NO_LINKS_FOUND;
        }
        int applyCount = 0;
        for (int i = 0; i < mLinks.size(); i++) {
            TextLink link = mLinks.get(i);
            TextLinkSpan span = spanFactory != null
                    ? spanFactory.apply(link)
                    : new TextLinkSpan(link);
            if (span == null) {
                continue;
            }
            ClickableSpan[] existing = text.getSpans(
                    link.getStart(), link.getEnd(), ClickableSpan.class);
            if (existing != null && existing.length > 0) {
                if (applyStrategy == APPLY_STRATEGY_REPLACE) {
                    for (int j = 0; j < existing.length; j++) {
                        text.removeSpan(existing[j]);
                    }
                    text.setSpan(
                            span,
                            link.getStart(),
                            link.getEnd(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    applyCount++;
                }
            } else {
                text.setSpan(
                        span,
                        link.getStart(),
                        link.getEnd(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                applyCount++;
            }
        }
        return applyCount == 0 ? STATUS_NO_LINKS_APPLIED : STATUS_LINKS_APPLIED;
    }

    @Override
    public String toString() {
        return String.format("TextLinks{fullText=%s, links=%s}", mFullText, mLinks);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFullText);
        dest.writeTypedList(mLinks);
        dest.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<TextLinks> CREATOR =
            new Parcelable.Creator<TextLinks>() {
                @Override
                public TextLinks createFromParcel(Parcel in) {
                    return new TextLinks(in);
                }

                @Override
                public TextLinks[] newArray(int size) {
                    return new TextLinks[size];
                }
            };

    public static final class TextLink implements Parcelable {
        private final int mStart;
        private final int mEnd;
        private final Map<String, Float> mEntityScores;
        private final List<String> mEntities;
        private final Bundle mExtras;

        TextLink(
                int start,
                int end,
                Map<String, Float> entityScores,
                Bundle extras) {
            Objects.requireNonNull(entityScores);
            if (entityScores.isEmpty()) {
                throw new IllegalArgumentException("entityScores must not be empty");
            }
            if (start > end) {
                throw new IllegalArgumentException("start > end");
            }
            mStart = start;
            mEnd = end;
            mEntityScores = new LinkedHashMap<String, Float>(entityScores);
            List<String> sorted = new ArrayList<String>(mEntityScores.keySet());
            Collections.sort(sorted, new java.util.Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return Float.compare(
                            mEntityScores.get(b).floatValue(),
                            mEntityScores.get(a).floatValue());
                }
            });
            mEntities = Collections.unmodifiableList(sorted);
            mExtras = extras != null ? extras : Bundle.EMPTY;
        }

        private TextLink(Parcel in) {
            int count = in.readInt();
            mEntityScores = new LinkedHashMap<String, Float>(count);
            List<String> ents = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                String k = in.readString();
                float v = in.readFloat();
                mEntityScores.put(k, Float.valueOf(v));
                ents.add(k);
            }
            mEntities = Collections.unmodifiableList(ents);
            mStart = in.readInt();
            mEnd = in.readInt();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
        }

        public int getStart() {
            return mStart;
        }

        public int getEnd() {
            return mEnd;
        }

        public int getEntityCount() {
            return mEntities.size();
        }

        public String getEntity(int index) {
            return mEntities.get(index);
        }

        public float getConfidenceScore(String entityType) {
            Float v = mEntityScores.get(entityType);
            return v != null ? v.floatValue() : 0f;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        @Override
        public String toString() {
            return String.format(
                    "TextLink{start=%d, end=%d, entityScores=%s}",
                    Integer.valueOf(mStart), Integer.valueOf(mEnd), mEntityScores);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mEntities.size());
            for (int i = 0; i < mEntities.size(); i++) {
                String k = mEntities.get(i);
                dest.writeString(k);
                dest.writeFloat(mEntityScores.get(k).floatValue());
            }
            dest.writeInt(mStart);
            dest.writeInt(mEnd);
            dest.writeBundle(mExtras);
        }

        public static final Parcelable.Creator<TextLink> CREATOR =
                new Parcelable.Creator<TextLink>() {
                    @Override
                    public TextLink createFromParcel(Parcel in) {
                        return new TextLink(in);
                    }

                    @Override
                    public TextLink[] newArray(int size) {
                        return new TextLink[size];
                    }
                };
    }

    public static final class Builder {
        private final String mFullText;
        private final List<TextLink> mLinks = new ArrayList<TextLink>();
        private Bundle mExtras;

        public Builder(String fullText) {
            mFullText = Objects.requireNonNull(fullText);
        }

        public Builder addLink(int start, int end, Map<String, Float> entityScores) {
            return addLink(start, end, entityScores, Bundle.EMPTY);
        }

        public Builder addLink(
                int start, int end, Map<String, Float> entityScores, Bundle extras) {
            mLinks.add(new TextLink(
                    start, end, Objects.requireNonNull(entityScores), Objects.requireNonNull(extras)));
            return this;
        }

        public Builder clearTextLinks() {
            mLinks.clear();
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public TextLinks build() {
            return new TextLinks(
                    mFullText,
                    mLinks,
                    mExtras == null ? Bundle.EMPTY : mExtras);
        }
    }

    public static final class Request implements Parcelable {
        private final CharSequence mText;
        private final LocaleList mDefaultLocales;
        private final TextClassifier.EntityConfig mEntityConfig;
        private final ZonedDateTime mReferenceTime;
        private final String mCallingPackageName;
        private final Bundle mExtras;

        private Request(
                CharSequence text,
                LocaleList defaultLocales,
                TextClassifier.EntityConfig entityConfig,
                ZonedDateTime referenceTime,
                String callingPackageName,
                Bundle extras) {
            mText = text;
            mDefaultLocales = defaultLocales;
            mEntityConfig = entityConfig;
            mReferenceTime = referenceTime;
            mCallingPackageName = callingPackageName;
            mExtras = extras;
        }

        private Request(Parcel in) {
            mText = in.readString();
            mDefaultLocales = in.readTypedObject(LocaleList.CREATOR);
            mEntityConfig = in.readTypedObject(TextClassifier.EntityConfig.CREATOR);
            mCallingPackageName = in.readString();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
            String refStr = in.readString();
            mReferenceTime = refStr != null
                    ? ZonedDateTime.parse(refStr, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    : null;
        }

        public CharSequence getText() {
            return mText;
        }

        public LocaleList getDefaultLocales() {
            return mDefaultLocales;
        }

        public TextClassifier.EntityConfig getEntityConfig() {
            return mEntityConfig;
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
            dest.writeTypedObject(mDefaultLocales, flags);
            dest.writeTypedObject(mEntityConfig, flags);
            dest.writeString(mCallingPackageName);
            dest.writeBundle(mExtras);
            dest.writeString(mReferenceTime != null
                    ? mReferenceTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : null);
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
            private LocaleList mDefaultLocales;
            private TextClassifier.EntityConfig mEntityConfig;
            private ZonedDateTime mReferenceTime;
            private Bundle mExtras;

            public Builder(CharSequence text) {
                mText = Objects.requireNonNull(text);
            }

            public Builder setDefaultLocales(LocaleList defaultLocales) {
                mDefaultLocales = defaultLocales;
                return this;
            }

            public Builder setEntityConfig(TextClassifier.EntityConfig entityConfig) {
                mEntityConfig = entityConfig;
                return this;
            }

            public Builder setExtras(Bundle extras) {
                mExtras = extras;
                return this;
            }

            public Builder setReferenceTime(ZonedDateTime referenceTime) {
                mReferenceTime = referenceTime;
                return this;
            }

            public Request build() {
                return new Request(
                        mText,
                        mDefaultLocales,
                        mEntityConfig,
                        mReferenceTime,
                        null,
                        mExtras == null ? Bundle.EMPTY : mExtras);
            }
        }
    }

    public static class TextLinkSpan extends ClickableSpan {
        private final TextLink mTextLink;

        public TextLinkSpan(TextLink textLink) {
            mTextLink = Objects.requireNonNull(textLink);
        }

        @Override
        public void onClick(View widget) {
        }

        public final TextLink getTextLink() {
            return mTextLink;
        }
    }
}
