// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.nio.file.Files;
public final class NotificationGroupSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File file=Files.createTempFile("notification-groups",".db").toFile();
        SQLiteDatabase legacy=SQLiteDatabase.openOrCreateDatabase(file,null);
        legacy.execSQL("CREATE TABLE channels (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL, deleted INTEGER NOT NULL CHECK(deleted IN (0,1)))");legacy.setVersion(1);legacy.close();
        NotificationManager first=NotificationManager.shirazForProcess("fixture",file),second=NotificationManager.shirazForProcess("fixture",file);
        try{
            check(first.getNotificationChannelGroups().isEmpty(),"v1 migration");
            NotificationChannelGroup group=new NotificationChannelGroup("account","Account");group.setDescription("original");first.createNotificationChannelGroup(group);group.setDescription("mutated");
            check("original".equals(second.getNotificationChannelGroup("account").getDescription()),"shared persisted snapshot");
            NotificationChannel channel=new NotificationChannel("messages","Messages",2);channel.setGroup("missing");
            try{first.createNotificationChannel(channel);throw new AssertionError("missing group accepted");}catch(IllegalArgumentException expected){}
            channel.setGroup("account");first.createNotificationChannel(channel);
            check(second.getNotificationChannelGroup("account").getChannels().size()==1,"membership invisible");
            NotificationChannelGroup renamed=new NotificationChannelGroup("account","Renamed");second.createNotificationChannelGroup(renamed);
            check("Renamed".contentEquals(first.getNotificationChannelGroup("account").getName()),"rename invisible");
            second.getNotificationChannelGroup("account").getChannels().get(0).setImportance(0);
            check(first.getNotificationChannel("messages").getImportance()==2,"query leaked mutable state");
            SQLiteDatabase deny=SQLiteDatabase.openOrCreateDatabase(file,null);deny.execSQL("CREATE TRIGGER deny_group BEFORE DELETE ON channel_groups BEGIN SELECT RAISE(ABORT,'denied'); END");
            try{second.deleteNotificationChannelGroup("account");throw new AssertionError("failed deletion accepted");}catch(RuntimeException expected){}
            check(first.getNotificationChannel("messages")!=null,"failed group deletion partially deleted channels");
            deny.execSQL("DROP TRIGGER deny_group");deny.close();
            second.deleteNotificationChannelGroup("account");
            check(first.getNotificationChannel("messages")==null&&first.getNotificationChannelGroup("account")==null,"cascade deletion invisible");
            first.createNotificationChannelGroup(renamed);channel.setImportance(5);first.createNotificationChannel(channel);
            check(second.getNotificationChannel("messages").getImportance()==2,"restoration lost retained policy");
        }finally{first.shirazClose();second.shirazClose();}
        NotificationManager reopened=NotificationManager.shirazForProcess("fixture",file);
        try{check(reopened.getNotificationChannelGroup("account").getChannels().size()==1,"reopen lost membership");}finally{reopened.shirazClose();file.delete();}
        System.out.println("NOTIFICATION_GROUP_OK");
    }
}
