// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.StrictMode;
import java.io.File;
import java.util.*;
import shiraz.runtime.DesktopNotifications;

/** Session-owned notification delivery with persistent channel policy. */
public class NotificationManager {
    public static final int IMPORTANCE_UNSPECIFIED = -1000, IMPORTANCE_NONE = 0, IMPORTANCE_MIN = 1,
        IMPORTANCE_LOW = 2, IMPORTANCE_DEFAULT = 3, IMPORTANCE_HIGH = 4, IMPORTANCE_MAX = 5;
    public static final int INTERRUPTION_FILTER_UNKNOWN = 0, INTERRUPTION_FILTER_ALL = 1,
        INTERRUPTION_FILTER_PRIORITY = 2, INTERRUPTION_FILTER_NONE = 3, INTERRUPTION_FILTER_ALARMS = 4;
    private final String application;
    private final File policy;
    private SQLiteDatabase database;
    private LinkedHashMap<String, Record> records;
    private DesktopNotifications desktop;
    private boolean closed;
    private final LinkedHashMap<String,Posted> posted=new LinkedHashMap<String,Posted>();
    private long desktopRetry;private long postedBytes;
    private static final class Posted { final Notification value;final long time=System.currentTimeMillis();final long desktopId;final int bytes;Posted(Notification value,long desktopId,int bytes){this.value=value;this.desktopId=desktopId;this.bytes=bytes;} }
    private static String key(String tag,int id){return id+":"+(tag==null?"N":"T"+tag);}
    private static String text(Notification value,String name){CharSequence text=value.extras.getCharSequence(name);if(text==null)return "";if(text.length()>8192)throw new IllegalArgumentException("Notification text limit");return text.toString();}
    private static String summary(Notification value){String title=text(value,Notification.EXTRA_TITLE);int max=value.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0),progress=value.extras.getInt(Notification.EXTRA_PROGRESS,0);if(max>0&&!value.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false))title+=" ("+(100L*Math.max(0,Math.min(progress,max))/max)+"%)";return title;}
    public synchronized android.service.notification.StatusBarNotification[] getActiveNotifications(){
        live();java.util.ArrayList<android.service.notification.StatusBarNotification> result=new java.util.ArrayList<>();
        for(Map.Entry<String,Posted> entry:posted.entrySet()){String key=entry.getKey();int colon=key.indexOf(':');int id=Integer.parseInt(key.substring(0,colon));String tag=key.charAt(colon+1)=='N'?null:key.substring(colon+2);Posted value=entry.getValue();result.add(new android.service.notification.StatusBarNotification(application,tag,id,value.value.clone(),value.time));}
        return result.toArray(new android.service.notification.StatusBarNotification[result.size()]);
    }
    public void notify(int id,Notification value){notify(null,id,value);}
    public synchronized void notify(String tag,int id,Notification value){
        live();Objects.requireNonNull(value,"notification");if(tag!=null&&tag.length()>1024)throw new IllegalArgumentException("Notification tag limit");
        Notification copy=value.clone();copy.extras=copy.extras.shirazBroadcastCopy();int bytes=copy.extras.shirazBroadcastCopiedBytes();
        if(copy.contentView!=null||copy.bigContentView!=null||copy.headsUpContentView!=null)throw new UnsupportedOperationException("Custom notification views");
        if(copy.getSmallIcon()!=null&&copy.getSmallIcon().getType()!=android.graphics.drawable.Icon.TYPE_RESOURCE)throw new UnsupportedOperationException("Notification small icon type");
        String title=summary(copy),body=text(copy,Notification.EXTRA_TEXT);
        if("1".equals(System.getenv("SHIRAZ_TRACE_NOTIFICATIONS")))System.err.println("SHIRAZ_NOTIFICATION "+title+" | "+body);
        String key=key(tag,id);Posted previous=posted.get(key);
        if(postedBytes-(previous==null?0:previous.bytes)+bytes>16*1024*1024L)throw new IllegalStateException("Notification payload limit");
        if(previous!=null)copy.flags|=previous.value.flags&Notification.FLAG_FOREGROUND_SERVICE;
        if(previous==null&&posted.size()>=256)throw new IllegalStateException("Posted notification limit");
        String channel=copy.getChannelId();if(channel!=null){NotificationChannel policy=getNotificationChannel(channel);if(policy==null)throw new IllegalArgumentException("Unknown notification channel");if(policy.getImportance()==IMPORTANCE_NONE){cancel(tag,id);return;}}
        long desktopId=previous==null?0:previous.desktopId;
        if(previous==null||!title.equals(summary(previous.value))||!body.equals(text(previous.value,Notification.EXTRA_TEXT))){
            if(android.os.SystemClock.uptimeMillis()>=desktopRetry)try{
                if(desktop==null)desktop=new DesktopNotifications(application);
                desktopId=desktop.post(desktopId,title,body,copy.priority>0?2:1,(copy.flags&Notification.FLAG_ONGOING_EVENT)!=0?0:-1,(copy.flags&Notification.FLAG_ONLY_ALERT_ONCE)!=0||copy.sound==null);
            }catch(IllegalStateException unavailable){if(desktop!=null){desktop.close();desktop=null;}desktopId=0;desktopRetry=android.os.SystemClock.uptimeMillis()+30000;}
        }
        postedBytes+=bytes-(previous==null?0:previous.bytes);posted.put(key,new Posted(copy,desktopId,bytes));
    }
    /** Foreground progress remains visible in the app window even without a desktop daemon. */
    public synchronized String shirazForegroundSummary(){live();String result="";for(Posted p:posted.values())if((p.value.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0)result=summary(p.value);return result.length()>512?result.substring(0,512):result;}
    public synchronized Notification shirazNotification(int id){live();Posted p=posted.get(key(null,id));return p==null?null:p.value.clone();}
    public synchronized void shirazDetachForeground(int id){Posted p=posted.get(key(null,id));if(p!=null)p.value.flags&=~Notification.FLAG_FOREGROUND_SERVICE;}

    private static final class Record {
        final NotificationChannel channel; final boolean deleted;
        Record(NotificationChannel channel, boolean deleted) { this.channel = channel; this.deleted = deleted; }
    }
    private NotificationManager(String application, File policy) { this.application = Objects.requireNonNull(application); this.policy = Objects.requireNonNull(policy); }
    public static NotificationManager shirazForProcess(String application, File policy) { return new NotificationManager(application, policy); }
    private static void restrict(NotificationChannel channel) { channel.setBypassDnd(false); channel.setLockscreenVisibility(-1000); channel.setAllowBubbles(false); }
    private void live() { if (closed) throw new IllegalStateException("Notification service is closed"); }
    private LinkedHashMap<String, Record> records() {
        live(); if (records != null) return records;
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        SQLiteDatabase opened = null;
        try {
            opened = SQLiteDatabase.openOrCreateDatabase(policy, null);
            int version = opened.getVersion();
            if (version == 0) {
                opened.beginTransaction();
                try { opened.execSQL("CREATE TABLE channels (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL, deleted INTEGER NOT NULL CHECK(deleted IN (0,1)))"); opened.setVersion(1); opened.setTransactionSuccessful(); }
                finally { opened.endTransaction(); }
            } else if (version != 1) throw new IllegalStateException("Unsupported notification policy database version");
            LinkedHashMap<String, Record> loaded = new LinkedHashMap<String, Record>();
            try (Cursor rows = opened.rawQuery("SELECT id,payload,deleted FROM channels ORDER BY rowid LIMIT 513", null)) {
                while (rows.moveToNext()) {
                    if (loaded.size() >= 512) throw new IllegalStateException("Notification channel limit exceeded");
                    NotificationChannel channel = NotificationChannel.decode(rows.getBlob(1)); restrict(channel);
                    if (loaded.containsKey(channel.getId()) || !channel.getId().equals(rows.getString(0)) || rows.isNull(2) || rows.getInt(2) < 0 || rows.getInt(2) > 1) throw new IllegalStateException("Invalid channel policy row");
                    loaded.put(channel.getId(), new Record(channel, rows.getInt(2) != 0));
                }
            }
            database = opened; opened = null; records = loaded; return loaded;
        } finally { try { if (opened != null) opened.close(); } finally { StrictMode.setThreadPolicy(old); } }
    }
    private void save(Record record) {
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        try {
            ContentValues value = new ContentValues(); value.put("id", record.channel.getId()); value.put("payload", record.channel.encode()); value.put("deleted", record.deleted ? 1 : 0);
            if (database.insertWithOnConflict("channels", null, value, SQLiteDatabase.CONFLICT_REPLACE) < 0) throw new IllegalStateException("Notification policy write did not insert a row");
            records.put(record.channel.getId(), record);
        } finally { StrictMode.setThreadPolicy(old); }
    }
    public synchronized void createNotificationChannel(NotificationChannel channel) {
        Objects.requireNonNull(channel, "channel"); channel = channel.copy(); channel.validate();
        Record previous = records().get(channel.getId());
        if (previous == null) {
            if (records.size() >= 512) throw new IllegalStateException("Notification channel limit exceeded");
            restrict(channel);
        } else {
            NotificationChannel retained = previous.channel.copy();
            retained.setName(channel.getName()); retained.setDescription(channel.getDescription()); retained.setBlockable(channel.isBlockable());
            retained.setImportance(Math.min(retained.getImportance(), channel.getImportance()));
            channel = retained;
        }
        if (previous == null || previous.deleted || !previous.channel.equals(channel)) save(new Record(channel, false));
    }
    public void createNotificationChannels(List<NotificationChannel> channels) { Objects.requireNonNull(channels); for (NotificationChannel channel : channels) createNotificationChannel(channel); }
    public synchronized NotificationChannel getNotificationChannel(String id) { Record value = records().get(Objects.requireNonNull(id)); return value == null || value.deleted ? null : value.channel.copy(); }
    public synchronized List<NotificationChannel> getNotificationChannels() {
        List<NotificationChannel> result = new ArrayList<NotificationChannel>();
        for (Record value : records().values()) if (!value.deleted) result.add(value.channel.copy()); return result;
    }
    public synchronized void deleteNotificationChannel(String id) {
        Objects.requireNonNull(id); if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(id)) throw new IllegalArgumentException("Cannot delete default channel");
        Record value = records().get(id); if (value != null && !value.deleted) save(new Record(value.channel, true));
    }
    // Unknown-ID cancellation does not open a desktop connection or erase policy.
    public void cancel(int id){cancel(null,id);}
    public synchronized void cancel(String tag,int id){live();Posted p=posted.remove(key(tag,id));if(p!=null)postedBytes-=p.bytes;if(p!=null&&p.desktopId!=0&&desktop!=null)try{desktop.cancel(p.desktopId);}catch(IllegalStateException unavailable){desktop.close();desktop=null;}}
    public synchronized void cancelAll(){live();for(Posted p:posted.values())if(p.desktopId!=0&&desktop!=null)try{desktop.cancel(p.desktopId);}catch(IllegalStateException unavailable){desktop.close();desktop=null;}posted.clear();postedBytes=0;}
    public synchronized boolean areNotificationsEnabled() {
        live();
        try { if (desktop == null) desktop = new DesktopNotifications(application); desktop.capabilities(); return true; }
        catch (IllegalStateException unavailable) { if (desktop != null) { desktop.close(); desktop = null; } return false; }
    }
    public int getImportance() { return areNotificationsEnabled() ? IMPORTANCE_DEFAULT : IMPORTANCE_NONE; }
    public synchronized boolean isNotificationPolicyAccessGranted() { live(); return false; }
    public synchronized int getCurrentInterruptionFilter() { live(); throw new UnsupportedOperationException("Desktop DND policy is not implemented"); }
    public synchronized void shirazClose() {
        if (closed) return; cancelAll(); closed = true;
        try { if (desktop != null) desktop.close(); }
        finally { if (database != null) database.close(); }
    }
}
