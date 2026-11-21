// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.app.Person;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ConversationActions implements Parcelable {
    private final List<ConversationAction> mConversationActions;
    private final String mId;

    public ConversationActions(List<ConversationAction> conversationActions, String id) {
        mConversationActions = Collections.unmodifiableList(
                new ArrayList<ConversationAction>(Objects.requireNonNull(conversationActions)));
        mId = id;
    }

    private ConversationActions(Parcel in) {
        List<ConversationAction> list = in.createTypedArrayList(ConversationAction.CREATOR);
        mConversationActions = list != null
                ? Collections.unmodifiableList(list)
                : Collections.<ConversationAction>emptyList();
        mId = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(mConversationActions);
        parcel.writeString(mId);
    }

    public List<ConversationAction> getConversationActions() {
        return mConversationActions;
    }

    public String getId() {
        return mId;
    }

    public static final Parcelable.Creator<ConversationActions> CREATOR =
            new Parcelable.Creator<ConversationActions>() {
                @Override
                public ConversationActions createFromParcel(Parcel in) {
                    return new ConversationActions(in);
                }

                @Override
                public ConversationActions[] newArray(int size) {
                    return new ConversationActions[size];
                }
            };

    public static final class Message implements Parcelable {
        public static final Person PERSON_USER_SELF =
                new Person.Builder()
                        .setKey("text-classifier-conversation-actions-user-self")
                        .build();
        public static final Person PERSON_USER_OTHERS =
                new Person.Builder()
                        .setKey("text-classifier-conversation-actions-user-others")
                        .build();

        private final Person mAuthor;
        private final ZonedDateTime mReferenceTime;
        private final CharSequence mText;
        private final Bundle mExtras;

        private Message(
                Person author,
                ZonedDateTime referenceTime,
                CharSequence text,
                Bundle extras) {
            mAuthor = author;
            mReferenceTime = referenceTime;
            mText = text;
            mExtras = Objects.requireNonNull(extras);
        }

        private Message(Parcel in) {
            mAuthor = in.readTypedObject(Person.CREATOR);
            String timeStr = in.readString();
            mReferenceTime = timeStr != null
                    ? ZonedDateTime.parse(timeStr, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    : null;
            mText = in.readString();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeTypedObject(mAuthor, flags);
            parcel.writeString(mReferenceTime != null
                    ? mReferenceTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : null);
            parcel.writeString(mText != null ? mText.toString() : null);
            parcel.writeBundle(mExtras);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public Person getAuthor() {
            return mAuthor;
        }

        public ZonedDateTime getReferenceTime() {
            return mReferenceTime;
        }

        public CharSequence getText() {
            return mText;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public static final Parcelable.Creator<Message> CREATOR =
                new Parcelable.Creator<Message>() {
                    @Override
                    public Message createFromParcel(Parcel in) {
                        return new Message(in);
                    }

                    @Override
                    public Message[] newArray(int size) {
                        return new Message[size];
                    }
                };

        public static final class Builder {
            private final Person mAuthor;
            private ZonedDateTime mReferenceTime;
            private CharSequence mText;
            private Bundle mExtras;

            public Builder(Person author) {
                mAuthor = Objects.requireNonNull(author);
            }

            public Builder setText(CharSequence text) {
                mText = text;
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

            public Message build() {
                return new Message(
                        mAuthor,
                        mReferenceTime,
                        mText,
                        mExtras == null ? Bundle.EMPTY : mExtras);
            }
        }
    }

    public static final class Request implements Parcelable {
        public static final String HINT_FOR_NOTIFICATION = "notification";
        public static final String HINT_FOR_IN_APP = "in_app";

        private final List<Message> mConversation;
        private final TextClassifier.EntityConfig mTypeConfig;
        private final int mMaxSuggestions;
        private final List<String> mHints;
        private final String mCallingPackageName;
        private final Bundle mExtras;

        private Request(
                List<Message> conversation,
                TextClassifier.EntityConfig typeConfig,
                int maxSuggestions,
                List<String> hints,
                String callingPackageName,
                Bundle extras) {
            mConversation = Collections.unmodifiableList(new ArrayList<Message>(conversation));
            mTypeConfig = Objects.requireNonNull(typeConfig);
            mMaxSuggestions = maxSuggestions;
            mHints = Collections.unmodifiableList(new ArrayList<String>(hints));
            mCallingPackageName = callingPackageName;
            mExtras = extras;
        }

        private Request(Parcel in) {
            List<Message> conv = in.createTypedArrayList(Message.CREATOR);
            mConversation = conv != null
                    ? Collections.unmodifiableList(conv)
                    : Collections.<Message>emptyList();
            mTypeConfig = in.readTypedObject(TextClassifier.EntityConfig.CREATOR);
            mMaxSuggestions = in.readInt();
            String[] hintsArr = in.createStringArray();
            List<String> hints = new ArrayList<String>();
            if (hintsArr != null) {
                Collections.addAll(hints, hintsArr);
            }
            mHints = Collections.unmodifiableList(hints);
            mCallingPackageName = in.readString();
            Bundle b = in.readBundle();
            mExtras = b != null ? b : Bundle.EMPTY;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeTypedList(mConversation);
            parcel.writeTypedObject(mTypeConfig, flags);
            parcel.writeInt(mMaxSuggestions);
            parcel.writeStringArray(mHints.toArray(new String[0]));
            parcel.writeString(mCallingPackageName);
            parcel.writeBundle(mExtras);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public TextClassifier.EntityConfig getTypeConfig() {
            return mTypeConfig;
        }

        public List<Message> getConversation() {
            return mConversation;
        }

        public int getMaxSuggestions() {
            return mMaxSuggestions;
        }

        public List<String> getHints() {
            return mHints;
        }

        public String getCallingPackageName() {
            return mCallingPackageName;
        }

        public Bundle getExtras() {
            return mExtras;
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
            private final List<Message> mConversation;
            private TextClassifier.EntityConfig mTypeConfig;
            private int mMaxSuggestions = -1;
            private List<String> mHints;
            private Bundle mExtras;

            public Builder(List<Message> conversation) {
                mConversation = Objects.requireNonNull(conversation);
            }

            public Builder setHints(List<String> hints) {
                mHints = hints;
                return this;
            }

            public Builder setTypeConfig(TextClassifier.EntityConfig typeConfig) {
                mTypeConfig = typeConfig;
                return this;
            }

            public Builder setMaxSuggestions(int maxSuggestions) {
                if (maxSuggestions < -1) {
                    throw new IllegalArgumentException("maxSuggestions has to be >= -1");
                }
                mMaxSuggestions = maxSuggestions;
                return this;
            }

            public Builder setExtras(Bundle extras) {
                mExtras = extras;
                return this;
            }

            public Request build() {
                return new Request(
                        mConversation,
                        mTypeConfig == null
                                ? new TextClassifier.EntityConfig.Builder().build()
                                : mTypeConfig,
                        mMaxSuggestions,
                        mHints == null ? Collections.<String>emptyList() : mHints,
                        null,
                        mExtras == null ? Bundle.EMPTY : mExtras);
            }
        }
    }
}
