// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.app.RemoteAction;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ConversationAction implements Parcelable {
    public static final String TYPE_VIEW_CALENDAR = "view_calendar";
    public static final String TYPE_VIEW_MAP = "view_map";
    public static final String TYPE_TRACK_FLIGHT = "track_flight";
    public static final String TYPE_OPEN_URL = "open_url";
    public static final String TYPE_SEND_SMS = "send_sms";
    public static final String TYPE_CALL_PHONE = "call_phone";
    public static final String TYPE_SEND_EMAIL = "send_email";
    public static final String TYPE_TEXT_REPLY = "text_reply";
    public static final String TYPE_CREATE_REMINDER = "create_reminder";
    public static final String TYPE_SHARE_LOCATION = "share_location";

    private final String mType;
    private final CharSequence mTextReply;
    private final RemoteAction mAction;
    private final float mScore;
    private final Bundle mExtras;

    private ConversationAction(
            String type,
            RemoteAction action,
            CharSequence textReply,
            float score,
            Bundle extras) {
        mType = Objects.requireNonNull(type);
        mAction = action;
        mTextReply = textReply;
        mScore = score;
        mExtras = Objects.requireNonNull(extras);
    }

    private ConversationAction(Parcel in) {
        mType = in.readString();
        mAction = in.readTypedObject(RemoteAction.CREATOR);
        mTextReply = in.readString();
        mScore = in.readFloat();
        Bundle b = in.readBundle();
        mExtras = b != null ? b : Bundle.EMPTY;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mType);
        parcel.writeTypedObject(mAction, flags);
        parcel.writeString(mTextReply != null ? mTextReply.toString() : null);
        parcel.writeFloat(mScore);
        parcel.writeBundle(mExtras);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getType() {
        return mType;
    }

    public RemoteAction getAction() {
        return mAction;
    }

    public float getConfidenceScore() {
        return mScore;
    }

    public CharSequence getTextReply() {
        return mTextReply;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public static final Parcelable.Creator<ConversationAction> CREATOR =
            new Parcelable.Creator<ConversationAction>() {
                @Override
                public ConversationAction createFromParcel(Parcel in) {
                    return new ConversationAction(in);
                }

                @Override
                public ConversationAction[] newArray(int size) {
                    return new ConversationAction[size];
                }
            };

    public static final class Builder {
        private final String mType;
        private RemoteAction mAction;
        private CharSequence mTextReply;
        private float mScore;
        private Bundle mExtras;

        public Builder(String actionType) {
            mType = Objects.requireNonNull(actionType);
        }

        public Builder setAction(RemoteAction action) {
            mAction = action;
            return this;
        }

        public Builder setTextReply(CharSequence textReply) {
            mTextReply = textReply;
            return this;
        }

        public Builder setConfidenceScore(float score) {
            mScore = score;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public ConversationAction build() {
            return new ConversationAction(
                    mType,
                    mAction,
                    mTextReply,
                    mScore,
                    mExtras == null ? Bundle.EMPTY : mExtras);
        }
    }
}
