// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class NotificationListenerService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.service.notification.NotificationListenerService";
    public static final String ACTION_SETTINGS_HOME =
            "android.service.notification.action.SETTINGS_HOME";
    public static final String META_DATA_DEFAULT_AUTOBIND =
            "android.service.notification.default_autobind_listenerservice";
    public static final String META_DATA_DEFAULT_FILTER_TYPES =
            "android.service.notification.default_filter_types";
    public static final String META_DATA_DISABLED_FILTER_TYPES =
            "android.service.notification.disabled_filter_types";

    public static final int HINT_HOST_DISABLE_EFFECTS = 1;
    public static final int HINT_HOST_DISABLE_NOTIFICATION_EFFECTS = 2;
    public static final int HINT_HOST_DISABLE_CALL_EFFECTS = 4;

    public static final int SUPPRESSED_EFFECT_SCREEN_OFF = 1;
    public static final int SUPPRESSED_EFFECT_SCREEN_ON = 2;

    public static final int INTERRUPTION_FILTER_UNKNOWN = 0;
    public static final int INTERRUPTION_FILTER_ALL = 1;
    public static final int INTERRUPTION_FILTER_PRIORITY = 2;
    public static final int INTERRUPTION_FILTER_NONE = 3;
    public static final int INTERRUPTION_FILTER_ALARMS = 4;

    public static final int NOTIFICATION_CHANNEL_OR_GROUP_ADDED = 1;
    public static final int NOTIFICATION_CHANNEL_OR_GROUP_UPDATED = 2;
    public static final int NOTIFICATION_CHANNEL_OR_GROUP_DELETED = 3;

    public static final int FLAG_FILTER_TYPE_CONVERSATIONS = 1;
    public static final int FLAG_FILTER_TYPE_ALERTING = 2;
    public static final int FLAG_FILTER_TYPE_SILENT = 4;
    public static final int FLAG_FILTER_TYPE_ONGOING = 8;

    public static final int REASON_CLICK = 1;
    public static final int REASON_CANCEL = 2;
    public static final int REASON_CANCEL_ALL = 3;
    public static final int REASON_ERROR = 4;
    public static final int REASON_PACKAGE_CHANGED = 5;
    public static final int REASON_USER_STOPPED = 6;
    public static final int REASON_PACKAGE_BANNED = 7;
    public static final int REASON_APP_CANCEL = 8;
    public static final int REASON_APP_CANCEL_ALL = 9;
    public static final int REASON_LISTENER_CANCEL = 10;
    public static final int REASON_LISTENER_CANCEL_ALL = 11;
    public static final int REASON_GROUP_SUMMARY_CANCELED = 12;
    public static final int REASON_GROUP_OPTIMIZATION = 13;
    public static final int REASON_PACKAGE_SUSPENDED = 14;
    public static final int REASON_PROFILE_TURNED_OFF = 15;
    public static final int REASON_UNAUTOBUNDLED = 16;
    public static final int REASON_CHANNEL_BANNED = 17;
    public static final int REASON_SNOOZED = 18;
    public static final int REASON_TIMEOUT = 19;
    public static final int REASON_CHANNEL_REMOVED = 20;
    public static final int REASON_CLEAR_DATA = 21;
    public static final int REASON_ASSISTANT_CANCEL = 22;
    public static final int REASON_LOCKDOWN = 23;

    private final IBinder mBinder = new Binder();
    private int mHints;
    private int mInterruptionFilter = INTERRUPTION_FILTER_ALL;
    private RankingMap mCurrentRanking = new RankingMap(new String[0]);

    public NotificationListenerService() {
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    public void onNotificationPosted(StatusBarNotification sbn) {
    }

    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationPosted(sbn);
    }

    public void onNotificationRemoved(StatusBarNotification sbn) {
    }

    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationRemoved(sbn);
    }

    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        onNotificationRemoved(sbn, rankingMap);
    }

    public void onListenerConnected() {
    }

    public void onListenerDisconnected() {
    }

    public void onNotificationRankingUpdate(RankingMap rankingMap) {
    }

    public void onListenerHintsChanged(int hints) {
    }

    public void onSilentStatusBarIconsVisibilityChanged(boolean hideSilentStatusIcons) {
    }

    public void onNotificationChannelModified(String pkg, UserHandle user,
            NotificationChannel channel, int modificationType) {
    }

    public void onNotificationChannelGroupModified(String pkg, UserHandle user,
            NotificationChannelGroup group, int modificationType) {
    }

    public void onInterruptionFilterChanged(int interruptionFilter) {
    }

    public final void cancelNotification(String pkg, String tag, int id) {
    }

    public final void cancelNotification(String key) {
    }

    public final void cancelAllNotifications() {
    }

    public final void cancelNotifications(String[] keys) {
    }

    public final void snoozeNotification(String key, long durationMs) {
    }

    public final void migrateNotificationFilter(int defaultTypes, List<String> disallowedPkgs) {
    }

    public final void setNotificationsShown(String[] keys) {
    }

    public final void updateNotificationChannel(String pkg, UserHandle user,
            NotificationChannel channel) {
    }

    public final List<NotificationChannel> getNotificationChannels(String pkg, UserHandle user) {
        return Collections.emptyList();
    }

    public final List<NotificationChannelGroup> getNotificationChannelGroups(String pkg,
            UserHandle user) {
        return Collections.emptyList();
    }

    public StatusBarNotification[] getActiveNotifications() {
        return new StatusBarNotification[0];
    }

    public final StatusBarNotification[] getSnoozedNotifications() {
        return new StatusBarNotification[0];
    }

    public StatusBarNotification[] getActiveNotifications(String[] keys) {
        return new StatusBarNotification[0];
    }

    public final int getCurrentListenerHints() {
        return mHints;
    }

    public final int getCurrentInterruptionFilter() {
        return mInterruptionFilter;
    }

    public final void clearRequestedListenerHints() {
        mHints = 0;
    }

    public final void requestListenerHints(int hints) {
        mHints = hints;
    }

    public final void requestInterruptionFilter(int interruptionFilter) {
        mInterruptionFilter = interruptionFilter;
    }

    public RankingMap getCurrentRanking() {
        return mCurrentRanking;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public static void requestRebind(ComponentName componentName) {
    }

    public static void requestUnbind(ComponentName componentName) {
    }

    public final void requestUnbind() {
    }

    public static class Ranking {
        public static final int VISIBILITY_NO_OVERRIDE = -1000;
        public static final int USER_SENTIMENT_NEGATIVE = -1;
        public static final int USER_SENTIMENT_NEUTRAL = 0;
        public static final int USER_SENTIMENT_POSITIVE = 1;

        private String mKey;
        private int mRank = -1;
        private boolean mIsAmbient;
        private int mVisibilityOverride = VISIBILITY_NO_OVERRIDE;
        private int mSuppressedVisualEffects;
        private boolean mMatchesInterruptionFilter = true;
        private int mImportance = NotificationManager.IMPORTANCE_DEFAULT;
        private CharSequence mImportanceExplanation;
        private String mOverrideGroupKey;
        private NotificationChannel mChannel;
        private int mUserSentiment = USER_SENTIMENT_NEUTRAL;
        private List<Notification.Action> mSmartActions = Collections.emptyList();
        private List<CharSequence> mSmartReplies = Collections.emptyList();
        private boolean mShowBadge = true;
        private boolean mSuspended;
        private long mLastAudiblyAlertedMillis = -1L;
        private boolean mCanBubble;
        private boolean mIsConversation;
        private ShortcutInfo mShortcutInfo;

        public Ranking() {
        }

        void copyFrom(Ranking other) {
            mKey = other.mKey;
            mRank = other.mRank;
            mIsAmbient = other.mIsAmbient;
            mVisibilityOverride = other.mVisibilityOverride;
            mSuppressedVisualEffects = other.mSuppressedVisualEffects;
            mMatchesInterruptionFilter = other.mMatchesInterruptionFilter;
            mImportance = other.mImportance;
            mImportanceExplanation = other.mImportanceExplanation;
            mOverrideGroupKey = other.mOverrideGroupKey;
            mChannel = other.mChannel;
            mUserSentiment = other.mUserSentiment;
            mSmartActions = other.mSmartActions;
            mSmartReplies = other.mSmartReplies;
            mShowBadge = other.mShowBadge;
            mSuspended = other.mSuspended;
            mLastAudiblyAlertedMillis = other.mLastAudiblyAlertedMillis;
            mCanBubble = other.mCanBubble;
            mIsConversation = other.mIsConversation;
            mShortcutInfo = other.mShortcutInfo;
        }

        public String getKey() { return mKey; }
        public int getRank() { return mRank; }
        public boolean isAmbient() { return mIsAmbient; }
        public int getLockscreenVisibilityOverride() { return mVisibilityOverride; }
        public int getSuppressedVisualEffects() { return mSuppressedVisualEffects; }
        public boolean matchesInterruptionFilter() { return mMatchesInterruptionFilter; }
        public int getImportance() { return mImportance; }
        public CharSequence getImportanceExplanation() { return mImportanceExplanation; }
        public String getOverrideGroupKey() { return mOverrideGroupKey; }
        public NotificationChannel getChannel() { return mChannel; }
        public int getUserSentiment() { return mUserSentiment; }
        public List<Notification.Action> getSmartActions() { return mSmartActions; }
        public List<CharSequence> getSmartReplies() { return mSmartReplies; }
        public boolean canShowBadge() { return mShowBadge; }
        public boolean isSuspended() { return mSuspended; }
        public long getLastAudiblyAlertedMillis() { return mLastAudiblyAlertedMillis; }
        public boolean canBubble() { return mCanBubble; }
        public boolean isConversation() { return mIsConversation; }
        public ShortcutInfo getConversationShortcutInfo() { return mShortcutInfo; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ranking)) return false;
            Ranking other = (Ranking) o;
            return mRank == other.mRank
                    && mIsAmbient == other.mIsAmbient
                    && mVisibilityOverride == other.mVisibilityOverride
                    && mSuppressedVisualEffects == other.mSuppressedVisualEffects
                    && mMatchesInterruptionFilter == other.mMatchesInterruptionFilter
                    && mImportance == other.mImportance
                    && mUserSentiment == other.mUserSentiment
                    && mShowBadge == other.mShowBadge
                    && mSuspended == other.mSuspended
                    && mLastAudiblyAlertedMillis == other.mLastAudiblyAlertedMillis
                    && mCanBubble == other.mCanBubble
                    && mIsConversation == other.mIsConversation
                    && Objects.equals(mKey, other.mKey);
        }
    }

    public static class RankingMap implements Parcelable {
        public static final Parcelable.Creator<RankingMap> CREATOR =
                new Parcelable.Creator<RankingMap>() {
                    @Override
                    public RankingMap createFromParcel(Parcel source) {
                        int count = source.readInt();
                        String[] keys = new String[Math.max(0, count)];
                        for (int i = 0; i < count; i++) {
                            keys[i] = source.readString();
                        }
                        return new RankingMap(keys);
                    }

                    @Override
                    public RankingMap[] newArray(int size) {
                        return new RankingMap[size];
                    }
                };

        private final String[] mOrderedKeys;
        private final Map<String, Ranking> mRankings = new HashMap<String, Ranking>();

        RankingMap(String[] orderedKeys) {
            mOrderedKeys = orderedKeys != null
                    ? Arrays.copyOf(orderedKeys, orderedKeys.length) : new String[0];
            for (int i = 0; i < mOrderedKeys.length; i++) {
                Ranking r = new Ranking();
                r.mKey = mOrderedKeys[i];
                r.mRank = i;
                mRankings.put(mOrderedKeys[i], r);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RankingMap)) return false;
            return Arrays.equals(mOrderedKeys, ((RankingMap) o).mOrderedKeys);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mOrderedKeys.length);
            for (int i = 0; i < mOrderedKeys.length; i++) {
                out.writeString(mOrderedKeys[i]);
            }
        }

        public String[] getOrderedKeys() {
            return Arrays.copyOf(mOrderedKeys, mOrderedKeys.length);
        }

        public boolean getRanking(String key, Ranking outRanking) {
            Ranking r = mRankings.get(key);
            if (r == null || outRanking == null) {
                return false;
            }
            outRanking.copyFrom(r);
            return true;
        }
    }
}
