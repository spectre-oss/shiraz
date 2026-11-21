// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.*;

public final class SharedForegroundNotificationSmoke {
 static HostServices host;
 public static class Worker extends Service {
  static Worker first;public IBinder onBind(Intent i){return null;}
  public int onStartCommand(Intent i,int flags,int id){first=this;promote(this,1);return START_NOT_STICKY;}
 }
 public static class Other extends Service {
  static Other second;public IBinder onBind(Intent i){return null;}
  public int onStartCommand(Intent i,int flags,int id){second=this;promote(this,2);return START_NOT_STICKY;}
 }
 static void promote(Service s,int id){Notification n=new Notification.Builder(s,"location").setSmallIcon(1).build();s.startForeground(id,n);}
 static void pump(){for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(256);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();java.io.File file=java.io.File.createTempFile("location-service-",".db");file.delete();
  final NotificationManager notifications=NotificationManager.shirazForProcess("Location test",file);
  notifications.createNotificationChannel(new NotificationChannel("location","Location",NotificationManager.IMPORTANCE_LOW));
  Context context=new ContextWrapper(null){
   public String getPackageName(){return "org.shiraz.location";}
   public ClassLoader getClassLoader(){return SharedForegroundNotificationSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  host=new HostServices(context,new Application(),meta);
  try{
   host.start(new Intent(context,Worker.class));host.start(new Intent(context,Other.class));pump();
   promote(Other.second,1);check(notifications.getActiveNotifications().length==1,"replacement removes old unshared ID");
   Worker.first.stopForeground(Service.STOP_FOREGROUND_REMOVE);
   check(notifications.getActiveNotifications().length==1,"remove preserves other service notification");
   promote(Worker.first,1);promote(Worker.first,3);
   check(notifications.getActiveNotifications().length==2,"replacement preserves shared old notification");
   promote(Worker.first,1);Worker.first.stopSelf();pump();
   check(notifications.getActiveNotifications().length==1,"destroy preserves other service notification");
   Other.second.stopSelf();pump();check(notifications.getActiveNotifications().length==0,"last owner removes notification");
   host.start(new Intent(context,Worker.class));host.start(new Intent(context,Other.class));pump();promote(Other.second,1);
   Worker.first.stopForeground(false);Other.second.stopSelf();pump();
   check(notifications.getActiveNotifications().length==1,"retained service notification survives other owner destruction");
   Worker.first.stopSelf();pump();check(notifications.getActiveNotifications().length==0,"retained owner destruction cleans up");
   host.start(new Intent(context,Worker.class));pump();Worker.first.stopForeground(false);Worker.first.stopForeground(true);
   check(notifications.getActiveNotifications().length==0,"remove after legacy retention");
   promote(Worker.first,1);Worker.first.stopForeground(false);Worker.first.stopForeground(Service.STOP_FOREGROUND_DETACH);
   Worker.first.stopForeground(true);Worker.first.stopSelf();pump();
   check(notifications.getActiveNotifications().length==1,"detached notification survives later remove and destruction");notifications.cancel(1);
   host.start(new Intent(context,Worker.class));pump();Worker.first.stopForeground(false);Worker.first.stopForeground(Service.STOP_FOREGROUND_REMOVE|Service.STOP_FOREGROUND_DETACH);
   check(notifications.getActiveNotifications().length==0,"remove takes precedence over detach");Worker.first.stopSelf();pump();
   System.out.println("SHARED_FOREGROUND_NOTIFICATION_OK");
  }finally{host.close();Looper.shirazShutdownMain();file.delete();}
 }
}
