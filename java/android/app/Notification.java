// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.LocusId;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Notification data consumed by the session-owned desktop notification manager. */
public class Notification implements Parcelable {
    public static final AudioAttributes AUDIO_ATTRIBUTES_DEFAULT =
            new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

    public static final int DEFAULT_ALL = -1;
    public static final int DEFAULT_SOUND = 1;
    public static final int DEFAULT_VIBRATE = 2;
    public static final int DEFAULT_LIGHTS = 4;

    public static final int STREAM_DEFAULT = -1;

    public static final int FLAG_SHOW_LIGHTS = 1;
    public static final int FLAG_ONGOING_EVENT = 2;
    public static final int FLAG_INSISTENT = 4;
    public static final int FLAG_ONLY_ALERT_ONCE = 8;
    public static final int FLAG_AUTO_CANCEL = 16;
    public static final int FLAG_NO_CLEAR = 32;
    public static final int FLAG_FOREGROUND_SERVICE = 64;
    @Deprecated public static final int FLAG_HIGH_PRIORITY = 128;
    public static final int FLAG_LOCAL_ONLY = 256;
    public static final int FLAG_GROUP_SUMMARY = 512;
    public static final int FLAG_BUBBLE = 4096;

    @Deprecated public static final int PRIORITY_DEFAULT = 0;
    @Deprecated public static final int PRIORITY_LOW = -1;
    @Deprecated public static final int PRIORITY_MIN = -2;
    @Deprecated public static final int PRIORITY_HIGH = 1;
    @Deprecated public static final int PRIORITY_MAX = 2;

    public static final int COLOR_DEFAULT = 0;

    public static final int VISIBILITY_PUBLIC = 1;
    public static final int VISIBILITY_PRIVATE = 0;
    public static final int VISIBILITY_SECRET = -1;

    public static final int BADGE_ICON_NONE = 0;
    public static final int BADGE_ICON_SMALL = 1;
    public static final int BADGE_ICON_LARGE = 2;

    public static final int GROUP_ALERT_ALL = 0;
    public static final int GROUP_ALERT_SUMMARY = 1;
    public static final int GROUP_ALERT_CHILDREN = 2;

    public static final int FOREGROUND_SERVICE_DEFAULT = 0;
    public static final int FOREGROUND_SERVICE_IMMEDIATE = 1;
    public static final int FOREGROUND_SERVICE_DEFERRED = 2;

    public static final String CATEGORY_CALL = "call";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_MESSAGE = "msg";
    public static final String CATEGORY_EMAIL = "email";
    public static final String CATEGORY_EVENT = "event";
    public static final String CATEGORY_PROMO = "promo";
    public static final String CATEGORY_ALARM = "alarm";
    public static final String CATEGORY_PROGRESS = "progress";
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_ERR = "err";
    public static final String CATEGORY_ERROR = "err";
    public static final String CATEGORY_TRANSPORT = "transport";
    public static final String CATEGORY_SYSTEM = "sys";
    public static final String CATEGORY_SERVICE = "service";
    public static final String CATEGORY_RECOMMENDATION = "recommendation";
    public static final String CATEGORY_STATUS = "status";
    public static final String CATEGORY_REMINDER = "reminder";
    public static final String CATEGORY_LOCATION_SHARING = "location_sharing";
    public static final String CATEGORY_STOPWATCH = "stopwatch";
    public static final String CATEGORY_WORKOUT = "workout";
    public static final String CATEGORY_MISSED_CALL = "missed_call";
    public static final String CATEGORY_VOICEMAIL = "voicemail";

    public static final String INTENT_CATEGORY_NOTIFICATION_PREFERENCES =
            "android.intent.category.NOTIFICATION_PREFERENCES";

    public static final String EXTRA_TITLE = "android.title";
    public static final String EXTRA_TITLE_BIG = "android.title.big";
    public static final String EXTRA_TEXT = "android.text";
    public static final String EXTRA_SUB_TEXT = "android.subText";
    public static final String EXTRA_REMOTE_INPUT_HISTORY = "android.remoteInputHistory";
    public static final String EXTRA_REMOTE_INPUT_DRAFT = "android.remoteInputDraft";
    public static final String EXTRA_INFO_TEXT = "android.infoText";
    public static final String EXTRA_SUMMARY_TEXT = "android.summaryText";
    public static final String EXTRA_BIG_TEXT = "android.bigText";
    @Deprecated public static final String EXTRA_SMALL_ICON = "android.icon";
    @Deprecated public static final String EXTRA_LARGE_ICON = "android.largeIcon";
    public static final String EXTRA_LARGE_ICON_BIG = "android.largeIcon.big";
    public static final String EXTRA_PROGRESS = "android.progress";
    public static final String EXTRA_PROGRESS_MAX = "android.progressMax";
    public static final String EXTRA_PROGRESS_INDETERMINATE = "android.progressIndeterminate";
    public static final String EXTRA_SHOW_CHRONOMETER = "android.showChronometer";
    public static final String EXTRA_CHRONOMETER_COUNT_DOWN = "android.chronometerCountDown";
    public static final String EXTRA_SHOW_WHEN = "android.showWhen";
    public static final String EXTRA_PICTURE = "android.picture";
    public static final String EXTRA_PICTURE_ICON = "android.pictureIcon";
    public static final String EXTRA_PICTURE_CONTENT_DESCRIPTION = "android.pictureContentDescription";
    public static final String EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED =
            "android.showBigPictureWhenCollapsed";
    public static final String EXTRA_TEXT_LINES = "android.textLines";
    public static final String EXTRA_TEMPLATE = "android.template";
    @Deprecated public static final String EXTRA_PEOPLE = "android.people";
    public static final String EXTRA_PEOPLE_LIST = "android.people.list";
    public static final String EXTRA_BACKGROUND_IMAGE_URI = "android.backgroundImageUri";
    public static final String EXTRA_MEDIA_SESSION = "android.mediaSession";
    public static final String EXTRA_COMPACT_ACTIONS = "android.compactActions";
    @Deprecated public static final String EXTRA_SELF_DISPLAY_NAME = "android.selfDisplayName";
    public static final String EXTRA_MESSAGING_PERSON = "android.messagingUser";
    public static final String EXTRA_CONVERSATION_TITLE = "android.conversationTitle";
    public static final String EXTRA_MESSAGES = "android.messages";
    public static final String EXTRA_HISTORIC_MESSAGES = "android.messages.historic";
    public static final String EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation";
    public static final String EXTRA_COLORIZED = "android.colorized";
    public static final String EXTRA_AUDIO_CONTENTS_URI = "android.audioContents";
    public static final String EXTRA_CHANNEL_ID = "android.intent.extra.CHANNEL_ID";
    public static final String EXTRA_CHANNEL_GROUP_ID = "android.intent.extra.CHANNEL_GROUP_ID";
    public static final String EXTRA_NOTIFICATION_TAG = "android.intent.extra.NOTIFICATION_TAG";
    public static final String EXTRA_NOTIFICATION_ID = "android.intent.extra.NOTIFICATION_ID";
    public static final String EXTRA_ANSWER_INTENT = "android.answerIntent";
    public static final String EXTRA_DECLINE_INTENT = "android.declineIntent";
    public static final String EXTRA_HANG_UP_INTENT = "android.hangUpIntent";
    public static final String EXTRA_CALL_IS_VIDEO = "android.callIsVideo";
    public static final String EXTRA_CALL_PERSON = "android.callPerson";
    public static final String EXTRA_CALL_TYPE = "android.callType";
    public static final String EXTRA_VERIFICATION_ICON = "android.verificationIcon";
    public static final String EXTRA_VERIFICATION_TEXT = "android.verificationText";
    public static final String EXTRA_ANSWER_COLOR = "android.answerColor";
    public static final String EXTRA_DECLINE_COLOR = "android.declineColor";

    public long when = System.currentTimeMillis();
    @Deprecated public int icon;
    public int iconLevel;
    public int number;
    public PendingIntent contentIntent;
    public PendingIntent deleteIntent;
    public PendingIntent fullScreenIntent;
    public CharSequence tickerText;
    @Deprecated public RemoteViews tickerView;
    @Deprecated public RemoteViews contentView;
    @Deprecated public RemoteViews bigContentView;
    @Deprecated public RemoteViews headsUpContentView;
    @Deprecated public Bitmap largeIcon;
    @Deprecated public Uri sound;
    @Deprecated public int audioStreamType = STREAM_DEFAULT;
    @Deprecated public AudioAttributes audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
    @Deprecated public long[] vibrate;
    @Deprecated public int ledARGB;
    @Deprecated public int ledOnMS;
    @Deprecated public int ledOffMS;
    @Deprecated public int defaults;
    public int flags;
    @Deprecated public int priority;
    public int color = COLOR_DEFAULT;
    public int visibility;
    public String category;
    public Bundle extras = new Bundle();
    public Action[] actions;
    public Notification publicVersion;

    private String channelId;
    private String group;
    private String sortKey;
    private String shortcutId;
    private LocusId locusId;
    private CharSequence settingsText;
    private int badgeIcon = BADGE_ICON_NONE;
    private int groupAlertBehavior = GROUP_ALERT_ALL;
    private BubbleMetadata bubbleMetadata;
    private boolean allowSystemGeneratedContextualActions = true;
    private long timeout;
    private Icon smallIcon;
    private Icon largeIconObj;

    public Notification() {}

    @Deprecated
    public Notification(int icon, CharSequence ticker, long when) {
        this.icon = icon;
        this.tickerText = ticker;
        this.when = when;
    }

