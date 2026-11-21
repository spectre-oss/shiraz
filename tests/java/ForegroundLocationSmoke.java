// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import shiraz.runtime.*;

public final class ForegroundLocationSmoke {
 static ActivityManager processes;
 static void importance(int expected){ActivityManager.RunningAppProcessInfo state=new ActivityManager.RunningAppProcessInfo();state.processName="keep";ActivityManager.getMyMemoryState(state);check(state.importance==expected&&processes.getRunningAppProcesses().get(0).importance==expected,"consistent process importance "+expected);check(state.pid==android.os.Process.myPid()&&state.uid==android.os.Process.myUid()&&"keep".equals(state.processName),"memory state identity and untouched fields");}
 static HostServices host;static int calls;static boolean granted=true;
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
 static void once(LocationManager m){m.requestSingleUpdate("gps",new LocationListener(){public void onLocationChanged(Location l){check(l.isMock(),"not mock");calls++;}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}},Looper.myLooper());pump();}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();java.io.File file=java.io.File.createTempFile("location-service-",".db");file.delete();
  final NotificationManager notifications=NotificationManager.shirazForProcess("Location test",file);
  notifications.createNotificationChannel(new NotificationChannel("location","Location",NotificationManager.IMPORTANCE_LOW));
  Context context=new ContextWrapper(null){
   public String getPackageName(){return "org.shiraz.location";}
   public ClassLoader getClassLoader(){return ForegroundLocationSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
   public int checkPermission(String p,int pid,int uid){return granted?0:-1;}
   public int checkCallingOrSelfPermission(String p){return granted?0:-1;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  host=new HostServices(context,new Application(),meta);
  processes=ActivityManager.shirazForProcess(context.getPackageName());processes.shirazSetForeground(false);ActivityManager.shirazInstall(processes,new java.util.function.IntSupplier(){public int getAsInt(){return host.processImportance();}});importance(400);
  LocationManager manager=LocationManager.shirazForTest(context,TestLocationConfig.parse("1,2"));manager.shirazSetForeground(false);
  try{
   host.start(new Intent(context,Worker.class));pump();host.trackLocationManager(manager);
   importance(125);once(manager);check(calls==1,"late attachment to promoted service");
   host.start(new Intent(context,Other.class));pump();Worker.first.stopForeground(Service.STOP_FOREGROUND_DETACH);
   once(manager);check(calls==2,"second promoted service keeps delivery");
   Other.second.stopForeground(false);importance(300);once(manager);check(calls==2,"retained notification must not authorize delivery");
   promote(Worker.first,1);pump();check(calls==3,"promotion resumes pending subscription");
   granted=false;try{once(manager);throw new AssertionError("promotion granted permission");}catch(SecurityException expected){}granted=true;
   Worker.first.stopSelf();pump();once(manager);check(calls==3,"destroyed foreground service retained delivery");
   processes.shirazSetForeground(true);importance(100);processes.shirazSetForeground(false);importance(300);manager.shirazSetForeground(true);pump();check(calls==4,"activity visibility resumes pending fix");
   manager.shirazSetForeground(false);promote(Other.second,2);host.close();importance(400);once(manager);check(calls==4,"closed host retained promotion");
   System.out.println("FOREGROUND_LOCATION_LIFETIME_OK");
  }finally{host.close();manager.shirazClose();ActivityManager.shirazClear();Looper.shirazShutdownMain();file.delete();}
 }
}
