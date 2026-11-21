// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.*;

public final class ForegroundServiceTypeSmoke {
 static HostServices host;
 public static class Worker extends Service {
  static Worker first;public IBinder onBind(Intent i){return null;}
  public int onStartCommand(Intent i,int flags,int id){first=this;return START_NOT_STICKY;}
 }
 public static class Other extends Service {
  static Other second;public IBinder onBind(Intent i){return null;}
  public int onStartCommand(Intent i,int flags,int id){second=this;return START_NOT_STICKY;}
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
   public ClassLoader getClassLoader(){return ForegroundServiceTypeSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  meta.setProperty("service.0.foregroundServiceType","9");
  host=new HostServices(context,new Application(),meta);
  try{
   host.start(new Intent(context,Worker.class));pump();
   Worker worker=Worker.first;check(worker.getForegroundServiceType()==0,"unpromoted type");
   Notification notice=new Notification.Builder(worker,"location").setSmallIcon(1).build();
   worker.startForeground(1,notice,1);check(worker.getForegroundServiceType()==1,"explicit data sync");
   worker.startForeground(1,notice,8);check(worker.getForegroundServiceType()==8,"type replacement");
   for(int invalid:new int[]{2,-2,Integer.MIN_VALUE}){
    try{worker.startForeground(2,notice,invalid);throw new AssertionError("undeclared type accepted");}catch(IllegalArgumentException expected){}
    check(worker.getForegroundServiceType()==8&&notifications.getActiveNotifications().length==1&&notifications.getActiveNotifications()[0].getId()==1,"failed promotion changed live state");
   }
   worker.startForeground(1,notice);check(worker.getForegroundServiceType()==9,"manifest types via legacy overload");
   worker.stopForeground(false);check(worker.getForegroundServiceType()==0,"demotion clears type");
   worker.startForeground(1,notice,-1);check(worker.getForegroundServiceType()==9,"manifest sentinel");
   worker.startForeground(1,notice,0);check(worker.getForegroundServiceType()==0,"explicit none legacy behavior");
   worker.startForeground(1,notice,8);worker.stopSelf();pump();check(worker.getForegroundServiceType()==0,"destruction clears type");
   host.start(new Intent(context,Other.class));pump();promote(Other.second,2);
   check(Other.second.getForegroundServiceType()==0,"missing declaration legacy default");
   System.out.println("FOREGROUND_SERVICE_TYPE_OK");
  }finally{host.close();Looper.shirazShutdownMain();file.delete();}
 }
}
