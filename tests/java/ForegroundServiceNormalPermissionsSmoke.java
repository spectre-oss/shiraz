// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.*;

public final class ForegroundServiceNormalPermissionsSmoke {
 static HostServices host;static final java.util.Set<String> grants=new java.util.HashSet<String>();static final java.util.Set<String> checks=new java.util.HashSet<String>();
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
 static void denied(Worker worker,Notification notice,int type,String message){
  int before=worker.getForegroundServiceType();NotificationManager nm=(NotificationManager)worker.getSystemService(Context.NOTIFICATION_SERVICE);int count=nm.getActiveNotifications().length;
  try{worker.startForeground(2,notice,type);throw new AssertionError(message);}catch(SecurityException expected){}
  check(worker.getForegroundServiceType()==before&&nm.getActiveNotifications().length==count,"denial mutated state: "+message);
  if(count>0)check(nm.getActiveNotifications()[0].getId()==1,"denial replaced notification");
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();java.io.File file=java.io.File.createTempFile("location-service-",".db");file.delete();
  final NotificationManager notifications=NotificationManager.shirazForProcess("Location test",file);
  notifications.createNotificationChannel(new NotificationChannel("location","Location",NotificationManager.IMPORTANCE_LOW));
  Context context=new ContextWrapper(null){
   public int checkSelfPermission(String permission){checks.add(permission);return grants.contains(permission)?0:-1;}
   public String getPackageName(){return "org.shiraz.location";}
   public ClassLoader getClassLoader(){return ForegroundServiceNormalPermissionsSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  meta.setProperty("targetSdk","34");meta.setProperty("service.0.foregroundServiceType",Integer.toString(0x40000fff));
  host=new HostServices(context,new Application(),meta);
  try{
   host.start(new Intent(context,Worker.class));pump();Worker worker=Worker.first;
   Notification notice=new Notification.Builder(worker,"location").setSmallIcon(1).build();
   grants.add("android.permission.FOREGROUND_SERVICE");
   grants.add("android.permission.ACCESS_COARSE_LOCATION");
   grants.add("android.permission.CAMERA");grants.add("android.permission.RECORD_AUDIO");
   int[] types={1,2,4,8,16,32,64,128,256,512,1024,0x40000000};
   String[] names={"DATA_SYNC","MEDIA_PLAYBACK","PHONE_CALL","LOCATION","CONNECTED_DEVICE","MEDIA_PROJECTION","CAMERA","MICROPHONE","HEALTH","REMOTE_MESSAGING","SYSTEM_EXEMPTED","SPECIAL_USE"};
   int union=0;
   for(int i=0;i<types.length;i++){
    String permission="android.permission.FOREGROUND_SERVICE_"+names[i];
    denied(worker,notice,types[i],"missing "+permission);
    grants.add(permission);worker.startForeground(1,notice,types[i]);
    check(worker.getForegroundServiceType()==types[i],"granted type not recorded");union|=types[i];
   }
   worker.startForeground(1,notice,union);
   for(String name:names){
    String permission="android.permission.FOREGROUND_SERVICE_"+name;
    grants.remove(permission);denied(worker,notice,union,"missing union member "+permission);grants.add(permission);
   }
   for(String runtime:new String[]{"android.permission.CAMERA","android.permission.RECORD_AUDIO"}){
    grants.remove(runtime);denied(worker,notice,192,"missing capture runtime grant "+runtime);
    denied(worker,notice,-1,"manifest union missing capture runtime grant "+runtime);
    grants.add(runtime);worker.startForeground(1,notice,192);
    check(worker.getForegroundServiceType()==192,"restored capture runtime grants");
   }
   checks.clear();worker.startForeground(1,notice,2048);
   check(checks.size()==1&&checks.contains("android.permission.FOREGROUND_SERVICE"),"short service must not invent a type permission");
   host.close();meta.setProperty("targetSdk","33");host=new HostServices(context,new Application(),meta);
   host.start(new Intent(context,Worker.class));pump();checks.clear();grants.clear();grants.add("android.permission.FOREGROUND_SERVICE");Worker.first.startForeground(1,notice,union);
   check(checks.size()==1,"legacy checked type permissions");
   System.out.println("FOREGROUND_SERVICE_NORMAL_PERMISSIONS_OK");
  }finally{host.close();Looper.shirazShutdownMain();file.delete();}
 }
}