    public Notification(Parcel in) {
        when = in.readLong();
        icon = in.readInt();
        iconLevel = in.readInt();
        flags = in.readInt();
        defaults = in.readInt();
        number = in.readInt();
        priority = in.readInt();
        color = in.readInt();
        visibility = in.readInt();
        channelId = in.readString();
        group = in.readString();
        sortKey = in.readString();
        category = in.readString();
        shortcutId = in.readString();
        badgeIcon = in.readInt();
        groupAlertBehavior = in.readInt();
        allowSystemGeneratedContextualActions = in.readInt() != 0;
        timeout = in.readLong();
        tickerText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        settingsText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        Bundle b = in.readBundle(Notification.class.getClassLoader());
        extras = b == null ? new Bundle() : b;
    }

    public String getChannelId() { return channelId; }
    public String getGroup() { return group; }
    public String getSortKey() { return sortKey; }
    public String getCategory() { return category; }
    public long getTimeoutAfter() { return timeout; }
    public int getBadgeIconType() { return badgeIcon; }
    public String getShortcutId() { return shortcutId; }
    public LocusId getLocusId() { return locusId; }
    public CharSequence getSettingsText() { return settingsText; }
    public int getGroupAlertBehavior() { return groupAlertBehavior; }
    public BubbleMetadata getBubbleMetadata() { return bubbleMetadata; }
    public boolean getAllowSystemGeneratedContextualActions() {
        return allowSystemGeneratedContextualActions;
    }
    public Icon getSmallIcon() { return smallIcon; }
    public Icon getLargeIcon() { return largeIconObj; }

