// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.*;

public final class ForegroundServicePermissionSmoke {
 static HostServices host;static boolean granted;static int checks;
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
   public int checkSelfPermission(String permission){checks++;if(!"android.permission.FOREGROUND_SERVICE".equals(permission))throw new AssertionError("Wrong permission");return granted?0:-1;}
   public String getPackageName(){return "org.shiraz.location";}
   public ClassLoader getClassLoader(){return ForegroundServicePermissionSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  meta.setProperty("targetSdk","28");meta.setProperty("service.0.foregroundServiceType","9");
  host=new HostServices(context,new Application(),meta);
  try{
   host.start(new Intent(context,Worker.class));pump();Worker worker=Worker.first;
   Notification notice=new Notification.Builder(worker,"location").setSmallIcon(1).build();
   try{worker.startForeground(1,notice);throw new AssertionError("Missing permission accepted");}catch(SecurityException expected){}
   check(worker.getForegroundServiceType()==0&&notifications.getActiveNotifications().length==0,"denied promotion mutated state");
   granted=true;worker.startForeground(1,notice,8);check(worker.getForegroundServiceType()==8,"granted promotion");
   granted=false;try{worker.startForeground(2,notice,1);throw new AssertionError("Denied replacement accepted");}catch(SecurityException expected){}
   check(worker.getForegroundServiceType()==8&&notifications.getActiveNotifications()[0].getId()==1,"denied replacement changed state");
   host.close();meta.setProperty("targetSdk","27");host=new HostServices(context,new Application(),meta);
   host.start(new Intent(context,Worker.class));pump();int before=checks;Worker.first.startForeground(1,notice);
   check(checks==before&&Worker.first.getForegroundServiceType()==9,"legacy permission behavior");
   System.out.println("FOREGROUND_SERVICE_PERMISSION_OK");
  }finally{host.close();Looper.shirazShutdownMain();file.delete();}
 }
}
