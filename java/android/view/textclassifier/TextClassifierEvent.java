// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public abstract class TextClassifierEvent implements Parcelable {
    public static final int CATEGORY_SELECTION = 1;
    public static final int CATEGORY_LINKIFY = 2;
    public static final int CATEGORY_CONVERSATION_ACTIONS = 3;
    public static final int CATEGORY_LANGUAGE_DETECTION = 4;

    public static final int TYPE_SELECTION_STARTED = 1;
    public static final int TYPE_SELECTION_MODIFIED = 2;
    public static final int TYPE_SMART_SELECTION_SINGLE = 3;
    public static final int TYPE_SMART_SELECTION_MULTI = 4;
    public static final int TYPE_AUTO_SELECTION = 5;
    public static final int TYPE_ACTIONS_SHOWN = 6;
    public static final int TYPE_LINK_CLICKED = 7;
    public static final int TYPE_OVERTYPE = 8;
    public static final int TYPE_COPY_ACTION = 9;
    public static final int TYPE_PASTE_ACTION = 10;
    public static final int TYPE_CUT_ACTION = 11;
    public static final int TYPE_SHARE_ACTION = 12;
    public static final int TYPE_SMART_ACTION = 13;
    public static final int TYPE_SELECTION_DRAG = 14;
    public static final int TYPE_SELECTION_DESTROYED = 15;
    public static final int TYPE_OTHER_ACTION = 16;
    public static final int TYPE_SELECT_ALL = 17;
    public static final int TYPE_SELECTION_RESET = 18;
    public static final int TYPE_MANUAL_REPLY = 19;
    public static final int TYPE_ACTIONS_GENERATED = 20;
    public static final int TYPE_LINKS_GENERATED = 21;

    private final int mEventCategory;
    private final int mEventType;
    private final String[] mEntityTypes;
    private final TextClassificationContext mEventContext;
    private final String mResultId;
    private final int mEventIndex;
    private final float[] mScores;
    private final String mModelName;
    private final int[] mActionIndices;
    private final ULocale mLocale;
    private final Bundle mExtras;

    TextClassifierEvent(Builder<?> builder) {
        mEventCategory = builder.mEventCategory;
        mEventType = builder.mEventType;
        mEntityTypes = builder.mEntityTypes;
        mEventContext = builder.mEventContext;
        mResultId = builder.mResultId;
        mEventIndex = builder.mEventIndex;
        mScores = builder.mScores;
        mModelName = builder.mModelName;
        mActionIndices = builder.mActionIndices;
        mLocale = builder.mLocale;
        mExtras = builder.mExtras == null ? Bundle.EMPTY : builder.mExtras;
    }

    TextClassifierEvent(Parcel in) {
        mEventCategory = in.readInt();
        mEventType = in.readInt();
        mEntityTypes = in.createStringArray();
        mEventContext = in.readTypedObject(TextClassificationContext.CREATOR);
        mResultId = in.readString();
        mEventIndex = in.readInt();
        mScores = in.createFloatArray();
        mModelName = in.readString();
        mActionIndices = in.createIntArray();
        String lang = in.readString();
        mLocale = lang != null ? new ULocale(lang) : null;
        Bundle b = in.readBundle();
        mExtras = b != null ? b : Bundle.EMPTY;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(getParcelToken());
        dest.writeInt(mEventCategory);
        dest.writeInt(mEventType);
        dest.writeStringArray(mEntityTypes);
        dest.writeTypedObject(mEventContext, flags);
        dest.writeString(mResultId);
        dest.writeInt(mEventIndex);
        dest.writeFloatArray(mScores);
        dest.writeString(mModelName);
        dest.writeIntArray(mActionIndices);
        dest.writeString(mLocale != null ? mLocale.getName() : null);
        dest.writeBundle(mExtras);
    }

    int getParcelToken() {
        return mEventCategory;
    }

    public int getEventCategory() { return mEventCategory; }
    public int getEventType() { return mEventType; }
    public String[] getEntityTypes() { return mEntityTypes.clone(); }
    public TextClassificationContext getEventContext() { return mEventContext; }
    public String getResultId() { return mResultId; }
    public int getEventIndex() { return mEventIndex; }
    public float[] getScores() { return mScores.clone(); }
    public String getModelName() { return mModelName; }
    public int[] getActionIndices() { return mActionIndices.clone(); }
    public ULocale getLocale() { return mLocale; }
    public Bundle getExtras() { return mExtras; }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{eventCategory=" + mEventCategory
                + ", eventType=" + mEventType
                + ", entityTypes=" + Arrays.toString(mEntityTypes)
                + ", resultId=" + mResultId + "}";
    }

    public static final Parcelable.Creator<TextClassifierEvent> CREATOR =
            new Parcelable.Creator<TextClassifierEvent>() {
                @Override
                public TextClassifierEvent createFromParcel(Parcel in) {
                    int token = in.readInt();
                    if (token == CATEGORY_SELECTION) {
                        return new TextSelectionEvent(in);
                    }
                    if (token == CATEGORY_LINKIFY) {
                        return new TextLinkifyEvent(in);
                    }
                    if (token == CATEGORY_LANGUAGE_DETECTION) {
                        return new LanguageDetectionEvent(in);
                    }
                    if (token == CATEGORY_CONVERSATION_ACTIONS) {
                        return new ConversationActionsEvent(in);
                    }
                    throw new IllegalStateException("Unexpected event token: " + token);
                }

                @Override
                public TextClassifierEvent[] newArray(int size) {
                    return new TextClassifierEvent[size];
                }
            };

    public abstract static class Builder<T extends Builder<T>> {
        final int mEventCategory;
        final int mEventType;
        String[] mEntityTypes = new String[0];
        TextClassificationContext mEventContext;
        String mResultId;
        int mEventIndex;
        float[] mScores = new float[0];
        String mModelName;
        int[] mActionIndices = new int[0];
        ULocale mLocale;
        Bundle mExtras;

        Builder(int eventCategory, int eventType) {
            mEventCategory = eventCategory;
            mEventType = eventType;
        }

        public T setEntityTypes(String... entityTypes) {
            Objects.requireNonNull(entityTypes);
            mEntityTypes = entityTypes.clone();
            return self();
        }

        public T setEventContext(TextClassificationContext eventContext) {
            mEventContext = eventContext;
            return self();
        }

        public T setResultId(String resultId) {
            mResultId = resultId;
            return self();
        }

        public T setEventIndex(int eventIndex) {
            mEventIndex = eventIndex;
            return self();
        }

        public T setScores(float... scores) {
            Objects.requireNonNull(scores);
            mScores = scores.clone();
            return self();
        }

        public T setModelName(String modelVersion) {
            mModelName = modelVersion;
            return self();
        }

        public T setActionIndices(int... actionIndices) {
            Objects.requireNonNull(actionIndices);
            mActionIndices = actionIndices.clone();
            return self();
        }

        public T setLocale(ULocale locale) {
            mLocale = locale;
            return self();
        }

        public T setExtras(Bundle extras) {
            mExtras = Objects.requireNonNull(extras);
            return self();
        }

        abstract T self();
    }

    public static final class TextSelectionEvent extends TextClassifierEvent implements Parcelable {
        private final int mRelativeWordStartIndex;
        private final int mRelativeWordEndIndex;
        private final int mRelativeSuggestedWordStartIndex;
        private final int mRelativeSuggestedWordEndIndex;

        private TextSelectionEvent(Builder builder) {
            super(builder);
            mRelativeWordStartIndex = builder.mRelativeWordStartIndex;
            mRelativeWordEndIndex = builder.mRelativeWordEndIndex;
            mRelativeSuggestedWordStartIndex = builder.mRelativeSuggestedWordStartIndex;
            mRelativeSuggestedWordEndIndex = builder.mRelativeSuggestedWordEndIndex;
        }

        private TextSelectionEvent(Parcel in) {
            super(in);
            mRelativeWordStartIndex = in.readInt();
            mRelativeWordEndIndex = in.readInt();
            mRelativeSuggestedWordStartIndex = in.readInt();
            mRelativeSuggestedWordEndIndex = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mRelativeWordStartIndex);
            dest.writeInt(mRelativeWordEndIndex);
            dest.writeInt(mRelativeSuggestedWordStartIndex);
            dest.writeInt(mRelativeSuggestedWordEndIndex);
        }

        public int getRelativeWordStartIndex() { return mRelativeWordStartIndex; }
        public int getRelativeWordEndIndex() { return mRelativeWordEndIndex; }
        public int getRelativeSuggestedWordStartIndex() { return mRelativeSuggestedWordStartIndex; }
        public int getRelativeSuggestedWordEndIndex() { return mRelativeSuggestedWordEndIndex; }

        public static final Parcelable.Creator<TextSelectionEvent> CREATOR =
                new Parcelable.Creator<TextSelectionEvent>() {
                    @Override
                    public TextSelectionEvent createFromParcel(Parcel in) {
                        in.readInt();
                        return new TextSelectionEvent(in);
                    }

                    @Override
                    public TextSelectionEvent[] newArray(int size) {
                        return new TextSelectionEvent[size];
                    }
                };

        public static final class Builder
                extends TextClassifierEvent.Builder<TextSelectionEvent.Builder> {
            int mRelativeWordStartIndex;
            int mRelativeWordEndIndex;
            int mRelativeSuggestedWordStartIndex;
            int mRelativeSuggestedWordEndIndex;

            public Builder(int eventType) {
                super(CATEGORY_SELECTION, eventType);
            }

            public Builder setRelativeWordStartIndex(int relativeWordStartIndex) {
                mRelativeWordStartIndex = relativeWordStartIndex;
                return this;
            }

            public Builder setRelativeWordEndIndex(int relativeWordEndIndex) {
                mRelativeWordEndIndex = relativeWordEndIndex;
                return this;
            }

            public Builder setRelativeSuggestedWordStartIndex(
                    int relativeSuggestedWordStartIndex) {
                mRelativeSuggestedWordStartIndex = relativeSuggestedWordStartIndex;
                return this;
            }

            public Builder setRelativeSuggestedWordEndIndex(
                    int relativeSuggestedWordEndIndex) {
                mRelativeSuggestedWordEndIndex = relativeSuggestedWordEndIndex;
                return this;
            }

            @Override
            Builder self() {
                return this;
            }

            public TextSelectionEvent build() {
                return new TextSelectionEvent(this);
            }
        }
    }

    public static final class TextLinkifyEvent extends TextClassifierEvent implements Parcelable {
        private TextLinkifyEvent(Builder builder) {
            super(builder);
        }

        private TextLinkifyEvent(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<TextLinkifyEvent> CREATOR =
                new Parcelable.Creator<TextLinkifyEvent>() {
                    @Override
                    public TextLinkifyEvent createFromParcel(Parcel in) {
                        in.readInt();
                        return new TextLinkifyEvent(in);
                    }

                    @Override
                    public TextLinkifyEvent[] newArray(int size) {
                        return new TextLinkifyEvent[size];
                    }
                };

        public static final class Builder
                extends TextClassifierEvent.Builder<TextLinkifyEvent.Builder> {
            public Builder(int eventType) {
                super(CATEGORY_LINKIFY, eventType);
            }

            @Override
            Builder self() {
                return this;
            }

            public TextLinkifyEvent build() {
                return new TextLinkifyEvent(this);
            }
        }
    }

    public static final class LanguageDetectionEvent
            extends TextClassifierEvent implements Parcelable {
        private LanguageDetectionEvent(Builder builder) {
            super(builder);
        }

        private LanguageDetectionEvent(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<LanguageDetectionEvent> CREATOR =
                new Parcelable.Creator<LanguageDetectionEvent>() {
                    @Override
                    public LanguageDetectionEvent createFromParcel(Parcel in) {
                        in.readInt();
                        return new LanguageDetectionEvent(in);
                    }

                    @Override
                    public LanguageDetectionEvent[] newArray(int size) {
                        return new LanguageDetectionEvent[size];
                    }
                };

        public static final class Builder
                extends TextClassifierEvent.Builder<LanguageDetectionEvent.Builder> {
            public Builder(int eventType) {
                super(CATEGORY_LANGUAGE_DETECTION, eventType);
            }

            @Override
            Builder self() {
                return this;
            }

            public LanguageDetectionEvent build() {
                return new LanguageDetectionEvent(this);
            }
        }
    }

    public static final class ConversationActionsEvent
            extends TextClassifierEvent implements Parcelable {
        private ConversationActionsEvent(Builder builder) {
            super(builder);
        }

        private ConversationActionsEvent(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<ConversationActionsEvent> CREATOR =
                new Parcelable.Creator<ConversationActionsEvent>() {
                    @Override
                    public ConversationActionsEvent createFromParcel(Parcel in) {
                        in.readInt();
                        return new ConversationActionsEvent(in);
                    }

                    @Override
                    public ConversationActionsEvent[] newArray(int size) {
                        return new ConversationActionsEvent[size];
                    }
                };

        public static final class Builder
                extends TextClassifierEvent.Builder<ConversationActionsEvent.Builder> {
            public Builder(int eventType) {
                super(CATEGORY_CONVERSATION_ACTIONS, eventType);
            }

            @Override
            Builder self() {
                return this;
            }

            public ConversationActionsEvent build() {
                return new ConversationActionsEvent(this);
            }
        }
    }
}
