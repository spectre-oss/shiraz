// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class ZenPolicy implements Parcelable {
    public static final int STATE_UNSET = 0;
    public static final int STATE_ALLOW = 1;
    public static final int STATE_DISALLOW = 2;

    public static final int PEOPLE_TYPE_UNSET = 0;
    public static final int PEOPLE_TYPE_ANYONE = 1;
    public static final int PEOPLE_TYPE_CONTACTS = 2;
    public static final int PEOPLE_TYPE_STARRED = 3;
    public static final int PEOPLE_TYPE_NONE = 4;

    public static final int CONVERSATION_SENDERS_UNSET = 0;
    public static final int CONVERSATION_SENDERS_ANYONE = 1;
    public static final int CONVERSATION_SENDERS_IMPORTANT = 2;
    public static final int CONVERSATION_SENDERS_NONE = 3;

    public static final Parcelable.Creator<ZenPolicy> CREATOR =
            new Parcelable.Creator<ZenPolicy>() {
                @Override
                public ZenPolicy createFromParcel(Parcel source) {
                    int[] vals = source.createIntArray();
                    return new ZenPolicy(vals != null ? vals : new int[20]);
                }

                @Override
                public ZenPolicy[] newArray(int size) {
                    return new ZenPolicy[size];
                }
            };

    private final int[] mValues;

    ZenPolicy(int[] values) {
        mValues = Arrays.copyOf(values, 20);
    }

    public int getPriorityCategoryReminders() { return mValues[0]; }
    public int getPriorityCategoryEvents() { return mValues[1]; }
    public int getPriorityCategoryMessages() { return mValues[2]; }
    public int getPriorityCategoryCalls() { return mValues[3]; }
    public int getPriorityCategoryRepeatCallers() { return mValues[4]; }
    public int getPriorityCategoryAlarms() { return mValues[5]; }
    public int getPriorityCategoryMedia() { return mValues[6]; }
    public int getPriorityCategorySystem() { return mValues[7]; }
    public int getPriorityCategoryConversations() { return mValues[8]; }
    public int getVisualEffectFullScreenIntent() { return mValues[9]; }
    public int getVisualEffectLights() { return mValues[10]; }
    public int getVisualEffectPeek() { return mValues[11]; }
    public int getVisualEffectStatusBar() { return mValues[12]; }
    public int getVisualEffectBadge() { return mValues[13]; }
    public int getVisualEffectAmbient() { return mValues[14]; }
    public int getVisualEffectNotificationList() { return mValues[15]; }
    public int getPriorityMessageSenders() { return mValues[16]; }
    public int getPriorityCallSenders() { return mValues[17]; }
    public int getPriorityConversationSenders() { return mValues[18]; }
    public int getPriorityChannelsAllowed() { return mValues[19]; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(mValues);
    }

    @Override
    public String toString() {
        return "ZenPolicy" + Arrays.toString(mValues);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZenPolicy)) return false;
        return Arrays.equals(mValues, ((ZenPolicy) o).mValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(Arrays.hashCode(mValues)));
    }

    public static final class Builder {
        private final int[] mValues = new int[20];

        public Builder() {
        }

        private static int toState(boolean allow) {
            return allow ? STATE_ALLOW : STATE_DISALLOW;
        }

        public ZenPolicy build() {
            return new ZenPolicy(mValues);
        }

        public Builder allowAllSounds() {
            for (int i = 0; i <= 8; i++) mValues[i] = STATE_ALLOW;
            mValues[16] = PEOPLE_TYPE_ANYONE;
            mValues[17] = PEOPLE_TYPE_ANYONE;
            mValues[18] = CONVERSATION_SENDERS_ANYONE;
            mValues[19] = STATE_ALLOW;
            return this;
        }

        public Builder disallowAllSounds() {
            for (int i = 0; i <= 8; i++) mValues[i] = STATE_DISALLOW;
            mValues[16] = PEOPLE_TYPE_NONE;
            mValues[17] = PEOPLE_TYPE_NONE;
            mValues[18] = CONVERSATION_SENDERS_NONE;
            mValues[19] = STATE_DISALLOW;
            return this;
        }

        public Builder showAllVisualEffects() {
            for (int i = 9; i <= 15; i++) mValues[i] = STATE_ALLOW;
            return this;
        }

        public Builder hideAllVisualEffects() {
            for (int i = 9; i <= 15; i++) mValues[i] = STATE_DISALLOW;
            return this;
        }

        public Builder allowReminders(boolean allow) { mValues[0] = toState(allow); return this; }
        public Builder allowEvents(boolean allow) { mValues[1] = toState(allow); return this; }
        public Builder allowRepeatCallers(boolean allow) { mValues[4] = toState(allow); return this; }
        public Builder allowAlarms(boolean allow) { mValues[5] = toState(allow); return this; }
        public Builder allowMedia(boolean allow) { mValues[6] = toState(allow); return this; }
        public Builder allowSystem(boolean allow) { mValues[7] = toState(allow); return this; }

        public Builder allowConversations(int audienceType) {
            if (audienceType == CONVERSATION_SENDERS_UNSET) {
                mValues[8] = STATE_UNSET;
            } else if (audienceType == CONVERSATION_SENDERS_NONE) {
                mValues[8] = STATE_DISALLOW;
            } else {
                mValues[8] = STATE_ALLOW;
            }
            mValues[18] = audienceType;
            return this;
        }

        public Builder allowMessages(int audienceType) {
            if (audienceType == PEOPLE_TYPE_UNSET) {
                mValues[2] = STATE_UNSET;
            } else if (audienceType == PEOPLE_TYPE_NONE) {
                mValues[2] = STATE_DISALLOW;
            } else {
                mValues[2] = STATE_ALLOW;
            }
            mValues[16] = audienceType;
            return this;
        }

        public Builder allowCalls(int audienceType) {
            if (audienceType == PEOPLE_TYPE_UNSET) {
                mValues[3] = STATE_UNSET;
            } else if (audienceType == PEOPLE_TYPE_NONE) {
                mValues[3] = STATE_DISALLOW;
            } else {
                mValues[3] = STATE_ALLOW;
            }
            mValues[17] = audienceType;
            return this;
        }

        public Builder showFullScreenIntent(boolean show) { mValues[9] = toState(show); return this; }
        public Builder showLights(boolean show) { mValues[10] = toState(show); return this; }
        public Builder showPeeking(boolean show) { mValues[11] = toState(show); return this; }
        public Builder showStatusBarIcons(boolean show) { mValues[12] = toState(show); return this; }
        public Builder showBadges(boolean show) { mValues[13] = toState(show); return this; }
        public Builder showInAmbientDisplay(boolean show) { mValues[14] = toState(show); return this; }
        public Builder showInNotificationList(boolean show) { mValues[15] = toState(show); return this; }
        public Builder allowPriorityChannels(boolean allow) { mValues[19] = toState(allow); return this; }
    }
}
