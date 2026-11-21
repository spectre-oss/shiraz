// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import shiraz.runtime.DesktopNotifications;
import shiraz.runtime.NotificationBackend;

/** Session-owned notification delivery with persistent channel policy. */
public class NotificationManager {
    public static final String ACTION_APP_BLOCK_STATE_CHANGED =
            "android.app.action.APP_BLOCK_STATE_CHANGED";
    public static final String ACTION_AUTOMATIC_ZEN_RULE =
            "android.app.action.AUTOMATIC_ZEN_RULE";
    public static final String ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED =
            "android.app.action.AUTOMATIC_ZEN_RULE_STATUS_CHANGED";
    public static final String ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED =
            "android.app.action.CONSOLIDATED_NOTIFICATION_POLICY_CHANGED";
    public static final String ACTION_INTERRUPTION_FILTER_CHANGED =
            "android.app.action.INTERRUPTION_FILTER_CHANGED";
    public static final String ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED =
            "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED";
    public static final String ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED =
            "android.app.action.NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED";
    public static final String ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED =
            "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED";
    public static final String ACTION_NOTIFICATION_POLICY_CHANGED =
            "android.app.action.NOTIFICATION_POLICY_CHANGED";

    public static final int AUTOMATIC_RULE_STATUS_UNKNOWN = -1;
    public static final int AUTOMATIC_RULE_STATUS_ENABLED = 1;
    public static final int AUTOMATIC_RULE_STATUS_DISABLED = 2;
    public static final int AUTOMATIC_RULE_STATUS_REMOVED = 3;
    public static final int AUTOMATIC_RULE_STATUS_ACTIVATED = 4;
    public static final int AUTOMATIC_RULE_STATUS_DEACTIVATED = 5;

    public static final int BUBBLE_PREFERENCE_NONE = 0;
    public static final int BUBBLE_PREFERENCE_ALL = 1;
    public static final int BUBBLE_PREFERENCE_SELECTED = 2;

    public static final String EXTRA_AUTOMATIC_RULE_ID = "android.app.extra.AUTOMATIC_RULE_ID";
    public static final String EXTRA_AUTOMATIC_ZEN_RULE_ID = "android.app.extra.AUTOMATIC_ZEN_RULE_ID";
    public static final String EXTRA_AUTOMATIC_ZEN_RULE_STATUS =
            "android.app.extra.AUTOMATIC_ZEN_RULE_STATUS";
    public static final String EXTRA_BLOCKED_STATE = "android.app.extra.BLOCKED_STATE";
    public static final String EXTRA_NOTIFICATION_CHANNEL_GROUP_ID =
            "android.app.extra.NOTIFICATION_CHANNEL_GROUP_ID";
    public static final String EXTRA_NOTIFICATION_CHANNEL_ID =
            "android.app.extra.NOTIFICATION_CHANNEL_ID";
    public static final String EXTRA_NOTIFICATION_POLICY = "android.app.extra.NOTIFICATION_POLICY";

    public static final String META_DATA_AUTOMATIC_RULE_TYPE =
            "android.service.zen.automatic.ruleType";
    public static final String META_DATA_RULE_INSTANCE_LIMIT =
            "android.service.zen.automatic.ruleInstanceLimit";

    public static final int IMPORTANCE_UNSPECIFIED = -1000;
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;
    public static final int IMPORTANCE_MAX = 5;

    public static final int INTERRUPTION_FILTER_UNKNOWN = 0;
    public static final int INTERRUPTION_FILTER_ALL = 1;
    public static final int INTERRUPTION_FILTER_PRIORITY = 2;
    public static final int INTERRUPTION_FILTER_NONE = 3;
    public static final int INTERRUPTION_FILTER_ALARMS = 4;

    public static class Policy implements Parcelable {
        public static final int PRIORITY_CATEGORY_REMINDERS = 1;
        public static final int PRIORITY_CATEGORY_EVENTS = 2;
        public static final int PRIORITY_CATEGORY_MESSAGES = 4;
        public static final int PRIORITY_CATEGORY_CALLS = 8;
        public static final int PRIORITY_CATEGORY_REPEAT_CALLERS = 16;
        public static final int PRIORITY_CATEGORY_ALARMS = 32;
        public static final int PRIORITY_CATEGORY_MEDIA = 64;
        public static final int PRIORITY_CATEGORY_SYSTEM = 128;
        public static final int PRIORITY_CATEGORY_CONVERSATIONS = 256;

