// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.*;

public final class ForegroundServiceMissingTypeSmoke {
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
 static void roundTrip(ForegroundServiceTypeException error){
  check(error instanceof ServiceStartNotAllowedException&&error instanceof IllegalStateException,"exception hierarchy");
  Parcel parcel=Parcel.obtain();
  try{
   Parcelable value=(Parcelable)error;check(value.describeContents()==0,"parcel contents");value.writeToParcel(parcel,0);parcel.setDataPosition(0);
   ForegroundServiceTypeException copy=error instanceof MissingForegroundServiceTypeException?MissingForegroundServiceTypeException.CREATOR.createFromParcel(parcel):InvalidForegroundServiceTypeException.CREATOR.createFromParcel(parcel);
   check(copy.getClass()==error.getClass()&&copy.getMessage().equals(error.getMessage()),"exception parcel round trip");
  }finally{parcel.recycle();}
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();java.io.File file=java.io.File.createTempFile("location-service-",".db");file.delete();
  final NotificationManager notifications=NotificationManager.shirazForProcess("Location test",file);
  notifications.createNotificationChannel(new NotificationChannel("location","Location",NotificationManager.IMPORTANCE_LOW));
  Context context=new ContextWrapper(null){
   public int checkSelfPermission(String permission){checks++;if(!"android.permission.FOREGROUND_SERVICE".equals(permission)&&!"android.permission.FOREGROUND_SERVICE_LOCATION".equals(permission)&&!"android.permission.ACCESS_COARSE_LOCATION".equals(permission))throw new AssertionError("Wrong permission");return granted?0:-1;}
   public String getPackageName(){return "org.shiraz.location";}
   public ClassLoader getClassLoader(){return ForegroundServiceMissingTypeSmoke.class.getClassLoader();}
   protected HostServices shirazServices(){return host;}
   public Object getSystemService(String n){return NOTIFICATION_SERVICE.equals(n)?notifications:null;}
  };
  java.util.Properties meta=new java.util.Properties();meta.setProperty("service.count","2");meta.setProperty("service.0.name",Worker.class.getName());meta.setProperty("service.1.name",Other.class.getName());
  meta.setProperty("targetSdk","33");meta.setProperty("service.0.foregroundServiceType","9");
  host=new HostServices(context,new Application(),meta);
  try{
   host.start(new Intent(context,Worker.class));pump();Worker worker=Worker.first;
   Notification notice=new Notification.Builder(worker,"location").setSmallIcon(1).build();
   granted=true;
   worker.startForeground(1,notice,0);check(worker.getForegroundServiceType()==0,"legacy explicit none");
   host.close();meta.setProperty("targetSdk","34");host=new HostServices(context,new Application(),meta);
   host.start(new Intent(context,Worker.class));host.start(new Intent(context,Other.class));pump();worker=Worker.first;
   worker.startForeground(1,notice,8);
   try{worker.startForeground(2,notice,0);throw new AssertionError("NONE accepted");}catch(InvalidForegroundServiceTypeException expected){roundTrip(expected);}
   check(worker.getForegroundServiceType()==8&&notifications.getActiveNotifications().length==1&&notifications.getActiveNotifications()[0].getId()==1,"rejection changed existing promotion");
   for(int mode=0;mode<2;mode++){
    try{if(mode==0)Other.second.startForeground(2,notice);else Other.second.startForeground(2,notice,-1);throw new AssertionError("Missing manifest type accepted");}
    catch(MissingForegroundServiceTypeException expected){roundTrip(expected);}
   }
   try{Other.second.startForeground(2,notice,0);throw new AssertionError("Explicit NONE without declaration accepted");}catch(InvalidForegroundServiceTypeException expected){}
   check(Other.second.getForegroundServiceType()==0&&notifications.getActiveNotifications().length==1,"failed first promotion changed state");
   host.close();meta.setProperty("targetSdk","33");host=new HostServices(context,new Application(),meta);
   host.start(new Intent(context,Other.class));pump();Other.second.startForeground(1,notice);
   check(notifications.getActiveNotifications().length==1,"legacy missing declaration rejected");
   System.out.println("FOREGROUND_SERVICE_MISSING_TYPE_OK");
  }finally{host.close();Looper.shirazShutdownMain();file.delete();}
 }
}
