// SPDX-License-Identifier: GPL-3.0-only
package android.app.people;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class ConversationStatus implements Parcelable {
    public static final int ACTIVITY_OTHER = 0;
    public static final int ACTIVITY_BIRTHDAY = 1;
    public static final int ACTIVITY_ANNIVERSARY = 2;
    public static final int ACTIVITY_NEW_STORY = 3;
    public static final int ACTIVITY_AUDIO = 4;
    public static final int ACTIVITY_VIDEO = 5;
    public static final int ACTIVITY_GAME = 6;
    public static final int ACTIVITY_LOCATION = 7;
    public static final int ACTIVITY_UPCOMING_BIRTHDAY = 8;

    public static final int AVAILABILITY_UNKNOWN = -1;
    public static final int AVAILABILITY_AVAILABLE = 0;
    public static final int AVAILABILITY_BUSY = 1;
    public static final int AVAILABILITY_OFFLINE = 2;

    private final String mId;
    private final int mActivity;
    private final int mAvailability;
    private final CharSequence mDescription;
    private final Icon mIcon;
    private final long mStartTimeMs;
    private final long mEndTimeMs;

    private ConversationStatus(Builder b) {
        mId = b.mId;
        mActivity = b.mActivity;
        mAvailability = b.mAvailability;
        mDescription = b.mDescription;
        mIcon = b.mIcon;
        mStartTimeMs = b.mStartTimeMs;
        mEndTimeMs = b.mEndTimeMs;
    }

    ConversationStatus(Parcel in) {
        mId = in.readString();
        mActivity = in.readInt();
        mAvailability = in.readInt();
        mDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mIcon = in.readTypedObject(Icon.CREATOR);
        mStartTimeMs = in.readLong();
        mEndTimeMs = in.readLong();
    }

    public String getId() {
        return mId;
    }

    public int getActivity() {
        return mActivity;
    }

    public int getAvailability() {
        return mAvailability;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public long getStartTimeMillis() {
        return mStartTimeMs;
    }

    public long getEndTimeMillis() {
        return mEndTimeMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationStatus)) return false;
        ConversationStatus that = (ConversationStatus) o;
        String d1 = mDescription == null ? null : mDescription.toString();
        String d2 = that.mDescription == null ? null : that.mDescription.toString();
        return mActivity == that.mActivity
                && mAvailability == that.mAvailability
                && mStartTimeMs == that.mStartTimeMs
                && mEndTimeMs == that.mEndTimeMs
                && Objects.equals(mId, that.mId)
                && Objects.equals(d1, d2)
                && Objects.equals(mIcon, that.mIcon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                Integer.valueOf(mActivity),
                Integer.valueOf(mAvailability),
                mDescription == null ? null : mDescription.toString(),
                mIcon,
                Long.valueOf(mStartTimeMs),
                Long.valueOf(mEndTimeMs));
    }

    @Override
    public String toString() {
        return "ConversationStatus{mId='" + mId + "', mActivity=" + mActivity
                + ", mAvailability=" + mAvailability + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeInt(mActivity);
        dest.writeInt(mAvailability);
        TextUtils.writeToParcel(mDescription, dest, flags);
        dest.writeTypedObject(mIcon, flags);
        dest.writeLong(mStartTimeMs);
        dest.writeLong(mEndTimeMs);
    }

    public static final Parcelable.Creator<ConversationStatus> CREATOR =
            new Parcelable.Creator<ConversationStatus>() {
                @Override
                public ConversationStatus createFromParcel(Parcel in) {
                    return new ConversationStatus(in);
                }

                @Override
                public ConversationStatus[] newArray(int size) {
                    return new ConversationStatus[size];
                }
            };

    public static final class Builder {
        final String mId;
        final int mActivity;
        int mAvailability = AVAILABILITY_UNKNOWN;
        CharSequence mDescription;
        Icon mIcon;
        long mStartTimeMs = -1L;
        long mEndTimeMs = -1L;

        public Builder(String id, int activity) {
            if (id == null) throw new NullPointerException("id");
            mId = id;
            mActivity = activity;
        }

        public Builder setAvailability(int availability) {
            mAvailability = availability;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setIcon(Icon icon) {
            mIcon = icon;
            return this;
        }

        public Builder setStartTimeMillis(long startTimeMs) {
            mStartTimeMs = startTimeMs;
            return this;
        }

        public Builder setEndTimeMillis(long endTimeMs) {
            mEndTimeMs = endTimeMs;
            return this;
        }

        public ConversationStatus build() {
            return new ConversationStatus(this);
        }
    }
}