        public static final int PRIORITY_SENDERS_ANY = 0;
        public static final int PRIORITY_SENDERS_CONTACTS = 1;
        public static final int PRIORITY_SENDERS_STARRED = 2;

        public static final int CONVERSATION_SENDERS_ANYONE = 1;
        public static final int CONVERSATION_SENDERS_IMPORTANT = 2;
        public static final int CONVERSATION_SENDERS_NONE = 3;

        @Deprecated public static final int SUPPRESSED_EFFECT_SCREEN_OFF = 1;
        @Deprecated public static final int SUPPRESSED_EFFECT_SCREEN_ON = 2;
        public static final int SUPPRESSED_EFFECT_FULL_SCREEN_INTENT = 4;
        public static final int SUPPRESSED_EFFECT_LIGHTS = 8;
        public static final int SUPPRESSED_EFFECT_PEEK = 16;
        public static final int SUPPRESSED_EFFECT_STATUS_BAR = 32;
        public static final int SUPPRESSED_EFFECT_BADGE = 64;
        public static final int SUPPRESSED_EFFECT_AMBIENT = 128;
        public static final int SUPPRESSED_EFFECT_NOTIFICATION_LIST = 256;

        public final int priorityCategories;
        public final int priorityCallSenders;
        public final int priorityMessageSenders;
        public final int suppressedVisualEffects;
        public final int priorityConversationSenders;

        public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders) {
            this(priorityCategories, priorityCallSenders, priorityMessageSenders, 0, CONVERSATION_SENDERS_NONE);
        }

        public Policy(
                int priorityCategories,
                int priorityCallSenders,
                int priorityMessageSenders,
                int suppressedVisualEffects) {
            this(
                    priorityCategories,
                    priorityCallSenders,
                    priorityMessageSenders,
                    suppressedVisualEffects,
                    CONVERSATION_SENDERS_NONE);
        }

        public Policy(
                int priorityCategories,
                int priorityCallSenders,
                int priorityMessageSenders,
                int suppressedVisualEffects,
                int priorityConversationSenders) {
            this.priorityCategories = priorityCategories;
            this.priorityCallSenders = priorityCallSenders;
            this.priorityMessageSenders = priorityMessageSenders;
            this.suppressedVisualEffects = suppressedVisualEffects;
            this.priorityConversationSenders = priorityConversationSenders;
        }