    public Pair<RemoteInput, Action> findRemoteInputActionPair(boolean requiresFreeform) {
        if (actions == null) return null;
        for (Action action : actions) {
            if (action == null) continue;
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null) continue;
            for (RemoteInput input : inputs) {
                if (input != null && (!requiresFreeform || input.getAllowFreeFormInput())) {
                    return Pair.create(input, action);
                }
            }
        }
        return null;
    }

    public List<Action> getContextualActions() {
        if (actions == null) return Collections.emptyList();
        List<Action> list = new ArrayList<Action>();
        for (Action action : actions) {
            if (action != null && action.isContextual()) {
                list.add(action);
            }
        }
        return list;
    }

    public boolean hasImage() {
        return largeIcon != null
                || largeIconObj != null
                || (extras != null
                        && (extras.containsKey(EXTRA_PICTURE)
                                || extras.containsKey(EXTRA_PICTURE_ICON)
                                || extras.containsKey(EXTRA_LARGE_ICON_BIG)));
    }

    @Override
    public Notification clone() {
        Notification n = new Notification();
        n.when = when;
        n.icon = icon;
        n.iconLevel = iconLevel;
        n.number = number;
        n.contentIntent = contentIntent;
        n.deleteIntent = deleteIntent;
        n.fullScreenIntent = fullScreenIntent;
        n.tickerText = tickerText;
        n.tickerView = tickerView;
        n.contentView = contentView;
        n.bigContentView = bigContentView;
        n.headsUpContentView = headsUpContentView;
        n.largeIcon = largeIcon;
        n.sound = sound;
        n.audioStreamType = audioStreamType;
        n.audioAttributes = audioAttributes;
        n.vibrate = vibrate == null ? null : vibrate.clone();
        n.ledARGB = ledARGB;
        n.ledOnMS = ledOnMS;
        n.ledOffMS = ledOffMS;
        n.defaults = defaults;
        n.flags = flags;
        n.priority = priority;
        n.color = color;
        n.visibility = visibility;
        n.category = category;
        n.extras = extras == null ? new Bundle() : new Bundle(extras);
        if (actions != null) {
            n.actions = new Action[actions.length];
            for (int i = 0; i < actions.length; i++) {
                n.actions[i] = actions[i] == null ? null : actions[i].clone();
            }
        }
        n.publicVersion = publicVersion == null ? null : publicVersion.clone();
        n.channelId = channelId;
        n.group = group;
        n.sortKey = sortKey;
        n.shortcutId = shortcutId;
        n.locusId = locusId;
        n.settingsText = settingsText;
        n.badgeIcon = badgeIcon;
        n.groupAlertBehavior = groupAlertBehavior;
        n.bubbleMetadata = bubbleMetadata;
        n.allowSystemGeneratedContextualActions = allowSystemGeneratedContextualActions;
        n.timeout = timeout;
        n.smallIcon = smallIcon;
        n.largeIconObj = largeIconObj;
        return n;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int parcelFlags) {
        out.writeLong(when);
        out.writeInt(icon);
        out.writeInt(iconLevel);
        out.writeInt(this.flags);
        out.writeInt(defaults);
        out.writeInt(number);
        out.writeInt(priority);
        out.writeInt(color);
        out.writeInt(visibility);
        out.writeString(channelId);
        out.writeString(group);
        out.writeString(sortKey);
        out.writeString(category);
        out.writeString(shortcutId);
        out.writeInt(badgeIcon);
        out.writeInt(groupAlertBehavior);
        out.writeInt(allowSystemGeneratedContextualActions ? 1 : 0);
        out.writeLong(timeout);
        TextUtils.writeToParcel(tickerText, out, parcelFlags);
        TextUtils.writeToParcel(settingsText, out, parcelFlags);
        out.writeBundle(extras);
    }

    @Override
    public String toString() {
        return "Notification(channel=" + channelId + " shortcut=" + shortcutId
                + " flags=0x" + Integer.toHexString(flags) + ")";
    }

    public static final Parcelable.Creator<Notification> CREATOR =
            new Parcelable.Creator<Notification>() {
                @Override
                public Notification createFromParcel(Parcel in) {
                    return new Notification(in);
                }

                @Override
                public Notification[] newArray(int size) {
                    return new Notification[size];
                }
            };

    public interface Extender {
        Builder extend(Builder builder);
    }

    public static class Action implements Parcelable {
        public static final int SEMANTIC_ACTION_NONE = 0;
        public static final int SEMANTIC_ACTION_REPLY = 1;
        public static final int SEMANTIC_ACTION_MARK_AS_READ = 2;
        public static final int SEMANTIC_ACTION_MARK_AS_UNREAD = 3;
        public static final int SEMANTIC_ACTION_DELETE = 4;
        public static final int SEMANTIC_ACTION_ARCHIVE = 5;
        public static final int SEMANTIC_ACTION_MUTE = 6;
        public static final int SEMANTIC_ACTION_UNMUTE = 7;
        public static final int SEMANTIC_ACTION_THUMBS_UP = 8;
        public static final int SEMANTIC_ACTION_THUMBS_DOWN = 9;
        public static final int SEMANTIC_ACTION_CALL = 10;

        @Deprecated public int icon;
        public CharSequence title;
        public PendingIntent actionIntent;
        public Bundle extras = new Bundle();
        public boolean allowGeneratedReplies = true;

        private Icon actionIcon;
        private RemoteInput[] remoteInputs;
        private RemoteInput[] dataOnlyRemoteInputs;
        private int semanticAction = SEMANTIC_ACTION_NONE;
        private boolean contextual;
        private boolean authenticationRequired;

        @Deprecated
        public Action(int icon, CharSequence title, PendingIntent intent) {
            this.icon = icon;
            this.title = title;
            this.actionIntent = intent;
            if (icon != 0) actionIcon = Icon.createWithResource("", icon);
        }

        public Action(Icon icon, CharSequence title, PendingIntent intent) {
            this.actionIcon = icon;
            this.icon = icon == null ? 0 : (icon.getType() == Icon.TYPE_RESOURCE ? icon.getResId() : 0);
            this.title = title;
            this.actionIntent = intent;
        }

        private Action(Parcel in) {
            icon = in.readInt();
            title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            actionIntent = PendingIntent.readPendingIntentOrNullFromParcel(in);
            Bundle b = in.readBundle(Action.class.getClassLoader());
            extras = b == null ? new Bundle() : b;
            allowGeneratedReplies = in.readInt() != 0;
            semanticAction = in.readInt();
            contextual = in.readInt() != 0;
            authenticationRequired = in.readInt() != 0;
            if (icon != 0) actionIcon = Icon.createWithResource("", icon);
        }

        public Icon getIcon() { return actionIcon; }
        public CharSequence getTitle() { return title; }
        public PendingIntent getActionIntent() { return actionIntent; }
        public Bundle getExtras() { return extras; }
        public boolean getAllowGeneratedReplies() { return allowGeneratedReplies; }
        public RemoteInput[] getRemoteInputs() { return remoteInputs; }
        public RemoteInput[] getDataOnlyRemoteInputs() { return dataOnlyRemoteInputs; }
        public int getSemanticAction() { return semanticAction; }
        public boolean isContextual() { return contextual; }
        public boolean isAuthenticationRequired() { return authenticationRequired; }

        @Override
        public Action clone() {
            Action copy = new Action(actionIcon, title, actionIntent);
            copy.icon = icon;
            copy.extras = extras == null ? new Bundle() : new Bundle(extras);
            copy.allowGeneratedReplies = allowGeneratedReplies;
            copy.remoteInputs = remoteInputs == null ? null : remoteInputs.clone();
            copy.dataOnlyRemoteInputs = dataOnlyRemoteInputs == null ? null : dataOnlyRemoteInputs.clone();
            copy.semanticAction = semanticAction;
            copy.contextual = contextual;
            copy.authenticationRequired = authenticationRequired;
            return copy;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(icon);
            TextUtils.writeToParcel(title, out, flags);
            PendingIntent.writePendingIntentOrNullToParcel(actionIntent, out);
            out.writeBundle(extras);
            out.writeInt(allowGeneratedReplies ? 1 : 0);
            out.writeInt(semanticAction);
            out.writeInt(contextual ? 1 : 0);
            out.writeInt(authenticationRequired ? 1 : 0);
        }

        public static final Parcelable.Creator<Action> CREATOR =
                new Parcelable.Creator<Action>() {
                    @Override
                    public Action createFromParcel(Parcel in) {
                        return new Action(in);
                    }

                    @Override
                    public Action[] newArray(int size) {
                        return new Action[size];
                    }
                };

        public interface Extender {
            Builder extend(Builder builder);
        }

        public static final class WearableExtender implements Extender {
            private static final int FLAG_AVAILABLE_OFFLINE = 1;
            private static final int FLAG_HINT_LAUNCHES_ACTIVITY = 2;
            private static final int FLAG_HINT_DISPLAY_INLINE = 4;

            private int flags = FLAG_AVAILABLE_OFFLINE;
            private CharSequence inProgressLabel;
            private CharSequence confirmLabel;
            private CharSequence cancelLabel;

            public WearableExtender() {}

            public WearableExtender(Action action) {
                if (action != null && action.extras != null) {
                    Bundle wearable = action.extras.getBundle("android.wearable.EXTENSIONS");
                    if (wearable != null) {
                        flags = wearable.getInt("flags", FLAG_AVAILABLE_OFFLINE);
                        inProgressLabel = wearable.getCharSequence("inProgressLabel");
                        confirmLabel = wearable.getCharSequence("confirmLabel");
                        cancelLabel = wearable.getCharSequence("cancelLabel");
                    }
                }
            }

            @Override
            public Builder extend(Builder builder) {
                Bundle wearable = new Bundle();
                wearable.putInt("flags", flags);
                if (inProgressLabel != null) wearable.putCharSequence("inProgressLabel", inProgressLabel);
                if (confirmLabel != null) wearable.putCharSequence("confirmLabel", confirmLabel);
                if (cancelLabel != null) wearable.putCharSequence("cancelLabel", cancelLabel);
                builder.getExtras().putBundle("android.wearable.EXTENSIONS", wearable);
                return builder;
            }

            @Override
            public WearableExtender clone() {
                WearableExtender copy = new WearableExtender();
                copy.flags = flags;
                copy.inProgressLabel = inProgressLabel;
                copy.confirmLabel = confirmLabel;
                copy.cancelLabel = cancelLabel;
                return copy;
            }

            private void setFlag(int mask, boolean value) {
                flags = value ? (flags | mask) : (flags & ~mask);
            }

            public WearableExtender setAvailableOffline(boolean availableOffline) {
                setFlag(FLAG_AVAILABLE_OFFLINE, availableOffline);
                return this;
            }

            public boolean isAvailableOffline() {
                return (flags & FLAG_AVAILABLE_OFFLINE) != 0;
            }

            @Deprecated
            public WearableExtender setInProgressLabel(CharSequence label) {
                inProgressLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getInProgressLabel() {
                return inProgressLabel;
            }

            @Deprecated
            public WearableExtender setConfirmLabel(CharSequence label) {
                confirmLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getConfirmLabel() {
                return confirmLabel;
            }

            @Deprecated
            public WearableExtender setCancelLabel(CharSequence label) {
                cancelLabel = label;
                return this;
            }

            @Deprecated
            public CharSequence getCancelLabel() {
                return cancelLabel;
            }

            public WearableExtender setHintLaunchesActivity(boolean hintLaunchesActivity) {
                setFlag(FLAG_HINT_LAUNCHES_ACTIVITY, hintLaunchesActivity);
                return this;
            }

            public boolean getHintLaunchesActivity() {
                return (flags & FLAG_HINT_LAUNCHES_ACTIVITY) != 0;
            }

            public WearableExtender setHintDisplayActionInline(boolean hintDisplayInline) {
                setFlag(FLAG_HINT_DISPLAY_INLINE, hintDisplayInline);
                return this;
            }

            public boolean getHintDisplayActionInline() {
                return (flags & FLAG_HINT_DISPLAY_INLINE) != 0;
            }
        }

        public static final class Builder {
            private final Action action;
            private ArrayList<RemoteInput> remoteInputs;

            @Deprecated
            public Builder(int icon, CharSequence title, PendingIntent intent) {
                action = new Action(icon, title, intent);
            }

            public Builder(Icon icon, CharSequence title, PendingIntent intent) {
                action = new Action(icon, title, intent);
            }

            public Builder(Action a) {
                if (a == null) throw new NullPointerException("action");
                action = a.clone();
                if (a.remoteInputs != null) {
                    remoteInputs = new ArrayList<RemoteInput>();
                    Collections.addAll(remoteInputs, a.remoteInputs);
                }
            }

            public Builder addExtras(Bundle extras) {
                if (extras != null) action.extras.putAll(extras);
                return this;
            }

            public Bundle getExtras() {
                return action.extras;
            }

            public Builder addRemoteInput(RemoteInput remoteInput) {
                if (remoteInputs == null) remoteInputs = new ArrayList<RemoteInput>();
                remoteInputs.add(remoteInput);
                return this;
            }

            public Builder setAllowGeneratedReplies(boolean allowGeneratedReplies) {
                action.allowGeneratedReplies = allowGeneratedReplies;
                return this;
            }

            public Builder setSemanticAction(int semanticAction) {
                action.semanticAction = semanticAction;
                return this;
            }

            public Builder setContextual(boolean isContextual) {
                action.contextual = isContextual;
                return this;
            }

            public Builder extend(Extender extender) {
                extender.extend(this);
                return this;
            }

            public Builder setAuthenticationRequired(boolean authenticationRequired) {
                action.authenticationRequired = authenticationRequired;
                return this;
            }

            public Action build() {
                if (remoteInputs != null) {
                    List<RemoteInput> dataOnly = new ArrayList<RemoteInput>();
                    List<RemoteInput> textInputs = new ArrayList<RemoteInput>();
                    for (RemoteInput input : remoteInputs) {
                        if (input != null && input.isDataOnly()) {
                            dataOnly.add(input);
                        } else if (input != null) {
                            textInputs.add(input);
                        }
                    }
                    action.dataOnlyRemoteInputs = dataOnly.isEmpty()
                            ? null
                            : dataOnly.toArray(new RemoteInput[dataOnly.size()]);
                    action.remoteInputs = textInputs.isEmpty()
                            ? null
                            : textInputs.toArray(new RemoteInput[textInputs.size()]);
                }
                return action.clone();
            }
        }
    }

    public static final class BubbleMetadata implements Parcelable {
        private final PendingIntent intent;
        private final PendingIntent deleteIntent;
        private final Icon icon;
        private final int desiredHeight;
        private final int desiredHeightResId;
        private final int flags;
        private final String shortcutId;

        private static final int FLAG_AUTO_EXPAND_BUBBLE = 1;
        private static final int FLAG_SUPPRESS_NOTIFICATION = 2;
        private static final int FLAG_SUPPRESSABLE_BUBBLE = 4;
        private static final int FLAG_SUPPRESSED_BUBBLE = 8;

        private BubbleMetadata(
                PendingIntent expandIntent,
                PendingIntent deleteIntent,
                Icon icon,
                int height,
                int heightResId,
                int flags,
                String shortcutId) {
            this.intent = expandIntent;
            this.deleteIntent = deleteIntent;
            this.icon = icon;
            this.desiredHeight = height;
            this.desiredHeightResId = heightResId;
            this.flags = flags;
            this.shortcutId = shortcutId;
        }

        private BubbleMetadata(Parcel in) {
            intent = PendingIntent.readPendingIntentOrNullFromParcel(in);
            icon = in.readInt() != 0 ? Icon.CREATOR.createFromParcel(in) : null;
            desiredHeight = in.readInt();
            desiredHeightResId = in.readInt();
            flags = in.readInt();
            deleteIntent = PendingIntent.readPendingIntentOrNullFromParcel(in);
            shortcutId = in.readString();
        }

        public String getShortcutId() { return shortcutId; }
        public PendingIntent getIntent() { return intent; }
        public PendingIntent getDeleteIntent() { return deleteIntent; }
        public Icon getIcon() { return icon; }
        public int getDesiredHeight() { return desiredHeight; }
        public int getDesiredHeightResId() { return desiredHeightResId; }
        public boolean getAutoExpandBubble() { return (flags & FLAG_AUTO_EXPAND_BUBBLE) != 0; }
        public boolean isNotificationSuppressed() { return (flags & FLAG_SUPPRESS_NOTIFICATION) != 0; }
        public boolean isBubbleSuppressable() { return (flags & FLAG_SUPPRESSABLE_BUBBLE) != 0; }
        public boolean isBubbleSuppressed() { return (flags & FLAG_SUPPRESSED_BUBBLE) != 0; }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int parcelFlags) {
            PendingIntent.writePendingIntentOrNullToParcel(intent, out);
            if (icon != null) {
                out.writeInt(1);
                icon.writeToParcel(out, 0);
            } else {
                out.writeInt(0);
            }
            out.writeInt(desiredHeight);
            out.writeInt(desiredHeightResId);
            out.writeInt(flags);
            PendingIntent.writePendingIntentOrNullToParcel(deleteIntent, out);
            out.writeString(shortcutId);
        }

        public static final Parcelable.Creator<BubbleMetadata> CREATOR =
                new Parcelable.Creator<BubbleMetadata>() {
                    @Override
                    public BubbleMetadata createFromParcel(Parcel in) {
                        return new BubbleMetadata(in);
                    }

                    @Override
                    public BubbleMetadata[] newArray(int size) {
                        return new BubbleMetadata[size];
                    }
                };

        public static final class Builder {
            private PendingIntent pendingIntent;
            private Icon icon;
            private int desiredHeight;
            private int desiredHeightResId;
            private int flags;
            private PendingIntent deleteIntent;
            private String shortcutId;

            @Deprecated
            public Builder() {}

            public Builder(String shortcutId) {
                if (TextUtils.isEmpty(shortcutId)) {
                    throw new NullPointerException("Bubble requires a non-null shortcut id");
                }
                this.shortcutId = shortcutId;
            }

            public Builder(PendingIntent intent, Icon icon) {
                this.pendingIntent = Objects.requireNonNull(intent, "Bubble requires non-null pending intent");
                this.icon = Objects.requireNonNull(icon, "Bubbles require non-null icon");
            }

            public Builder setIntent(PendingIntent intent) {
                if (shortcutId != null) {
                    throw new IllegalStateException(
                            "Created as a shortcut bubble, cannot set a PendingIntent");
                }
                this.pendingIntent = Objects.requireNonNull(intent, "Bubble requires non-null pending intent");
                return this;
            }

            public Builder setIcon(Icon icon) {
                if (shortcutId != null) {
                    throw new IllegalStateException("Created as a shortcut bubble, cannot set an Icon");
                }
                this.icon = Objects.requireNonNull(icon, "Bubbles require non-null icon");
                return this;
            }

            public Builder setDesiredHeight(int height) {
                desiredHeight = Math.max(height, 0);
                desiredHeightResId = 0;
                return this;
            }

            public Builder setDesiredHeightResId(int heightResId) {
                desiredHeightResId = heightResId;
                desiredHeight = 0;
                return this;
            }

            public Builder setAutoExpandBubble(boolean shouldExpand) {
                setFlag(FLAG_AUTO_EXPAND_BUBBLE, shouldExpand);
                return this;
            }

            public Builder setSuppressNotification(boolean shouldSuppressNotif) {
                setFlag(FLAG_SUPPRESS_NOTIFICATION, shouldSuppressNotif);
                return this;
            }

            public Builder setSuppressableBubble(boolean suppressable) {
                setFlag(FLAG_SUPPRESSABLE_BUBBLE, suppressable);
                return this;
            }

            public Builder setDeleteIntent(PendingIntent deleteIntent) {
                this.deleteIntent = deleteIntent;
                return this;
            }

            private void setFlag(int mask, boolean value) {
                flags = value ? (flags | mask) : (flags & ~mask);
            }

            public BubbleMetadata build() {
                if (shortcutId == null && pendingIntent == null) {
                    throw new NullPointerException("Must supply pending intent or shortcut to bubble");
                }
                if (shortcutId == null && icon == null) {
                    throw new NullPointerException("Must supply an icon or shortcut for the bubble");
                }
                return new BubbleMetadata(
                        pendingIntent,
                        deleteIntent,
                        icon,
                        desiredHeight,
                        desiredHeightResId,
                        flags,
                        shortcutId);
            }
        }
    }

    public static class Builder {
        private final Context context;
        private final Notification n = new Notification();
        private Bundle suppliedExtras = new Bundle();
        private final ArrayList<Action> builtActions = new ArrayList<Action>();
        private final ArrayList<Person> people = new ArrayList<Person>();
        private Style style;

        @Deprecated
        public Builder(Context context) {
            if (context == null) throw new NullPointerException("context");
            this.context = context;
        }

        public Builder(Context context, String channelId) {
            this(context);
            n.channelId = channelId;
        }

        public static Builder recoverBuilder(Context context, Notification n) {
            Builder b = new Builder(context, n != null ? n.channelId : null);
            if (n != null) {
                Notification copy = n.clone();
                b.n.when = copy.when;
                b.n.icon = copy.icon;
                b.n.iconLevel = copy.iconLevel;
                b.n.number = copy.number;
                b.n.contentIntent = copy.contentIntent;
                b.n.deleteIntent = copy.deleteIntent;
                b.n.tickerText = copy.tickerText;
                b.n.largeIcon = copy.largeIcon;
                b.n.sound = copy.sound;
                b.n.audioStreamType = copy.audioStreamType;
                b.n.audioAttributes = copy.audioAttributes;
                b.n.vibrate = copy.vibrate;
                b.n.ledARGB = copy.ledARGB;
                b.n.ledOnMS = copy.ledOnMS;
                b.n.ledOffMS = copy.ledOffMS;
                b.n.defaults = copy.defaults;
                b.n.flags = copy.flags;
                b.n.priority = copy.priority;
                b.n.color = copy.color;
                b.n.visibility = copy.visibility;
                b.n.category = copy.category;
                b.n.publicVersion = copy.publicVersion;
                b.n.group = copy.group;
                b.n.sortKey = copy.sortKey;
                b.n.shortcutId = copy.shortcutId;
                b.n.locusId = copy.locusId;
                b.n.settingsText = copy.settingsText;
                b.n.badgeIcon = copy.badgeIcon;
                b.n.groupAlertBehavior = copy.groupAlertBehavior;
                b.n.bubbleMetadata = copy.bubbleMetadata;
                b.n.allowSystemGeneratedContextualActions = copy.allowSystemGeneratedContextualActions;
                b.n.timeout = copy.timeout;
                b.n.smallIcon = copy.smallIcon;
                b.n.largeIconObj = copy.largeIconObj;
                b.suppliedExtras = new Bundle(copy.extras);
                if (copy.actions != null) {
                    Collections.addAll(b.builtActions, copy.actions);
                }
            }
            return b;
        }

        private Builder flag(int flag, boolean set) {
            n.flags = set ? (n.flags | flag) : (n.flags & ~flag);
            return this;
        }

        Builder text(String key, CharSequence value) {
            if (value != null && value.length() > 8192) {
                throw new IllegalArgumentException("Notification text limit");
            }
            n.extras.putCharSequence(key, value == null ? null : value.toString());
            return this;
        }

        public Builder setShortcutId(String shortcutId) {
            n.shortcutId = shortcutId;
            n.extras.putString("shiraz.shortcut", shortcutId);
            return this;
        }

        public Builder setLocusId(LocusId locusId) {
            n.locusId = locusId;
            return this;
        }

        public Builder setBadgeIconType(int icon) {
            n.badgeIcon = icon;
            n.extras.putInt("shiraz.badge", icon);
            return this;
        }

        public Builder setGroupAlertBehavior(int groupAlertBehavior) {
            n.groupAlertBehavior = groupAlertBehavior;
            n.extras.putInt("shiraz.groupAlert", groupAlertBehavior);
            return this;
        }

        public Builder setBubbleMetadata(BubbleMetadata data) {
            n.bubbleMetadata = data;
            return this;
        }

        public Builder setChannelId(String channelId) {
            n.channelId = channelId;
            return this;
        }

        public Builder setTimeoutAfter(long durationMs) {
            if (durationMs < 0) throw new IllegalArgumentException("timeout");
            n.timeout = durationMs;
            return this;
        }

        public Builder setWhen(long when) {
            n.when = when;
            return this;
        }

        public Builder setShowWhen(boolean show) {
            n.extras.putBoolean(EXTRA_SHOW_WHEN, show);
            return this;
        }

        public Builder setUsesChronometer(boolean b) {
            n.extras.putBoolean(EXTRA_SHOW_CHRONOMETER, b);
            return this;
        }

        public Builder setChronometerCountDown(boolean isCountDown) {
            n.extras.putBoolean(EXTRA_CHRONOMETER_COUNT_DOWN, isCountDown);
            return this;
        }

        public Builder setSmallIcon(int icon) {
            return setSmallIcon(icon, 0);
        }

        public Builder setSmallIcon(int icon, int level) {
            n.icon = icon;
            n.iconLevel = level;
            return this;
        }

        public Builder setSmallIcon(Icon icon) {
            n.smallIcon = icon;
            return this;
        }

        public Builder setContentTitle(CharSequence title) {
            return text(EXTRA_TITLE, title);
        }

        public Builder setContentText(CharSequence text) {
            return text(EXTRA_TEXT, text);
        }

        public Builder setSubText(CharSequence text) {
            return text(EXTRA_SUB_TEXT, text);
        }

        public Builder setSettingsText(CharSequence text) {
            n.settingsText = text;
            return text("android.settingsText", text);
        }

        public Builder setRemoteInputHistory(CharSequence[] text) {
            if (text != null && text.length != 0) {
                throw new UnsupportedOperationException("Notification remote input");
            }
            return this;
        }

        public Builder setNumber(int number) {
            n.number = number;
            return this;
        }

        @Deprecated
        public Builder setContentInfo(CharSequence info) {
            return text(EXTRA_INFO_TEXT, info);
        }

        public Builder setProgress(int max, int progress, boolean indeterminate) {
            if (max < 0 || progress < 0) {
                throw new IllegalArgumentException("Notification progress");
            }
            n.extras.putInt(EXTRA_PROGRESS_MAX, max);
            n.extras.putInt(EXTRA_PROGRESS, progress);
            n.extras.putBoolean(EXTRA_PROGRESS_INDETERMINATE, indeterminate);
            return this;
        }

        private static void custom(RemoteViews v) {
            if (v != null) throw new UnsupportedOperationException("Custom notification views");
        }

        @Deprecated
        public Builder setContent(RemoteViews views) {
            custom(views);
            return this;
        }

        public Builder setCustomContentView(RemoteViews contentView) {
            custom(contentView);
            return this;
        }

        public Builder setCustomBigContentView(RemoteViews contentView) {
            custom(contentView);
            return this;
        }

        public Builder setCustomHeadsUpContentView(RemoteViews contentView) {
            custom(contentView);
            return this;
        }

        public Builder setContentIntent(PendingIntent intent) {
            n.contentIntent = intent;
            return this;
        }

        public Builder setDeleteIntent(PendingIntent intent) {
            n.deleteIntent = intent;
            return this;
        }

        public Builder setFullScreenIntent(PendingIntent intent, boolean highPriority) {
            if (intent != null) {
                throw new UnsupportedOperationException("Full-screen notifications");
            }
            return this;
        }

        public Builder setTicker(CharSequence tickerText) {
            n.tickerText = tickerText;
            return this;
        }

        @Deprecated
        public Builder setTicker(CharSequence tickerText, RemoteViews views) {
            custom(views);
            return setTicker(tickerText);
        }

        public Builder setLargeIcon(Bitmap b) {
            n.largeIcon = b;
            n.largeIconObj = b == null ? null : Icon.createWithBitmap(b);
            return this;
        }

        public Builder setLargeIcon(Icon icon) {
            n.largeIconObj = icon;
            n.largeIcon = null;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound) {
            n.sound = sound;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, int streamType) {
            n.sound = sound;
            n.audioStreamType = streamType;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, AudioAttributes audioAttributes) {
            n.sound = sound;
            n.audioAttributes = audioAttributes;
            return this;
        }

        @Deprecated
        public Builder setVibrate(long[] pattern) {
            if (pattern != null && pattern.length > 256) {
                throw new IllegalArgumentException("Vibration limit");
            }
            n.vibrate = pattern == null ? null : pattern.clone();
            return this;
        }

        @Deprecated
        public Builder setLights(int argb, int onMs, int offMs) {
            n.ledARGB = argb;
            n.ledOnMS = onMs;
            n.ledOffMS = offMs;
            return this;
        }

        public Builder setOngoing(boolean ongoing) {
            return flag(FLAG_ONGOING_EVENT, ongoing);
        }

        public Builder setColorized(boolean colorize) {
            n.extras.putBoolean(EXTRA_COLORIZED, colorize);
            n.extras.putBoolean("shiraz.colorized", colorize);
            return this;
        }

        public Builder setOnlyAlertOnce(boolean onlyAlertOnce) {
            return flag(FLAG_ONLY_ALERT_ONCE, onlyAlertOnce);
        }

        public Builder setForegroundServiceBehavior(int behavior) {
            n.extras.putInt("android.foregroundServiceBehavior", behavior);
            return this;
        }

        public Builder setAutoCancel(boolean autoCancel) {
            return flag(FLAG_AUTO_CANCEL, autoCancel);
        }

        public Builder setLocalOnly(boolean b) {
            return flag(FLAG_LOCAL_ONLY, b);
        }

        @Deprecated
        public Builder setDefaults(int defaults) {
            n.defaults = defaults;
            return this;
        }

        @Deprecated
        public Builder setPriority(int pri) {
            n.priority = pri;
            return this;
        }

        public Builder setCategory(String category) {
            n.category = category;
            return this;
        }

        @Deprecated
        public Builder addPerson(String uri) {
            if (uri != null) {
                people.add(new Person.Builder().setUri(uri).build());
            }
            return this;
        }

        public Builder addPerson(Person person) {
            if (person != null) {
                people.add(person);
            }
            return this;
        }

        public Builder setGroup(String groupKey) {
            n.group = groupKey;
            return this;
        }

        public Builder setGroupSummary(boolean isGroupSummary) {
            return flag(FLAG_GROUP_SUMMARY, isGroupSummary);
        }

        public Builder setSortKey(String sortKey) {
            n.sortKey = sortKey;
            return this;
        }

        public Builder addExtras(Bundle extras) {
            if (extras != null) suppliedExtras.putAll(extras);
            return this;
        }

        public Builder setExtras(Bundle extras) {
            suppliedExtras = extras == null ? new Bundle() : new Bundle(extras);
            return this;
        }

        public Bundle getExtras() {
            return suppliedExtras;
        }

        @Deprecated
        public Builder addAction(int icon, CharSequence title, PendingIntent intent) {
            return addAction(new Action(icon, title, intent));
        }

        public Builder addAction(Action action) {
            if (action == null) throw new NullPointerException("action");
            if (builtActions.size() >= 10) {
                throw new IllegalStateException("Notification action limit");
            }
            builtActions.add(action);
            return this;
        }

        public Builder setActions(Action... actions) {
            builtActions.clear();
            if (actions != null) {
                if (actions.length > 10) {
                    throw new IllegalStateException("Notification action limit");
                }
                for (Action action : actions) {
                    if (action != null) builtActions.add(action);
                }
            }
            return this;
        }

        public Builder setStyle(Style style) {
            if (this.style != style) {
                this.style = style;
                if (style != null) {
                    style.setBuilder(this);
                    style.apply();
                }
            }
            return this;
        }

        public Style getStyle() {
            return style;
        }

        public Builder setVisibility(int visibility) {
            n.visibility = visibility;
            return this;
        }

        public Builder setPublicVersion(Notification v) {
            n.publicVersion = v == null ? null : v.clone();
            if (v != null) n.extras.putBundle("shiraz.public", new Bundle(v.extras));
            return this;
        }

        public Builder extend(Extender extender) {
            extender.extend(this);
            return this;
        }

        public Builder setFlag(int mask, boolean value) {
            return flag(mask, value);
        }

        public Builder setColor(int argb) {
            n.color = argb;
            return this;
        }

        @Deprecated
        public RemoteViews createContentView() {
            return null;
        }

        @Deprecated
        public RemoteViews createBigContentView() {
            return null;
        }

        @Deprecated
        public RemoteViews createHeadsUpContentView() {
            return null;
        }

        public Builder setAllowSystemGeneratedContextualActions(boolean allowed) {
            n.allowSystemGeneratedContextualActions = allowed;
            return this;
        }

        @Deprecated
        public Notification getNotification() {
            return build();
        }

        public Notification build() {
            if (style != null) {
                style.apply();
            }
            Notification result = n.clone();
            Bundle merged = new Bundle(suppliedExtras);
            merged.putAll(result.extras);
            if (!people.isEmpty()) {
                merged.putParcelableArrayList(EXTRA_PEOPLE_LIST, new ArrayList<Person>(people));
            }
            result.extras = merged;
            if (!builtActions.isEmpty()) {
                result.actions = builtActions.toArray(new Action[builtActions.size()]);
            }
            return result;
        }
    }

    public static abstract class Style {
        protected Builder mBuilder;
        Builder builder;
        private CharSequence mBigContentTitle;
        private CharSequence mSummaryText;
        private boolean mSummaryTextSet;

        public Style() {}

        protected void internalSetBigContentTitle(CharSequence title) {
            mBigContentTitle = title;
            if (mBuilder != null && title != null) {
                mBuilder.text(EXTRA_TITLE_BIG, title);
            }
        }

        protected void internalSetSummaryText(CharSequence cs) {
            mSummaryText = cs;
            mSummaryTextSet = true;
            if (mBuilder != null && cs != null) {
                mBuilder.text(EXTRA_SUMMARY_TEXT, cs);
            }
        }

        public void setBuilder(Builder builder) {
            if (this.mBuilder != builder) {
                this.mBuilder = builder;
                this.builder = builder;
                if (this.mBuilder != null) {
                    this.mBuilder.setStyle(this);
                }
            }
        }

        protected void checkBuilder() {
            if (mBuilder == null) {
                throw new IllegalArgumentException("Style requires a valid Builder object");
            }
        }

        protected RemoteViews getStandardView(int layoutId) {
            return null;
        }

        public Notification build() {
            checkBuilder();
            return mBuilder.build();
        }

        protected void apply() {
            if (mBuilder != null) {
                if (mBigContentTitle != null) mBuilder.text(EXTRA_TITLE_BIG, mBigContentTitle);
                if (mSummaryTextSet && mSummaryText != null) {
                    mBuilder.text(EXTRA_SUMMARY_TEXT, mSummaryText);
                }
            }
        }
    }

    public static class BigPictureStyle extends Style {
        private Icon pictureIcon;
        private Bitmap pictureBitmap;
        private Icon bigLargeIcon;
        private boolean bigLargeIconSet;
        private CharSequence contentDescription;
        private boolean showBigPictureWhenCollapsed;

        public BigPictureStyle() {}

        @Deprecated
        public BigPictureStyle(Builder builder) {
            setBuilder(builder);
        }

        public BigPictureStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(title);
            return this;
        }

        public BigPictureStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(cs);
            return this;
        }

        public BigPictureStyle setContentDescription(CharSequence contentDescription) {
            this.contentDescription = contentDescription;
            if (mBuilder != null) apply();
            return this;
        }

        public BigPictureStyle bigPicture(Bitmap b) {
            this.pictureBitmap = b;
            this.pictureIcon = b != null ? Icon.createWithBitmap(b) : null;
            if (mBuilder != null) apply();
            return this;
        }

        public BigPictureStyle bigPicture(Icon icon) {
            this.pictureIcon = icon;
            this.pictureBitmap = null;
            if (mBuilder != null) apply();
            return this;
        }

        public BigPictureStyle showBigPictureWhenCollapsed(boolean show) {
            this.showBigPictureWhenCollapsed = show;
            if (mBuilder != null) apply();
            return this;
        }

        public BigPictureStyle bigLargeIcon(Bitmap b) {
            this.bigLargeIconSet = true;
            this.bigLargeIcon = b != null ? Icon.createWithBitmap(b) : null;
            if (mBuilder != null) apply();
            return this;
        }

        public BigPictureStyle bigLargeIcon(Icon icon) {
            this.bigLargeIconSet = true;
            this.bigLargeIcon = icon;
            if (mBuilder != null) apply();
            return this;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder == null) return;
            mBuilder.n.extras.putString(EXTRA_TEMPLATE, BigPictureStyle.class.getName());
            if (pictureBitmap != null) {
                mBuilder.n.extras.putParcelable(EXTRA_PICTURE, pictureBitmap);
            }
            if (pictureIcon != null) {
                mBuilder.n.extras.putParcelable(EXTRA_PICTURE_ICON, pictureIcon);
            }
            if (bigLargeIconSet) {
                mBuilder.n.extras.putParcelable(EXTRA_LARGE_ICON_BIG, bigLargeIcon);
            }
            if (contentDescription != null) {
                mBuilder.text(EXTRA_PICTURE_CONTENT_DESCRIPTION, contentDescription);
            }
            mBuilder.n.extras.putBoolean(
                    EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED, showBigPictureWhenCollapsed);
        }
    }

    public static class BigTextStyle extends Style {
        private CharSequence bigText;

        public BigTextStyle() {}

        @Deprecated
        public BigTextStyle(Builder b) {
            setBuilder(b);
        }

        public BigTextStyle bigText(CharSequence text) {
            bigText = text;
            if (mBuilder != null) apply();
            return this;
        }

        public BigTextStyle setBigContentTitle(CharSequence text) {
            internalSetBigContentTitle(text);
            if (mBuilder != null) apply();
            return this;
        }

        public BigTextStyle setSummaryText(CharSequence text) {
            internalSetSummaryText(text);
            if (mBuilder != null) apply();
            return this;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder != null && bigText != null) {
                mBuilder.text(EXTRA_BIG_TEXT, bigText);
            }
        }
    }

    public static class InboxStyle extends Style {
        private final ArrayList<CharSequence> texts = new ArrayList<CharSequence>(5);

        public InboxStyle() {}

        @Deprecated
        public InboxStyle(Builder builder) {
            setBuilder(builder);
        }

        public InboxStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(title);
            return this;
        }

        public InboxStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(cs);
            return this;
        }

        public InboxStyle addLine(CharSequence cs) {
            if (cs != null) texts.add(cs);
            if (mBuilder != null) apply();
            return this;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder == null) return;
            mBuilder.n.extras.putString(EXTRA_TEMPLATE, InboxStyle.class.getName());
            mBuilder.n.extras.putCharSequenceArray(
                    EXTRA_TEXT_LINES, texts.toArray(new CharSequence[texts.size()]));
        }
    }

    public static class MediaStyle extends Style {
        private MediaSession.Token token;
        private int[] compact;
        private CharSequence remoteDeviceName;
        private int remoteDeviceIconRes;
        private PendingIntent remoteDeviceIntent;

        public MediaStyle() {}

        @Deprecated
        public MediaStyle(Builder builder) {
            setBuilder(builder);
        }

        public MediaStyle setMediaSession(MediaSession.Token token) {
            this.token = token;
            if (mBuilder != null) apply();
            return this;
        }

        public MediaStyle setShowActionsInCompactView(int... actions) {
            if (actions == null || actions.length > 3) {
                throw new IllegalArgumentException("Compact media actions");
            }
            compact = actions.clone();
            if (mBuilder != null) apply();
            return this;
        }

        public MediaStyle setRemotePlaybackInfo(
                CharSequence deviceName, int iconResource, PendingIntent chipIntent) {
            this.remoteDeviceName = Objects.requireNonNull(deviceName, "deviceName");
            this.remoteDeviceIconRes = iconResource;
            this.remoteDeviceIntent = chipIntent;
            if (mBuilder != null) apply();
            return this;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder == null) return;
            mBuilder.n.extras.putParcelable(EXTRA_MEDIA_SESSION, token);
            if (compact != null) {
                mBuilder.n.extras.putIntArray(EXTRA_COMPACT_ACTIONS, compact.clone());
            }
            mBuilder.setCategory(CATEGORY_TRANSPORT);
        }
    }

    public static class DecoratedCustomViewStyle extends Style {
        public DecoratedCustomViewStyle() {}
    }

    public static class DecoratedMediaCustomViewStyle extends MediaStyle {
        public DecoratedMediaCustomViewStyle() {}
    }

    public static class MessagingStyle extends Style {
        public static final int MAXIMUM_RETAINED_MESSAGES = 25;

        private final Person user;
        private CharSequence conversationTitle;
        private Boolean isGroupConversation;
        private final ArrayList<Message> messages = new ArrayList<Message>();
        private final ArrayList<Message> historicMessages = new ArrayList<Message>();

        @Deprecated
        public MessagingStyle(CharSequence userDisplayName) {
            this.user = new Person.Builder()
                    .setName(userDisplayName != null ? userDisplayName : "")
                    .build();
        }

        public MessagingStyle(Person user) {
            Objects.requireNonNull(user, "user");
            if (TextUtils.isEmpty(user.getName())) {
                throw new IllegalArgumentException("User's name must not be empty.");
            }
            this.user = user;
        }

        public Person getUser() {
            return user;
        }

        @Deprecated
        public CharSequence getUserDisplayName() {
            return user.getName();
        }

        public MessagingStyle setConversationTitle(CharSequence conversationTitle) {
            this.conversationTitle = conversationTitle;
            if (mBuilder != null) apply();
            return this;
        }

        public CharSequence getConversationTitle() {
            return conversationTitle;
        }

        @Deprecated
        public MessagingStyle addMessage(CharSequence text, long timestamp, CharSequence sender) {
            return addMessage(new Message(
                    text,
                    timestamp,
                    sender == null ? null : new Person.Builder().setName(sender).build()));
        }

        public MessagingStyle addMessage(CharSequence text, long timestamp, Person sender) {
            return addMessage(new Message(text, timestamp, sender));
        }

        public MessagingStyle addMessage(Message message) {
            if (message != null) {
                messages.add(message);
                if (messages.size() > MAXIMUM_RETAINED_MESSAGES) {
                    messages.remove(0);
                }
                if (mBuilder != null) apply();
            }
            return this;
        }

        public MessagingStyle addHistoricMessage(Message message) {
            if (message != null) {
                historicMessages.add(message);
                if (historicMessages.size() > MAXIMUM_RETAINED_MESSAGES) {
                    historicMessages.remove(0);
                }
                if (mBuilder != null) apply();
            }
            return this;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public List<Message> getHistoricMessages() {
            return historicMessages;
        }

        public MessagingStyle setGroupConversation(boolean isGroupConversation) {
            this.isGroupConversation = Boolean.valueOf(isGroupConversation);
            if (mBuilder != null) apply();
            return this;
        }

        public boolean isGroupConversation() {
            if (isGroupConversation != null) {
                return isGroupConversation.booleanValue();
            }
            return conversationTitle != null;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder == null) return;
            mBuilder.n.extras.putString(EXTRA_TEMPLATE, MessagingStyle.class.getName());
            mBuilder.n.extras.putCharSequence(EXTRA_SELF_DISPLAY_NAME, user.getName());
            mBuilder.n.extras.putParcelable(EXTRA_MESSAGING_PERSON, user);
            if (conversationTitle != null) {
                mBuilder.n.extras.putCharSequence(EXTRA_CONVERSATION_TITLE, conversationTitle);
            }
            if (!messages.isEmpty()) {
                mBuilder.n.extras.putParcelableArray(EXTRA_MESSAGES, Message.toBundleArray(messages));
            }
            if (!historicMessages.isEmpty()) {
                mBuilder.n.extras.putParcelableArray(
                        EXTRA_HISTORIC_MESSAGES, Message.toBundleArray(historicMessages));
            }
            mBuilder.n.extras.putBoolean(EXTRA_IS_GROUP_CONVERSATION, isGroupConversation());
        }

        public static final class Message {
            private final CharSequence text;
            private final long timestamp;
            private final Person sender;
            private final Bundle extras = new Bundle();
            private String dataMimeType;
            private Uri dataUri;

            @Deprecated
            public Message(CharSequence text, long timestamp, CharSequence sender) {
                this(text, timestamp, sender == null ? null : new Person.Builder().setName(sender).build());
            }

            public Message(CharSequence text, long timestamp, Person sender) {
                this.text = text;
                this.timestamp = timestamp;
                this.sender = sender;
            }

            public Message setData(String dataMimeType, Uri dataUri) {
                this.dataMimeType = dataMimeType;
                this.dataUri = dataUri;
                return this;
            }

            public CharSequence getText() { return text; }
            public long getTimestamp() { return timestamp; }
            public Bundle getExtras() { return extras; }
            @Deprecated
            public CharSequence getSender() { return sender == null ? null : sender.getName(); }
            public Person getSenderPerson() { return sender; }
            public String getDataMimeType() { return dataMimeType; }
            public Uri getDataUri() { return dataUri; }

            Bundle toBundle() {
                Bundle b = new Bundle();
                if (text != null) b.putCharSequence("text", text);
                b.putLong("time", timestamp);
                if (sender != null) {
                    b.putCharSequence("sender", sender.getName());
                    b.putParcelable("sender_person", sender);
                }
                if (dataMimeType != null) b.putString("type", dataMimeType);
                if (dataUri != null) b.putString("uri_str", dataUri.toString());
                if (!extras.isEmpty()) b.putBundle("extras", new Bundle(extras));
                return b;
            }

            static Bundle[] toBundleArray(List<Message> messages) {
                Bundle[] bundles = new Bundle[messages.size()];
                for (int i = 0; i < messages.size(); i++) {
                    bundles[i] = messages.get(i).toBundle();
                }
                return bundles;
            }

            public static List<Message> getMessagesFromBundleArray(Parcelable[] bundles) {
                if (bundles == null) return Collections.emptyList();
                List<Message> list = new ArrayList<Message>(bundles.length);
                for (Parcelable p : bundles) {
                    if (p instanceof Bundle) {
                        Bundle b = (Bundle) p;
                        if (b.containsKey("text") && b.containsKey("time")) {
                            Person person = b.getParcelable("sender_person");
                            if (person == null && b.containsKey("sender")) {
                                person = new Person.Builder().setName(b.getCharSequence("sender")).build();
                            }
                            Message msg = new Message(
                                    b.getCharSequence("text"), b.getLong("time"), person);
                            if (b.containsKey("type") && b.containsKey("uri_str")) {
                                msg.setData(b.getString("type"), Uri.parse(b.getString("uri_str")));
                            }
                            Bundle ext = b.getBundle("extras");
                            if (ext != null) msg.getExtras().putAll(ext);
                            list.add(msg);
                        }
                    }
                }
                return list;
            }
        }
    }

    public static class CallStyle extends Style {
        public static final int CALL_TYPE_UNKNOWN = 0;
        public static final int CALL_TYPE_INCOMING = 1;
        public static final int CALL_TYPE_ONGOING = 2;
        public static final int CALL_TYPE_SCREENING = 3;

        private final int callType;
        private final Person person;
        private final PendingIntent answerIntent;
        private final PendingIntent declineIntent;
        private final PendingIntent hangUpIntent;
        private boolean isVideo;
        private Icon verificationIcon;
        private CharSequence verificationText;
        private Integer answerButtonColor;
        private Integer declineButtonColor;

        private CallStyle(
                int callType,
                Person person,
                PendingIntent hangUpIntent,
                PendingIntent declineIntent,
                PendingIntent answerIntent) {
            if (person == null || TextUtils.isEmpty(person.getName())) {
                throw new IllegalArgumentException("person must be non-null and have a non-empty name");
            }
            this.callType = callType;
            this.person = person;
            this.hangUpIntent = hangUpIntent;
            this.declineIntent = declineIntent;
            this.answerIntent = answerIntent;
        }

        public static CallStyle forIncomingCall(
                Person person, PendingIntent declineIntent, PendingIntent answerIntent) {
            return new CallStyle(
                    CALL_TYPE_INCOMING,
                    person,
                    null,
                    Objects.requireNonNull(declineIntent, "declineIntent is required"),
                    Objects.requireNonNull(answerIntent, "answerIntent is required"));
        }

        public static CallStyle forOngoingCall(Person person, PendingIntent hangUpIntent) {
            return new CallStyle(
                    CALL_TYPE_ONGOING,
                    person,
                    Objects.requireNonNull(hangUpIntent, "hangUpIntent is required"),
                    null,
                    null);
        }

        public static CallStyle forScreeningCall(
                Person person, PendingIntent hangUpIntent, PendingIntent answerIntent) {
            return new CallStyle(
                    CALL_TYPE_SCREENING,
                    person,
                    Objects.requireNonNull(hangUpIntent, "hangUpIntent is required"),
                    null,
                    Objects.requireNonNull(answerIntent, "answerIntent is required"));
        }

        public CallStyle setIsVideo(boolean isVideo) {
            this.isVideo = isVideo;
            if (mBuilder != null) apply();
            return this;
        }

        public CallStyle setVerificationIcon(Icon verificationIcon) {
            this.verificationIcon = verificationIcon;
            if (mBuilder != null) apply();
            return this;
        }

        public CallStyle setVerificationText(CharSequence verificationText) {
            this.verificationText = verificationText;
            if (mBuilder != null) apply();
            return this;
        }

        public CallStyle setAnswerButtonColorHint(int color) {
            this.answerButtonColor = Integer.valueOf(color);
            if (mBuilder != null) apply();
            return this;
        }

        public CallStyle setDeclineButtonColorHint(int color) {
            this.declineButtonColor = Integer.valueOf(color);
            if (mBuilder != null) apply();
            return this;
        }

        @Override
        protected void apply() {
            super.apply();
            if (mBuilder == null) return;
            mBuilder.n.extras.putString(EXTRA_TEMPLATE, CallStyle.class.getName());
            mBuilder.n.extras.putInt(EXTRA_CALL_TYPE, callType);
            mBuilder.n.extras.putParcelable(EXTRA_CALL_PERSON, person);
            mBuilder.n.extras.putBoolean(EXTRA_CALL_IS_VIDEO, isVideo);
            if (answerIntent != null) mBuilder.n.extras.putParcelable(EXTRA_ANSWER_INTENT, answerIntent);
            if (declineIntent != null) mBuilder.n.extras.putParcelable(EXTRA_DECLINE_INTENT, declineIntent);
            if (hangUpIntent != null) mBuilder.n.extras.putParcelable(EXTRA_HANG_UP_INTENT, hangUpIntent);
            if (verificationIcon != null) {
                mBuilder.n.extras.putParcelable(EXTRA_VERIFICATION_ICON, verificationIcon);
            }
            if (verificationText != null) {
                mBuilder.text(EXTRA_VERIFICATION_TEXT, verificationText);
            }
            if (answerButtonColor != null) {
                mBuilder.n.extras.putInt(EXTRA_ANSWER_COLOR, answerButtonColor.intValue());
            }
            if (declineButtonColor != null) {
                mBuilder.n.extras.putInt(EXTRA_DECLINE_COLOR, declineButtonColor.intValue());
            }
            mBuilder.setCategory(CATEGORY_CALL);
        }
    }

    public static final class CarExtender implements Extender {
        private Bitmap largeIcon;
        private int color = COLOR_DEFAULT;
        private UnreadConversation unreadConversation;

        public CarExtender() {}

        public CarExtender(Notification notif) {
            if (notif != null && notif.extras != null) {
                Bundle carBundle = notif.extras.getBundle("android.car.EXTENSIONS");
                if (carBundle != null) {
                    largeIcon = carBundle.getParcelable("large_icon");
                    color = carBundle.getInt("app_color", COLOR_DEFAULT);
                }
            }
        }

        @Override
        public Notification.Builder extend(Notification.Builder builder) {
            Bundle carExtensions = new Bundle();
            if (largeIcon != null) carExtensions.putParcelable("large_icon", largeIcon);
            if (color != COLOR_DEFAULT) carExtensions.putInt("app_color", color);
            builder.getExtras().putBundle("android.car.EXTENSIONS", carExtensions);
            return builder;
        }

        public CarExtender setColor(int color) {
            this.color = color;
            return this;
        }

        public int getColor() {
            return color;
        }

        public CarExtender setLargeIcon(Bitmap largeIcon) {
            this.largeIcon = largeIcon;
            return this;
        }

        public Bitmap getLargeIcon() {
            return largeIcon;
        }

        @Deprecated
        public CarExtender setUnreadConversation(UnreadConversation unreadConversation) {
            this.unreadConversation = unreadConversation;
            return this;
        }

        @Deprecated
        public UnreadConversation getUnreadConversation() {
            return unreadConversation;
        }

        @Deprecated
        public static class UnreadConversation {
            private final String[] messages;
            private final RemoteInput remoteInput;
            private final PendingIntent replyPendingIntent;
            private final PendingIntent readPendingIntent;
            private final String[] participants;
            private final long latestTimestamp;

            UnreadConversation(
                    String[] messages,
                    RemoteInput remoteInput,
                    PendingIntent replyPendingIntent,
                    PendingIntent readPendingIntent,
                    String[] participants,
                    long latestTimestamp) {
                this.messages = messages;
                this.remoteInput = remoteInput;
                this.replyPendingIntent = replyPendingIntent;
                this.readPendingIntent = readPendingIntent;
                this.participants = participants;
                this.latestTimestamp = latestTimestamp;
            }

            public String[] getMessages() { return messages; }
            public RemoteInput getRemoteInput() { return remoteInput; }
            public PendingIntent getReplyPendingIntent() { return replyPendingIntent; }
            public PendingIntent getReadPendingIntent() { return readPendingIntent; }
            public String[] getParticipants() { return participants; }
            public String getParticipant() {
                return participants != null && participants.length > 0 ? participants[0] : null;
            }
            public long getLatestTimestamp() { return latestTimestamp; }
        }

        @Deprecated
        public static class Builder {
            private final List<String> messages = new ArrayList<String>();
            private RemoteInput remoteInput;
            private PendingIntent replyPendingIntent;
            private PendingIntent readPendingIntent;
            private final String participant;
            private long latestTimestamp;

            public Builder(String name) {
                this.participant = name;
            }

            public Builder addMessage(String message) {
                messages.add(message);
                return this;
            }

            public Builder setReplyAction(PendingIntent pendingIntent, RemoteInput remoteInput) {
                this.remoteInput = remoteInput;
                this.replyPendingIntent = pendingIntent;
                return this;
            }

            public Builder setReadPendingIntent(PendingIntent pendingIntent) {
                this.readPendingIntent = pendingIntent;
                return this;
            }

            public Builder setLatestTimestamp(long timestamp) {
                this.latestTimestamp = timestamp;
                return this;
            }

            public UnreadConversation build() {
                return new UnreadConversation(
                        messages.toArray(new String[messages.size()]),
                        remoteInput,
                        replyPendingIntent,
                        readPendingIntent,
                        new String[] {participant},
                        latestTimestamp);
            }
        }
    }

    public static final class TvExtender implements Extender {
        private int flags = 1;
        private String channelId;
        private PendingIntent contentIntent;
        private PendingIntent deleteIntent;
        private boolean suppressShowOverApps;

        public TvExtender() {}

        public TvExtender(Notification notif) {
            if (notif != null && notif.extras != null) {
                Bundle tv = notif.extras.getBundle("android.tv.EXTENSIONS");
                if (tv != null) {
                    flags = tv.getInt("flags", 0);
                    channelId = tv.getString("channel_id");
                    contentIntent = tv.getParcelable("content_intent");
                    deleteIntent = tv.getParcelable("delete_intent");
                    suppressShowOverApps = tv.getBoolean("suppressShowOverApps");
                } else {
                    flags = 0;
                }
            }
        }

        @Override
        public Builder extend(Builder builder) {
            Bundle tv = new Bundle();
            tv.putInt("flags", flags);
            if (channelId != null) tv.putString("channel_id", channelId);
            if (contentIntent != null) tv.putParcelable("content_intent", contentIntent);
            if (deleteIntent != null) tv.putParcelable("delete_intent", deleteIntent);
            tv.putBoolean("suppressShowOverApps", suppressShowOverApps);
            builder.getExtras().putBundle("android.tv.EXTENSIONS", tv);
            return builder;
        }

        public boolean isAvailableOnTv() { return (flags & 1) != 0; }
        public TvExtender setChannelId(String channelId) { this.channelId = channelId; return this; }
        public String getChannelId() { return channelId; }
        public TvExtender setContentIntent(PendingIntent intent) { this.contentIntent = intent; return this; }
        public PendingIntent getContentIntent() { return contentIntent; }
        public TvExtender setDeleteIntent(PendingIntent intent) { this.deleteIntent = intent; return this; }
        public PendingIntent getDeleteIntent() { return deleteIntent; }
        public TvExtender setSuppressShowOverApps(boolean suppress) {
            this.suppressShowOverApps = suppress;
            return this;
        }
        public boolean isSuppressShowOverApps() { return suppressShowOverApps; }
    }

    public static final class WearableExtender implements Extender {
        public static final int UNSET_ACTION_INDEX = -1;
        @Deprecated public static final int SIZE_DEFAULT = 0;
        @Deprecated public static final int SIZE_XSMALL = 1;
        @Deprecated public static final int SIZE_SMALL = 2;
        @Deprecated public static final int SIZE_MEDIUM = 3;
        @Deprecated public static final int SIZE_LARGE = 4;
        @Deprecated public static final int SIZE_FULL_SCREEN = 5;
        @Deprecated public static final int SCREEN_TIMEOUT_SHORT = 0;
        @Deprecated public static final int SCREEN_TIMEOUT_LONG = -1;

        private final ArrayList<Action> actions = new ArrayList<Action>();
        private int flags = 1;
        private PendingIntent displayIntent;
        private final ArrayList<Notification> pages = new ArrayList<Notification>();
        private int contentIcon;
        private int contentIconGravity = 0x800005;
        private int contentActionIndex = UNSET_ACTION_INDEX;
        private int customSizePreset = SIZE_DEFAULT;
        private int customContentHeight;
        private int gravity = 0x50;
        private int hintScreenTimeout = SCREEN_TIMEOUT_SHORT;
        private String dismissalId;
        private String bridgeTag;

        public WearableExtender() {}

        public WearableExtender(Notification notif) {
            if (notif != null && notif.extras != null) {
                Bundle wearable = notif.extras.getBundle("android.wearable.EXTENSIONS");
                if (wearable != null) {
                    flags = wearable.getInt("flags", 1);
                    displayIntent = wearable.getParcelable("displayIntent");
                    contentIcon = wearable.getInt("contentIcon");
                    contentIconGravity = wearable.getInt("contentIconGravity", 0x800005);
                    contentActionIndex = wearable.getInt("contentActionIndex", UNSET_ACTION_INDEX);
                    customSizePreset = wearable.getInt("customSizePreset", SIZE_DEFAULT);
                    customContentHeight = wearable.getInt("customContentHeight");
                    gravity = wearable.getInt("gravity", 0x50);
                    hintScreenTimeout = wearable.getInt("hintScreenTimeout", SCREEN_TIMEOUT_SHORT);
                    dismissalId = wearable.getString("dismissalId");
                    bridgeTag = wearable.getString("bridgeTag");
                }
            }
        }

        @Override
        public Builder extend(Builder builder) {
            Bundle wearable = new Bundle();
            wearable.putInt("flags", flags);
            if (displayIntent != null) wearable.putParcelable("displayIntent", displayIntent);
            if (contentIcon != 0) wearable.putInt("contentIcon", contentIcon);
            wearable.putInt("contentIconGravity", contentIconGravity);
            wearable.putInt("contentActionIndex", contentActionIndex);
            wearable.putInt("customSizePreset", customSizePreset);
            wearable.putInt("customContentHeight", customContentHeight);
            wearable.putInt("gravity", gravity);
            wearable.putInt("hintScreenTimeout", hintScreenTimeout);
            if (dismissalId != null) wearable.putString("dismissalId", dismissalId);
            if (bridgeTag != null) wearable.putString("bridgeTag", bridgeTag);
            builder.getExtras().putBundle("android.wearable.EXTENSIONS", wearable);
            return builder;
        }

        @Override
        public WearableExtender clone() {
            WearableExtender that = new WearableExtender();
            that.actions.addAll(this.actions);
            that.flags = this.flags;
            that.displayIntent = this.displayIntent;
            that.pages.addAll(this.pages);
            that.contentIcon = this.contentIcon;
            that.contentIconGravity = this.contentIconGravity;
            that.contentActionIndex = this.contentActionIndex;
            that.customSizePreset = this.customSizePreset;
            that.customContentHeight = this.customContentHeight;
            that.gravity = this.gravity;
            that.hintScreenTimeout = this.hintScreenTimeout;
            that.dismissalId = this.dismissalId;
            that.bridgeTag = this.bridgeTag;
            return that;
        }

        private void setFlag(int mask, boolean value) {
            flags = value ? (flags | mask) : (flags & ~mask);
        }

        public WearableExtender addAction(Action action) { actions.add(action); return this; }
        public WearableExtender addActions(List<Action> actions) { this.actions.addAll(actions); return this; }
        public WearableExtender clearActions() { actions.clear(); return this; }
        public List<Action> getActions() { return new ArrayList<Action>(actions); }
        @Deprecated public WearableExtender setDisplayIntent(PendingIntent intent) { displayIntent = intent; return this; }
        @Deprecated public PendingIntent getDisplayIntent() { return displayIntent; }
        @Deprecated public WearableExtender addPage(Notification page) { pages.add(page); return this; }
        @Deprecated public WearableExtender addPages(List<Notification> pages) { this.pages.addAll(pages); return this; }
        @Deprecated public WearableExtender clearPages() { pages.clear(); return this; }
        @Deprecated public List<Notification> getPages() { return new ArrayList<Notification>(pages); }
        @Deprecated public WearableExtender setContentIcon(int icon) { contentIcon = icon; return this; }
        @Deprecated public int getContentIcon() { return contentIcon; }
        @Deprecated public WearableExtender setContentIconGravity(int gravity) { contentIconGravity = gravity; return this; }
        @Deprecated public int getContentIconGravity() { return contentIconGravity; }
        public WearableExtender setContentAction(int actionIndex) { contentActionIndex = actionIndex; return this; }
        public int getContentAction() { return contentActionIndex; }
        @Deprecated public WearableExtender setGravity(int gravity) { this.gravity = gravity; return this; }
        @Deprecated public int getGravity() { return gravity; }
        @Deprecated public WearableExtender setCustomSizePreset(int sizePreset) { customSizePreset = sizePreset; return this; }
        @Deprecated public int getCustomSizePreset() { return customSizePreset; }
        @Deprecated public WearableExtender setCustomContentHeight(int height) { customContentHeight = height; return this; }
        @Deprecated public int getCustomContentHeight() { return customContentHeight; }
        public WearableExtender setStartScrollBottom(boolean startScrollBottom) { setFlag(8, startScrollBottom); return this; }
        public boolean getStartScrollBottom() { return (flags & 8) != 0; }
        public WearableExtender setContentIntentAvailableOffline(boolean availableOffline) { setFlag(1, availableOffline); return this; }
        public boolean getContentIntentAvailableOffline() { return (flags & 1) != 0; }
        @Deprecated public WearableExtender setHintHideIcon(boolean hintHideIcon) { setFlag(2, hintHideIcon); return this; }
        @Deprecated public boolean getHintHideIcon() { return (flags & 2) != 0; }
        @Deprecated public WearableExtender setHintShowBackgroundOnly(boolean hintShowBackgroundOnly) { setFlag(4, hintShowBackgroundOnly); return this; }
        @Deprecated public boolean getHintShowBackgroundOnly() { return (flags & 4) != 0; }
        @Deprecated public WearableExtender setHintAvoidBackgroundClipping(boolean hintAvoidBackgroundClipping) { setFlag(16, hintAvoidBackgroundClipping); return this; }
        @Deprecated public boolean getHintAvoidBackgroundClipping() { return (flags & 16) != 0; }
        @Deprecated public WearableExtender setHintScreenTimeout(int timeout) { hintScreenTimeout = timeout; return this; }
        @Deprecated public int getHintScreenTimeout() { return hintScreenTimeout; }
        @Deprecated public WearableExtender setHintAmbientBigPicture(boolean hintAmbientBigPicture) { setFlag(32, hintAmbientBigPicture); return this; }
        @Deprecated public boolean getHintAmbientBigPicture() { return (flags & 32) != 0; }
        public WearableExtender setHintContentIntentLaunchesActivity(boolean hint) { setFlag(64, hint); return this; }
        public boolean getHintContentIntentLaunchesActivity() { return (flags & 64) != 0; }
        public WearableExtender setDismissalId(String dismissalId) { this.dismissalId = dismissalId; return this; }
        public String getDismissalId() { return dismissalId; }
        public WearableExtender setBridgeTag(String bridgeTag) { this.bridgeTag = bridgeTag; return this; }
        public String getBridgeTag() { return bridgeTag; }
    }
}
