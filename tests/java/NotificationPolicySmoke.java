// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.database.sqlite.*;
import android.database.Cursor;
import android.os.StrictMode;
import dalvik.system.BlockGuard;
import java.io.File;
import java.nio.file.*;
import java.util.*;

public final class NotificationPolicySmoke {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static long descriptors() throws Exception { try (java.util.stream.Stream<Path> paths = Files.list(Paths.get("/proc/self/fd"))) { return paths.count(); } }
    private static NotificationManager manager(File file) { return NotificationManager.shirazForProcess("org.shiraz.fixture", file); }
    public static void main(String[] args) throws Exception {
        check(Notification.AUDIO_ATTRIBUTES_DEFAULT.getUsage()==android.media.AudioAttributes.USAGE_NOTIFICATION&&Notification.AUDIO_ATTRIBUTES_DEFAULT.getContentType()==android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION,"Android notification sound defaults");
        check(new Notification().audioAttributes.equals(Notification.AUDIO_ATTRIBUTES_DEFAULT),"Notification inherits audio defaults");
        Notification styled=new Notification.Builder(new android.content.ContextWrapper(null)).setStyle(new Notification.BigTextStyle().bigText("details").setBigContentTitle("heading")).build();
        check("details".equals(styled.extras.getCharSequence("android.bigText"))&&"heading".equals(styled.extras.getCharSequence("android.title.big")),"BigTextStyle subtype and values");
        NotificationChannel defaults=new NotificationChannel("defaults","Defaults",3);check(defaults.getAudioAttributes().equals(Notification.AUDIO_ATTRIBUTES_DEFAULT)&&android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.equals(defaults.getSound()),"Channel defaults");defaults.setSound(null,null);check(defaults.getSound()==null&&defaults.getAudioAttributes()==null,"Explicit silent channel");
        File file = new File(args[0]); Files.deleteIfExists(file.toPath());
        NotificationManager first = manager(file);
        first.cancel(7);first.cancel(null,7);first.cancel("route",7);first.cancel("other",Integer.MIN_VALUE);first.cancelAll();
        check(!file.exists(),"Empty cancellation must not create channel storage");
        if (args.length > 1) {
            boolean available = Boolean.parseBoolean(args[1]);
            check(first.areNotificationsEnabled() == available, "real desktop availability");
            check(first.getImportance() == (available ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_NONE), "availability importance");
            first.shirazClose();
            System.out.println("NOTIFICATION_POLICY_AVAILABILITY_OK"); return;
        }
        check(first.getNotificationChannels().isEmpty(), "fresh channel policy");
        NotificationChannel channel = new NotificationChannel("main", "initial", 4);
        channel.enableLights(true); channel.setShowBadge(false); channel.setVibrationPattern(new long[]{0,5,10});
        first.createNotificationChannel(channel);
        NotificationChannel update = new NotificationChannel("main", "lower", 2);
        first.createNotificationChannel(update);
        check(first.getNotificationChannel("main").getImportance() == 2, "importance may decrease");
        update.setImportance(5); first.createNotificationChannel(update);
        check(first.getNotificationChannel("main").getImportance() == 2 && first.getNotificationChannel("main").shouldShowLights(), "importance cannot increase and existing fields retained");
        first.cancel("main",1);first.cancelAll();check(first.getNotificationChannel("main").getImportance()==2,"Cancellation preserves channels");
        first.deleteNotificationChannel("main"); first.shirazClose();
        try{first.cancel("route",7);throw new AssertionError("Closed cancellation");}catch(IllegalStateException expected){}
        try{first.cancelAll();throw new AssertionError("Closed cancelAll");}catch(IllegalStateException expected){}
        first = manager(file); check(first.getNotificationChannels().isEmpty(), "deleted channel persists");
        first.createNotificationChannel(update);
        check(first.getNotificationChannel("main").getImportance() == 2 && !first.getNotificationChannel("main").canShowBadge(), "restored policy retains settings across reopen");
        StrictMode.ThreadPolicy original = StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyDeath().build());
        try {
            first.createNotificationChannel(new NotificationChannel("guard", "guard", 1));
            first.getNotificationChannels();
            try { BlockGuard.getThreadPolicy().onReadFromDisk(); throw new AssertionError("service lost caller read policy"); } catch (RuntimeException expected) { }
            try { BlockGuard.getThreadPolicy().onWriteToDisk(); throw new AssertionError("service lost caller write policy"); } catch (RuntimeException expected) { }
        } finally { StrictMode.setThreadPolicy(original); }
        first.shirazClose(); first.shirazClose();
        try { first.getNotificationChannels(); throw new AssertionError("closed service accepted query"); } catch (IllegalStateException expected) { }
        long before = descriptors();
        for (int count = 0; count < 32; ++count) { NotificationManager current = manager(file); check(current.getNotificationChannels().size() == 2, "persistent channel list"); current.shirazClose(); }
        check(before == descriptors(), "policy database descriptor lifetime");
        NotificationManager failedWrite = manager(file); failedWrite.getNotificationChannels();
        SQLiteDatabase deny = SQLiteDatabase.openOrCreateDatabase(file, null);
        deny.execSQL("CREATE TRIGGER deny_channel BEFORE INSERT ON channels BEGIN SELECT RAISE(ABORT,'fixture write failure'); END");
        try { failedWrite.createNotificationChannel(new NotificationChannel("rejected", "rejected", 2)); throw new AssertionError("accepted failed policy write"); }
        catch (SQLiteConstraintException expected) { }
        check(failedWrite.getNotificationChannel("rejected") == null, "failed write published channel");
        deny.execSQL("DROP TRIGGER deny_channel");
        deny.execSQL("CREATE TRIGGER deny_channel BEFORE INSERT ON channels BEGIN SELECT RAISE(IGNORE); END");
        try { failedWrite.createNotificationChannel(new NotificationChannel("ignored", "ignored", 2)); throw new AssertionError("accepted ignored policy write"); }
        catch (IllegalStateException expected) { }
        check(failedWrite.getNotificationChannel("ignored") == null, "ignored write published channel");
        deny.execSQL("DROP TRIGGER deny_channel"); deny.close(); failedWrite.shirazClose();
        check(before == descriptors(), "failed write descriptor lifetime");
        // Invalid schemas and corrupt payloads must never turn into a fake empty list.
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null); db.setVersion(99); db.close();
        for (int count = 0; count < 16; ++count) {
            NotificationManager current = manager(file);
            try { current.getNotificationChannels(); throw new AssertionError("accepted unknown policy version"); } catch (IllegalStateException expected) { } finally { current.shirazClose(); }
        }
        check(before == descriptors(), "failed policy open descriptor lifetime");
        db = SQLiteDatabase.openOrCreateDatabase(file, null); db.setVersion(1);
        byte[] healthy; String healthyId;
        try (Cursor row = db.rawQuery("SELECT id,payload FROM channels LIMIT 1", null)) { check(row.moveToFirst(), "healthy policy fixture"); healthyId = row.getString(0); healthy = row.getBlob(1); }
        db.execSQL("UPDATE channels SET payload=X'00'"); db.close();
        NotificationManager corrupt = manager(file);
        try { corrupt.getNotificationChannels(); throw new AssertionError("accepted corrupt channel"); } catch (IllegalStateException expected) { } finally { corrupt.shirazClose(); }
        db = SQLiteDatabase.openOrCreateDatabase(file, null); db.execSQL("DROP TABLE channels"); db.execSQL("CREATE TABLE channels(id,payload,deleted)");
        db.execSQL("INSERT INTO channels VALUES(?,?,0)", new Object[]{healthyId, healthy}); db.execSQL("INSERT INTO channels VALUES(?,?,0)", new Object[]{healthyId, healthy}); db.close();
        NotificationManager duplicate = manager(file);
        try { duplicate.getNotificationChannels(); throw new AssertionError("accepted duplicate channel rows"); } catch (IllegalStateException expected) { } finally { duplicate.shirazClose(); }
        db = SQLiteDatabase.openOrCreateDatabase(file, null); db.execSQL("DELETE FROM channels"); db.execSQL("INSERT INTO channels VALUES(?,?,NULL)", new Object[]{healthyId, healthy}); db.close();
        NotificationManager nullState = manager(file);
        try { nullState.getNotificationChannels(); throw new AssertionError("accepted null channel state"); } catch (IllegalStateException expected) { } finally { nullState.shirazClose(); }
        check(before == descriptors(), "corrupt policy descriptor lifetime");
        Files.delete(file.toPath());
        System.out.println("NOTIFICATION_POLICY_PERSISTENCE_AND_LIFETIME_OK");
    }
}