        private Policy(Parcel source) {
            this(
                    source.readInt(),
                    source.readInt(),
                    source.readInt(),
                    source.readInt(),
                    source.readInt());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(priorityCategories);
            dest.writeInt(priorityCallSenders);
            dest.writeInt(priorityMessageSenders);
            dest.writeInt(suppressedVisualEffects);
            dest.writeInt(priorityConversationSenders);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    priorityCategories,
                    priorityCallSenders,
                    priorityMessageSenders,
                    suppressedVisualEffects,
                    priorityConversationSenders);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Policy)) return false;
            Policy other = (Policy) o;
            return priorityCategories == other.priorityCategories
                    && priorityCallSenders == other.priorityCallSenders
                    && priorityMessageSenders == other.priorityMessageSenders
                    && suppressedVisualEffects == other.suppressedVisualEffects
                    && priorityConversationSenders == other.priorityConversationSenders;
        }

        @Override
        public String toString() {
            return "NotificationManager.Policy["
                    + "priorityCategories=" + priorityCategoriesToString(priorityCategories)
                    + ",priorityCallSenders=" + prioritySendersToString(priorityCallSenders)
                    + ",priorityMessageSenders=" + prioritySendersToString(priorityMessageSenders)
                    + ",priorityConversationSenders=" + priorityConversationSenders
                    + ",suppressedVisualEffects=" + suppressedEffectsToString(suppressedVisualEffects)
                    + "]";
        }

        public static String suppressedEffectsToString(int effects) {
            if (effects <= 0) return "";
            StringBuilder sb = new StringBuilder();
            appendFlag(sb, effects, SUPPRESSED_EFFECT_SCREEN_OFF, "SUPPRESSED_EFFECT_SCREEN_OFF");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_SCREEN_ON, "SUPPRESSED_EFFECT_SCREEN_ON");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_FULL_SCREEN_INTENT, "SUPPRESSED_EFFECT_FULL_SCREEN_INTENT");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_LIGHTS, "SUPPRESSED_EFFECT_LIGHTS");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_PEEK, "SUPPRESSED_EFFECT_PEEK");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_STATUS_BAR, "SUPPRESSED_EFFECT_STATUS_BAR");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_BADGE, "SUPPRESSED_EFFECT_BADGE");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_AMBIENT, "SUPPRESSED_EFFECT_AMBIENT");
            appendFlag(sb, effects, SUPPRESSED_EFFECT_NOTIFICATION_LIST, "SUPPRESSED_EFFECT_NOTIFICATION_LIST");
            return sb.toString();
        }

        public static String priorityCategoriesToString(int priorityCategories) {
            if (priorityCategories == 0) return "";
            StringBuilder sb = new StringBuilder();
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_REMINDERS, "PRIORITY_CATEGORY_REMINDERS");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_EVENTS, "PRIORITY_CATEGORY_EVENTS");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_MESSAGES, "PRIORITY_CATEGORY_MESSAGES");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_CALLS, "PRIORITY_CATEGORY_CALLS");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_REPEAT_CALLERS, "PRIORITY_CATEGORY_REPEAT_CALLERS");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_ALARMS, "PRIORITY_CATEGORY_ALARMS");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_MEDIA, "PRIORITY_CATEGORY_MEDIA");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_SYSTEM, "PRIORITY_CATEGORY_SYSTEM");
            appendFlag(sb, priorityCategories, PRIORITY_CATEGORY_CONVERSATIONS, "PRIORITY_CATEGORY_CONVERSATIONS");
            return sb.toString();
        }

        public static String prioritySendersToString(int prioritySenders) {
            switch (prioritySenders) {
                case PRIORITY_SENDERS_ANY:
                    return "PRIORITY_SENDERS_ANY";
                case PRIORITY_SENDERS_CONTACTS:
                    return "PRIORITY_SENDERS_CONTACTS";
                case PRIORITY_SENDERS_STARRED:
                    return "PRIORITY_SENDERS_STARRED";
                default:
                    return "UNKNOWN_" + prioritySenders;
            }
        }

        private static void appendFlag(StringBuilder sb, int value, int flag, String name) {
            if ((value & flag) != 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(name);
            }
        }

        public static final Parcelable.Creator<Policy> CREATOR =
                new Parcelable.Creator<Policy>() {
                    @Override
                    public Policy createFromParcel(Parcel source) {
                        return new Policy(source);
                    }

                    @Override
                    public Policy[] newArray(int size) {
                        return new Policy[size];
                    }
                };
    }

    private final String application;
    private final File policy;
    private SQLiteDatabase database;
    private LinkedHashMap<String, Record> records;

    public interface DeliveryFactory {
        NotificationBackend open(String application);
    }

    private final DeliveryFactory deliveryFactory;
    private NotificationBackend desktop;
    private boolean closed;
    private final LinkedHashMap<String, Posted> posted = new LinkedHashMap<String, Posted>();
    private final LinkedHashMap<String, AutomaticZenRule> automaticZenRules =
            new LinkedHashMap<String, AutomaticZenRule>();
    private final LinkedHashMap<String, Integer> automaticZenRuleStates =
            new LinkedHashMap<String, Integer>();
    private Policy notificationPolicy = new Policy(0, 0, 0);
    private long desktopRetry;
    private long postedBytes;

    private static final class Posted {
        final Notification value;
        final long time = System.currentTimeMillis();
        long desktopId;
        final int bytes;

        Posted(Notification value, long desktopId, int bytes) {
            this.value = value;
            this.desktopId = desktopId;
            this.bytes = bytes;
        }
    }

    private static String key(String tag, int id) {
        return id + ":" + (tag == null ? "N" : "T" + tag);
    }

    private static String text(Notification value, String name) {
        CharSequence text = value.extras.getCharSequence(name);
        if (text == null) return "";
        if (text.length() > 8192) throw new IllegalArgumentException("Notification text limit");
        return text.toString();
    }

    private static String summary(Notification value) {
        String title = text(value, Notification.EXTRA_TITLE);
        int max = value.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        int progress = value.extras.getInt(Notification.EXTRA_PROGRESS, 0);
        if (max > 0 && !value.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) {
            title += " (" + (100L * Math.max(0, Math.min(progress, max)) / max) + "%)";
        }
        return title;
    }

    public synchronized android.service.notification.StatusBarNotification[] getActiveNotifications() {
        live();
        ArrayList<android.service.notification.StatusBarNotification> result =
                new ArrayList<android.service.notification.StatusBarNotification>();
        for (Map.Entry<String, Posted> entry : posted.entrySet()) {
            String key = entry.getKey();
            int colon = key.indexOf(':');
            int id = Integer.parseInt(key.substring(0, colon));
            String tag = key.charAt(colon + 1) == 'N' ? null : key.substring(colon + 2);
            Posted value = entry.getValue();
            result.add(new android.service.notification.StatusBarNotification(
                    application, tag, id, value.value.clone(), value.time));
        }
        return result.toArray(new android.service.notification.StatusBarNotification[result.size()]);
    }

    public void notify(int id, Notification value) {
        notify(null, id, value);
    }

    public synchronized void notify(String tag, int id, Notification value) {
        live();
        Objects.requireNonNull(value, "notification");
        if (tag != null && tag.length() > 1024) {
            throw new IllegalArgumentException("Notification tag limit");
        }
        Notification copy = value.clone();
        copy.extras = copy.extras.shirazBroadcastCopy();
        int bytes = copy.extras.shirazBroadcastCopiedBytes();
        if (copy.contentView != null || copy.bigContentView != null || copy.headsUpContentView != null) {
            throw new UnsupportedOperationException("Custom notification views");
        }
        if (copy.getSmallIcon() != null
                && copy.getSmallIcon().getType() != android.graphics.drawable.Icon.TYPE_RESOURCE) {
            throw new UnsupportedOperationException("Notification small icon type");
        }
        String title = summary(copy);
        String body = text(copy, Notification.EXTRA_TEXT);
        if ("1".equals(System.getenv("SHIRAZ_TRACE_NOTIFICATIONS"))) {
            System.err.println("SHIRAZ_NOTIFICATION " + title + " | " + body);
        }
        String key = key(tag, id);
        Posted previous = posted.get(key);
        if (postedBytes - (previous == null ? 0 : previous.bytes) + bytes > 16 * 1024 * 1024L) {
            throw new IllegalStateException("Notification payload limit");
        }
        if (previous != null) {
            copy.flags |= previous.value.flags & Notification.FLAG_FOREGROUND_SERVICE;
        }
        if (previous == null && posted.size() >= 256) {
            throw new IllegalStateException("Posted notification limit");
        }
        String channel = copy.getChannelId();
        if (channel != null) {
            NotificationChannel policy = getNotificationChannel(channel);
            if (policy == null) throw new IllegalArgumentException("Unknown notification channel");
            if (policy.getImportance() == IMPORTANCE_NONE) {
                cancel(tag, id);
                return;
            }
        }
        long desktopId = previous == null ? 0 : previous.desktopId;
        if (previous == null
                || desktopId == 0
                || !title.equals(summary(previous.value))
                || !body.equals(text(previous.value, Notification.EXTRA_TEXT))) {
            if (android.os.SystemClock.uptimeMillis() >= desktopRetry) {
                try {
                    if (desktop == null) desktop = deliveryFactory.open(application);
                    desktopId = desktop.post(
                            desktopId,
                            title,
                            body,
                            copy.priority > 0 ? 2 : 1,
                            (copy.flags & Notification.FLAG_ONGOING_EVENT) != 0 ? 0 : -1,
                            (copy.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0 || copy.sound == null);
                } catch (IllegalStateException unavailable) {
                    disconnectDesktop();
                    desktopId = 0;
                }
            }
        }
        postedBytes += bytes - (previous == null ? 0 : previous.bytes);
        posted.put(key, new Posted(copy, desktopId, bytes));
    }

    public synchronized void notifyAsPackage(
            String targetPackage, String tag, int id, Notification notification) {
        live();
        if (!application.equals(targetPackage)) {
            throw new SecurityException("Not allowed to notify as package " + targetPackage);
        }
        notify(tag, id, notification);
    }

    public synchronized void cancelAsPackage(String targetPackage, String tag, int id) {
        live();
        if (!application.equals(targetPackage)) {
            throw new SecurityException("Not allowed to cancel as package " + targetPackage);
        }
        cancel(tag, id);
    }

    /** Server IDs belong to one connection; never replace or cancel them after reconnect. */
    private void disconnectDesktop() {
        NotificationBackend old = desktop;
        desktop = null;
        for (Posted value : posted.values()) value.desktopId = 0;
        desktopRetry = android.os.SystemClock.uptimeMillis() + 30000;
        if (old != null) old.close();
    }

    /** Foreground progress remains visible in the app window even without a desktop daemon. */
    public synchronized String shirazForegroundSummary() {
        live();
        String result = "";
        for (Posted p : posted.values()) {
            if ((p.value.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) result = summary(p.value);
        }
        return result.length() > 512 ? result.substring(0, 512) : result;
    }

    public synchronized Notification shirazNotification(int id) {
        live();
        Posted p = posted.get(key(null, id));
        return p == null ? null : p.value.clone();
    }

    public synchronized void shirazDetachForeground(int id) {
        Posted p = posted.get(key(null, id));
        if (p != null) p.value.flags &= ~Notification.FLAG_FOREGROUND_SERVICE;
    }

    private static final class Record {
        final NotificationChannel channel;
        final boolean deleted;

        Record(NotificationChannel channel, boolean deleted) {
            this.channel = channel;
            this.deleted = deleted;
        }
    }

    private NotificationManager(String application, File policy, DeliveryFactory factory) {
        this.application = Objects.requireNonNull(application);
        this.policy = Objects.requireNonNull(policy);
        this.deliveryFactory = Objects.requireNonNull(factory);
    }

    public static NotificationManager shirazForProcess(String application, File policy) {
        return new NotificationManager(application, policy, new DeliveryFactory() {
            public NotificationBackend open(String name) {
                return new DesktopNotifications(name);
            }
        });
    }

    public static NotificationManager shirazForProcess(
            String application, File policy, DeliveryFactory factory) {
        return new NotificationManager(application, policy, factory);
    }

    private static void restrict(NotificationChannel channel) {
        channel.setBypassDnd(false);
        channel.setLockscreenVisibility(-1000);
        channel.setAllowBubbles(false);
    }

    private void live() {
        if (closed) throw new IllegalStateException("Notification service is closed");
    }

    private void openPolicy() {
        live();
        if (database != null) return;
        SQLiteDatabase opened = SQLiteDatabase.openOrCreateDatabase(policy, null);
        try {
            opened.beginTransaction();
            try {
                int version = opened.getVersion();
                if (version == 0) {
                    opened.execSQL(
                            "CREATE TABLE channels (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL,"
                                    + " deleted INTEGER NOT NULL CHECK(deleted IN (0,1)))");
                    opened.setVersion(1);
                    version = 1;
                }
                if (version == 1) {
                    opened.execSQL(
                            "CREATE TABLE channel_groups (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL,"
                                    + " description TEXT)");
                    opened.setVersion(2);
                } else if (version != 2) {
                    throw new IllegalStateException("Unsupported notification policy database version");
                }
                opened.setTransactionSuccessful();
            } finally {
                opened.endTransaction();
            }
            database = opened;
            opened = null;
        } finally {
            if (opened != null) opened.close();
        }
    }

    private LinkedHashMap<String, Record> records() {
        live();
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            if (database.getVersion() != 2) {
                throw new IllegalStateException("Unsupported notification policy database version");
            }
            LinkedHashMap<String, Record> loaded = new LinkedHashMap<String, Record>();
            try (Cursor rows = database.rawQuery(
                    "SELECT id,payload,deleted FROM channels ORDER BY rowid LIMIT 513", null)) {
                while (rows.moveToNext()) {
                    if (loaded.size() >= 512) {
                        throw new IllegalStateException("Notification channel limit exceeded");
                    }
                    NotificationChannel channel = NotificationChannel.decode(rows.getBlob(1));
                    restrict(channel);
                    if (loaded.containsKey(channel.getId())
                            || !channel.getId().equals(rows.getString(0))
                            || rows.isNull(2)
                            || rows.getInt(2) < 0
                            || rows.getInt(2) > 1) {
                        throw new IllegalStateException("Invalid channel policy row");
                    }
                    loaded.put(channel.getId(), new Record(channel, rows.getInt(2) != 0));
                }
            }
            records = loaded;
            return loaded;
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    private void save(Record record) {
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            ContentValues value = new ContentValues();
            value.put("id", record.channel.getId());
            value.put("payload", record.channel.encode());
            value.put("deleted", record.deleted ? 1 : 0);
            if (database.insertWithOnConflict("channels", null, value, SQLiteDatabase.CONFLICT_REPLACE) < 0) {
                throw new IllegalStateException("Notification policy write did not insert a row");
            }
            records.put(record.channel.getId(), record);
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    public synchronized void createNotificationChannel(NotificationChannel channel) {
        Objects.requireNonNull(channel, "channel");
        channel = channel.copy();
        channel.validate();
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            database.beginTransaction();
            try {
                if (channel.getGroup() != null && !groups().containsKey(channel.getGroup())) {
                    throw new IllegalArgumentException("Notification channel group does not exist");
                }
                Record previous = records().get(channel.getId());
                if (previous == null) {
                    if (records.size() >= 512) {
                        throw new IllegalStateException("Notification channel limit exceeded");
                    }
                    restrict(channel);
                } else {
                    NotificationChannel retained = previous.channel.copy();
                    if (retained.getGroup() == null) retained.setGroup(channel.getGroup());
                    retained.setName(channel.getName());
                    retained.setDescription(channel.getDescription());
                    retained.setBlockable(channel.isBlockable());
                    retained.setImportance(Math.min(retained.getImportance(), channel.getImportance()));
                    channel = retained;
                }
                if (previous == null || previous.deleted || !previous.channel.equals(channel)) {
                    save(new Record(channel, false));
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    public void createNotificationChannels(List<NotificationChannel> channels) {
        Objects.requireNonNull(channels);
        for (NotificationChannel channel : channels) createNotificationChannel(channel);
    }

    public synchronized NotificationChannel getNotificationChannel(String id) {
        Record value = records().get(Objects.requireNonNull(id));
        return value == null || value.deleted ? null : value.channel.copy();
    }

    public synchronized NotificationChannel getNotificationChannel(
            String channelId, String conversationId) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(conversationId, "conversationId");
        NotificationChannel exact = getNotificationChannel(channelId + " : " + conversationId);
        return exact != null ? exact : getNotificationChannel(channelId);
    }

    public synchronized List<NotificationChannel> getNotificationChannels() {
        List<NotificationChannel> result = new ArrayList<NotificationChannel>();
        for (Record value : records().values()) {
            if (!value.deleted) result.add(value.channel.copy());
        }
        return result;
    }

    public synchronized void deleteNotificationChannel(String id) {
        Objects.requireNonNull(id);
        if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(id)) {
            throw new IllegalArgumentException("Cannot delete default channel");
        }
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            database.beginTransaction();
            try {
                Record value = records().get(id);
                if (value != null && !value.deleted) save(new Record(value.channel, true));
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    private LinkedHashMap<String, NotificationChannelGroup> groups() {
        openPolicy();
        LinkedHashMap<String, NotificationChannelGroup> result =
                new LinkedHashMap<String, NotificationChannelGroup>();
        try (Cursor rows = database.rawQuery(
                "SELECT id,name,description FROM channel_groups ORDER BY rowid LIMIT 513", null)) {
            while (rows.moveToNext()) {
                if (result.size() >= 512) {
                    throw new IllegalStateException("Notification group limit exceeded");
                }
                String id = rows.getString(0);
                String name = rows.getString(1);
                String description = rows.getString(2);
                if (id == null
                        || name == null
                        || id.length() > 1000
                        || name.length() > 1000
                        || (description != null && description.length() > 1000)) {
                    throw new IllegalStateException("Invalid group policy row");
                }
                NotificationChannelGroup group = new NotificationChannelGroup(id, name);
                group.setDescription(description);
                group.validate();
                if (result.put(id, group) != null) {
                    throw new IllegalStateException("Duplicate group policy row");
                }
            }
        }
        return result;
    }

    public synchronized void createNotificationChannelGroup(NotificationChannelGroup group) {
        Objects.requireNonNull(group);
        group = group.clone();
        group.validate();
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            database.beginTransaction();
            try {
                Map<String, NotificationChannelGroup> existing = groups();
                if (!existing.containsKey(group.getId()) && existing.size() >= 512) {
                    throw new IllegalStateException("Notification group limit exceeded");
                }
                ContentValues values = new ContentValues();
                values.put("id", group.getId());
                values.put("name", group.getName().toString());
                values.put("description", group.getDescription());
                if (database.insertWithOnConflict(
                                "channel_groups", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        < 0) {
                    throw new IllegalStateException("Notification group write failed");
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    public void createNotificationChannelGroups(List<NotificationChannelGroup> groups) {
        Objects.requireNonNull(groups);
        for (NotificationChannelGroup group : groups) createNotificationChannelGroup(group);
    }

    public synchronized List<NotificationChannelGroup> getNotificationChannelGroups() {
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            database.beginTransaction();
            try {
                LinkedHashMap<String, NotificationChannelGroup> result = groups();
                for (Record record : records().values()) {
                    if (!record.deleted) {
                        NotificationChannelGroup group = result.get(record.channel.getGroup());
                        if (group != null) group.add(record.channel);
                    }
                }
                database.setTransactionSuccessful();
                return new ArrayList<NotificationChannelGroup>(result.values());
            } finally {
                database.endTransaction();
            }
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    public synchronized NotificationChannelGroup getNotificationChannelGroup(String id) {
        Objects.requireNonNull(id);
        for (NotificationChannelGroup group : getNotificationChannelGroups()) {
            if (id.equals(group.getId())) return group;
        }
        return null;
    }

    public synchronized void deleteNotificationChannelGroup(String id) {
        Objects.requireNonNull(id);
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            openPolicy();
            database.beginTransaction();
            try {
                if (groups().containsKey(id)) {
                    for (Record record : records().values()) {
                        if (!record.deleted && id.equals(record.channel.getGroup())) {
                            save(new Record(record.channel, true));
                        }
                    }
                    if (database.delete("channel_groups", "id=?", new String[] {id}) != 1) {
                        throw new IllegalStateException("Notification group deletion failed");
                    }
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    // Unknown-ID cancellation does not open a desktop connection or erase policy.
    public void cancel(int id) {
        cancel(null, id);
    }

    public synchronized void cancel(String tag, int id) {
        live();
        Posted p = posted.remove(key(tag, id));
        if (p != null) postedBytes -= p.bytes;
        if (p != null && p.desktopId != 0 && desktop != null) {
            try {
                desktop.cancel(p.desktopId);
            } catch (IllegalStateException unavailable) {
                disconnectDesktop();
            }
        }
    }

    public synchronized void cancelAll() {
        live();
        for (Posted p : posted.values()) {
            if (p.desktopId != 0 && desktop != null) {
                try {
                    desktop.cancel(p.desktopId);
                } catch (IllegalStateException unavailable) {
                    disconnectDesktop();
                }
            }
        }
        posted.clear();
        postedBytes = 0;
    }

    public synchronized void setNotificationDelegate(String delegate) {
        live();
        if (delegate != null && !application.equals(delegate)) {
            throw new SecurityException("Cross-package notification delegation is not allowed");
        }
    }

    /** Notifications belong to this session; no other package is a delegate. */
    public synchronized String getNotificationDelegate() {
        live();
        return null;
    }

    public synchronized boolean canNotifyAsPackage(String pkg) {
        live();
        return application.equals(pkg);
    }

    public synchronized boolean canUseFullScreenIntent() {
        live();
        return false;
    }

    public synchronized Policy getConsolidatedNotificationPolicy() {
        live();
        return notificationPolicy;
    }

    public synchronized boolean areAutomaticZenRulesUserManaged() {
        live();
        return false;
    }

    public synchronized Map<String, AutomaticZenRule> getAutomaticZenRules() {
        live();
        return new LinkedHashMap<String, AutomaticZenRule>(automaticZenRules);
    }

    public synchronized AutomaticZenRule getAutomaticZenRule(String id) {
        live();
        Objects.requireNonNull(id, "id");
        return automaticZenRules.get(id);
    }

    public synchronized String addAutomaticZenRule(AutomaticZenRule automaticZenRule) {
        live();
        Objects.requireNonNull(automaticZenRule, "automaticZenRule");
        String id = UUID.randomUUID().toString();
        automaticZenRules.put(id, automaticZenRule);
        automaticZenRuleStates.put(
                id,
                automaticZenRule.isEnabled()
                        ? AUTOMATIC_RULE_STATUS_ENABLED
                        : AUTOMATIC_RULE_STATUS_DISABLED);
        return id;
    }

    public synchronized boolean updateAutomaticZenRule(String id, AutomaticZenRule automaticZenRule) {
        live();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(automaticZenRule, "automaticZenRule");
        if (!automaticZenRules.containsKey(id)) return false;
        automaticZenRules.put(id, automaticZenRule);
        automaticZenRuleStates.put(
                id,
                automaticZenRule.isEnabled()
                        ? AUTOMATIC_RULE_STATUS_ENABLED
                        : AUTOMATIC_RULE_STATUS_DISABLED);
        return true;
    }

    public synchronized int getAutomaticZenRuleState(String id) {
        live();
        Objects.requireNonNull(id, "id");
        Integer status = automaticZenRuleStates.get(id);
        return status != null ? status.intValue() : AUTOMATIC_RULE_STATUS_UNKNOWN;
    }

    public synchronized void setAutomaticZenRuleState(
            String id, android.service.notification.Condition condition) {
        live();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        if (automaticZenRules.containsKey(id)) {
            automaticZenRuleStates.put(
                    id,
                    condition.state == android.service.notification.Condition.STATE_TRUE
                            ? AUTOMATIC_RULE_STATUS_ACTIVATED
                            : AUTOMATIC_RULE_STATUS_DEACTIVATED);
        }
    }

    public synchronized boolean removeAutomaticZenRule(String id) {
        live();
        Objects.requireNonNull(id, "id");
        automaticZenRuleStates.remove(id);
        return automaticZenRules.remove(id) != null;
    }

    public synchronized boolean areNotificationsEnabled() {
        live();
        try {
            if (desktop == null) desktop = deliveryFactory.open(application);
            desktop.capabilities();
            desktopRetry = 0;
            return true;
        } catch (IllegalStateException unavailable) {
            disconnectDesktop();
            return false;
        }
    }

    public int getImportance() {
        return areNotificationsEnabled() ? IMPORTANCE_DEFAULT : IMPORTANCE_NONE;
    }

    public synchronized boolean areBubblesAllowed() {
        live();
        return false;
    }

    @Deprecated
    public synchronized boolean areBubblesEnabled() {
        live();
        return false;
    }

    public synchronized int getBubblePreference() {
        live();
        return BUBBLE_PREFERENCE_NONE;
    }

    public synchronized boolean areNotificationsPaused() {
        live();
        return false;
    }

    public synchronized boolean isNotificationPolicyAccessGranted() {
        live();
        return false;
    }

    public synchronized boolean isNotificationListenerAccessGranted(ComponentName listener) {
        live();
        Objects.requireNonNull(listener, "listener");
        return false;
    }

    public synchronized boolean shouldHideSilentStatusBarIcons() {
        live();
        return false;
    }

    public synchronized Policy getNotificationPolicy() {
        live();
        return notificationPolicy;
    }

    public synchronized void setNotificationPolicy(Policy policy) {
        live();
        notificationPolicy = Objects.requireNonNull(policy, "policy");
    }

    public final synchronized int getCurrentInterruptionFilter() {
        live();
        throw new UnsupportedOperationException("Desktop DND policy is not implemented");
    }

    public final synchronized void setInterruptionFilter(int interruptionFilter) {
        live();
        throw new UnsupportedOperationException("Desktop DND policy is not implemented");
    }

    public synchronized boolean matchesCallFilter(Uri uri) {
        live();
        return true;
    }

    public synchronized void shirazClose() {
        if (closed) return;
        cancelAll();
        closed = true;
        try {
            if (desktop != null) desktop.close();
        } finally {
            if (database != null) database.close();
        }
    }
}
