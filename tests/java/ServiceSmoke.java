// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import shiraz.runtime.HostServices;
import shiraz.runtime.ActivityLifecycle;
import shiraz.runtime.ActivityConfigurationPolicy;
import org.shiraz.fixture.ServiceFixture;
import java.util.Properties;
public final class ServiceSmoke {
 private static final class BoundActivity extends Activity {
  static ServiceConnection connection;
  BoundActivity(Context base){attachBaseContext(base);}
  @Override protected void onCreate(Bundle state){
   super.onCreate(state);
   connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){}public void onServiceDisconnected(ComponentName name){}};
   check(bindService(new Intent(this,ServiceFixture.Sample.class),connection,Context.BIND_AUTO_CREATE),"Activity service binding");
  }
 }
 private static void activityBindingCleanup(final Context context){
  Properties facts=new Properties();facts.setProperty("activity.count","1");facts.setProperty("activity.0.name",BoundActivity.class.getName());
  ActivityLifecycle lifecycle=new ActivityLifecycle(new ActivityLifecycle.Factory(){public Activity create(){return new BoundActivity(context);}},new ActivityLifecycle.Cleanup(){public void detached(Activity activity){}},new ActivityConfigurationPolicy(facts,BoundActivity.class.getName(),27),27);
  lifecycle.start();due();BoundActivity activity=(BoundActivity)lifecycle.current();ServiceConnection connection=BoundActivity.connection;
  lifecycle.close();due();
  try{context.unbindService(connection);throw new AssertionError("Destroyed Activity retained its service binding");}catch(IllegalArgumentException expected){}
  activity.unbindService(connection);
  try{activity.unbindService(connection);throw new AssertionError("Late unbind accepted twice");}catch(IllegalArgumentException expected){}
 }
 public static class NeverForeground extends Service {public IBinder onBind(Intent intent){return null;}public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}}
 public static class Foreground extends Service {
  static int starts,destroys;static Foreground instance;
  public IBinder onBind(Intent intent){return null;}
  public int onStartCommand(Intent intent,int flags,int id){instance=this;starts++;NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("download","Download",NotificationManager.IMPORTANCE_LOW));startForeground(8,new Notification.Builder(this,"download").setSmallIcon(1).setContentTitle("Downloading map").setProgress(100,30,false).setOnlyAlertOnce(true).build());return START_NOT_STICKY;}
  public void onDestroy(){destroys++;stopForeground(true);}
 }
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 private static void absent(Context context){
  final int[] callbacks={0};ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){callbacks[0]++;}public void onServiceDisconnected(ComponentName name){callbacks[0]++;}};
  Intent external=new Intent().setComponent(new ComponentName("missing.provider","missing.provider.Service"));
  check(!context.bindService(external,connection,Context.BIND_AUTO_CREATE),"Absent external service must not report a connection");due();check(callbacks[0]==0,"Absent service must not fabricate callbacks");context.unbindService(connection);
  try{context.unbindService(connection);throw new AssertionError("Unknown connection accepted");}catch(IllegalArgumentException expected){}
  check(!context.bindService(new Intent().setComponent(new ComponentName("org.shiraz.fixture","org.shiraz.fixture.Absent")),connection,0),"Absent local service");context.unbindService(connection);
  check(context.bindService(new Intent(context,ServiceFixture.Sample.class),connection,Context.BIND_AUTO_CREATE),"Declared bound service scheduled");context.unbindService(connection);due();check(callbacks[0]==0,"Canceled binding must not connect");
  try{context.bindService(new Intent("implicit"),connection,0);throw new AssertionError("Implicit service accepted");}catch(IllegalArgumentException expected){}
 }
 private static void due(){for(int i=0;i<12;i++)Looper.myLooper().shirazDispatchDue(256);}
 private static void serviceMetadata()throws Exception{
  Properties facts=new Properties();facts.setProperty("package","test.pkg");facts.setProperty("sourceDir","/unused/fixture.apk");facts.setProperty("dataDir","/unused/data");facts.setProperty("service.count","2");
  facts.setProperty("service.0.name","test.pkg.Child");facts.setProperty("service.0.process",":child");facts.setProperty("service.0.isolated","true");facts.setProperty("service.1.name","test.pkg.Disabled");facts.setProperty("service.1.enabled","false");
  facts.setProperty("activity.count","1");facts.setProperty("activity.0.name","test.pkg.Main");
  shiraz.runtime.HostPackageManager manager=new shiraz.runtime.HostPackageManager(facts,null);
  check(!manager.isInstantApp(),"Packaged APK is not an Android instant app");
  Intent explicit=new Intent().setComponent(new ComponentName("test.pkg","test.pkg.Main"));
  Intent selectedActivity=new Intent(Intent.ACTION_VIEW);selectedActivity.setSelector(explicit);
  check(manager.queryIntentActivities(selectedActivity,0).size()==1,"Selector resolves declared activity");
  check(manager.queryIntentActivities(explicit,android.content.pm.PackageManager.MATCH_ALL).size()==1,"MATCH_ALL includes declared own activity");
  check(manager.queryIntentActivities(explicit,android.content.pm.PackageManager.MATCH_SYSTEM_ONLY|android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).isEmpty(),"Own APK is not a system package");
  check(manager.resolveActivity(new Intent("android.provider.action.PICK_IMAGES"),android.content.pm.PackageManager.MATCH_SYSTEM_ONLY|android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)==null,"Absent system picker does not fabricate activity");
  android.content.pm.PackageInfo info=manager.getPackageInfo("test.pkg",android.content.pm.PackageManager.GET_SERVICES);
  check(info.services.length==1&&info.services[0].processName.equals("test.pkg:child")&&(info.services[0].flags&android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS)!=0,"Package service manifest metadata");
  info.services[0].name="mutated";check(manager.getPackageInfo("test.pkg",0).services==null&&manager.getPackageInfo("test.pkg",android.content.pm.PackageManager.GET_SERVICES|android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS).services.length==2,"Service query flags and fresh snapshots");
 }
 public static void main(String[] args)throws Exception{serviceMetadata();Looper.prepareMainLooper();final HostServices[] host={null};final Application app=new Application();final java.io.File policy=java.io.File.createTempFile("shiraz-service-", ".db");policy.delete();final NotificationManager notifications=NotificationManager.shirazForProcess("Service test",policy);Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public Context getApplicationContext(){return app;}public ApplicationInfo getApplicationInfo(){ApplicationInfo i=new ApplicationInfo();i.targetSdkVersion=35;return i;}protected HostServices shirazServices(){return host[0];}public Object getSystemService(String name){if(NOTIFICATION_SERVICE.equals(name))return notifications;throw new UnsupportedOperationException(name);}};
  Properties metadata=new Properties();String[] names={"Sample","Legacy","Persistent","Disabled","Separate"};metadata.setProperty("service.count","5");for(int i=0;i<names.length;i++)metadata.setProperty("service."+i+".name","org.shiraz.fixture.ServiceFixture$"+names[i]);metadata.setProperty("service.3.enabled","false");metadata.setProperty("service.4.process",":separate");host[0]=new HostServices(context,app,metadata);
  try{absent(context);activityBindingCleanup(context);ServiceFixture.start(context);due();ServiceFixture.verify(context);app.onLowMemory();check(ServiceFixture.memory==0,"destroyed service callback unregistered");host[0].close();check(ServiceFixture.persistentDestroys==1,"session destroys running service");host[0].close();check(ServiceFixture.persistentDestroys==1,"idempotent close");try{context.startService(new Intent(context,ServiceFixture.Sample.class));throw new AssertionError("closed session restarted");}catch(IllegalStateException expected){}
   host[0]=new HostServices(context,app,metadata);Thread worker=new Thread(new Runnable(){public void run(){context.startService(new Intent(context,ServiceFixture.Persistent.class));}});worker.start();try{worker.join();}catch(InterruptedException error){throw new AssertionError(error);}check(ServiceFixture.persistentStarts==1,"worker start queued");due();check(ServiceFixture.persistentStarts==2&&context.stopService(new Intent(context,ServiceFixture.Persistent.class)),"worker delivery/explicit stop");check(ServiceFixture.persistentDestroys==1,"stopService queues destruction");due();check(ServiceFixture.persistentDestroys==2,"explicit stop delivered");host[0].close();
   host[0]=new HostServices(context,app,metadata);for(int i=0;i<64;i++){context.startService(new Intent(context,ServiceFixture.Persistent.class));check(context.stopService(new Intent(context,ServiceFixture.Persistent.class)),"pending stop");}try{context.startService(new Intent(context,ServiceFixture.Persistent.class));throw new AssertionError("instance budget bypassed");}catch(IllegalStateException expected){}host[0].close();due();check(ServiceFixture.persistentStarts==2&&ServiceFixture.persistentDestroys==2,"canceled pending services never created");
   host[0]=new HostServices(context,app,metadata);for(int i=0;i<1024;i++)context.startService(new Intent(context,ServiceFixture.Sample.class));try{context.startService(new Intent(context,ServiceFixture.Sample.class));throw new AssertionError("queue budget bypassed");}catch(IllegalStateException expected){}host[0].close();due();check(ServiceFixture.creates==1&&ServiceFixture.starts==2&&ServiceFixture.destroys==1,"queue cancellation drains without callbacks");
   metadata.setProperty("service.count","6");metadata.setProperty("service.5.name",Foreground.class.getName());host[0]=new HostServices(context,app,metadata);
   context.startForegroundService(new Intent(context,Foreground.class));check(Foreground.starts==0,"foreground queued");due();
   check(Foreground.starts==1&&notifications.shirazForegroundSummary().equals("Downloading map (30%)"),"foreground promotion and visible progress");
   Notification update=new Notification.Builder(context,"download").setSmallIcon(1).setContentTitle("Downloading map").setProgress(100,60,false).setExtras(new Bundle()).build();notifications.notify(8,update);
   {android.os.Binder token=new android.os.Binder();Bundle holder=new Bundle();holder.putBinder("token",token);if(holder.getBinder("token")!=token||holder.getBinder("missing")!=null)throw new AssertionError("Bundle binder");}
   {if(!android.text.format.DateUtils.formatElapsedTime(75).equals("01:15"))throw new AssertionError("elapsed time");if(!android.text.format.DateUtils.isToday(System.currentTimeMillis()))throw new AssertionError("today");if(!android.text.format.Formatter.formatFileSize(null,1500000L).startsWith("1.50"))throw new AssertionError("file size");}
   {android.app.AlarmManager alarms=new android.app.AlarmManager();android.app.PendingIntent none=null;try{alarms.setExact(0,1L,none);throw new AssertionError("null operation accepted");}catch(NullPointerException expected){}alarms.cancel(none);}
   {android.content.ClipboardManager clips=new android.content.ClipboardManager();
    android.content.ClipData value=android.content.ClipData.newPlainText("timestamp","copy");
    if(value.getDescription().getTimestamp()!=0)throw new AssertionError("Unpublished clip timestamp");
    final long[] observed={0};android.content.ClipboardManager.OnPrimaryClipChangedListener listener=new android.content.ClipboardManager.OnPrimaryClipChangedListener(){public void onPrimaryClipChanged(){observed[0]=clips.getPrimaryClipDescription().getTimestamp();}};
    clips.addPrimaryClipChangedListener(listener);long before=System.currentTimeMillis();clips.setPrimaryClip(value);long stamp=clips.getPrimaryClip().getDescription().getTimestamp();
    if(stamp<before||stamp>System.currentTimeMillis()||observed[0]!=stamp||new android.content.ClipDescription(clips.getPrimaryClipDescription()).getTimestamp()!=stamp||value.getDescription().getTimestamp()!=0)throw new AssertionError("Clipboard publication timestamp and copies");
    clips.removePrimaryClipChangedListener(listener);clips.clearPrimaryClip();
   }
   {final int[] fired={0};android.content.ClipboardManager clips=new android.content.ClipboardManager();clips.addPrimaryClipChangedListener(new android.content.ClipboardManager.OnPrimaryClipChangedListener(){public void onPrimaryClipChanged(){fired[0]++;}});clips.setPrimaryClip(android.content.ClipData.newPlainText("","x"));if(fired[0]!=1||!clips.hasPrimaryClip()||!"x".equals(String.valueOf(clips.getPrimaryClip().getItemAt(0).getText()))||android.content.Context.CLIPBOARD_SERVICE==null)throw new AssertionError("Clipboard");clips.clearPrimaryClip();}
   {final java.util.ArrayList<String> ran=new java.util.ArrayList<String>();shiraz.runtime.ExternalLauncher.setRunner(new shiraz.runtime.ExternalLauncher.Runner(){public void run(java.util.List<String> c){ran.addAll(c);}});
   boolean web=shiraz.runtime.ExternalLauncher.open(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://osmand.net/a?x=1")));
   boolean scoped=shiraz.runtime.ExternalLauncher.open(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://osmand.net")).setPackage("net.osmand.plus"));
   boolean file=shiraz.runtime.ExternalLauncher.open(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("file:///etc/passwd")));
   boolean spaced=shiraz.runtime.ExternalLauncher.open(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://a.b/x y")));
   boolean send=shiraz.runtime.ExternalLauncher.open(new android.content.Intent(android.content.Intent.ACTION_SEND,android.net.Uri.parse("https://a.b")));
   if(!web||scoped||file||spaced||send||!ran.toString().equals("[xdg-open, https://osmand.net/a?x=1]"))throw new AssertionError("External launcher "+web+scoped+file+spaced+send+ran);}
   {java.io.File picked=java.io.File.createTempFile("shiraz-pick-",".png");picked.deleteOnExit();try(java.io.FileOutputStream out=new java.io.FileOutputStream(picked)){out.write(new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A});}
    final String[] chosen={null};shiraz.runtime.HostContentPicker.setChooser(new shiraz.runtime.HostContentPicker.Chooser(){public String choose(String mime){return chosen[0];}});
    android.content.Intent get=new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*");
    android.content.Intent chooser=android.content.Intent.createChooser(get,"Pick");
    if(!shiraz.runtime.HostContentPicker.handles(get)||!shiraz.runtime.HostContentPicker.handles(chooser)||shiraz.runtime.HostContentPicker.handles(new android.content.Intent(android.content.Intent.ACTION_VIEW)))throw new AssertionError("content pick handles");
    chosen[0]=picked.getAbsolutePath();android.net.Uri uri=shiraz.runtime.HostContentPicker.pick(chooser);
    if(uri==null||!"file".equals(uri.getScheme())||!picked.getAbsolutePath().equals(uri.getPath()))throw new AssertionError("content pick path "+uri);
    chosen[0]=null;if(shiraz.runtime.HostContentPicker.pick(get)!=null)throw new AssertionError("cancelled pick");}
   check(notifications.shirazForegroundSummary().equals("Downloading map (60%)"),"progress update retains foreground ownership/title");
   update.extras.putCharSequence(Notification.EXTRA_TITLE,"mutated");check(notifications.shirazNotification(8).extras.getCharSequence(Notification.EXTRA_TITLE).equals("Downloading map"),"notification snapshot");
   Foreground.instance.stopForeground(Service.STOP_FOREGROUND_DETACH);check(notifications.shirazForegroundSummary().isEmpty()&&notifications.shirazNotification(8)!=null,"detach retains notification");
   Foreground.instance.startForeground(8,update);Foreground.instance.stopForeground(true);check(notifications.shirazNotification(8)==null,"remove notification");
   Foreground.instance.startForeground(8,update);host[0].close();check(Foreground.destroys==1&&notifications.shirazNotification(8)==null,"foreground teardown removes notification");
   host[0]=new HostServices(context,app,metadata);context.startForegroundService(new Intent(context,Foreground.class));due();Foreground.instance.stopForeground(false);check(notifications.shirazForegroundSummary().isEmpty()&&notifications.shirazNotification(8)!=null,"legacy demotion retains notification until service stops");host[0].close();check(notifications.shirazNotification(8)==null,"legacy notification removed at destruction");
   metadata.setProperty("service.count","7");metadata.setProperty("service.6.name",NeverForeground.class.getName());host[0]=new HostServices(context,app,metadata);context.startForegroundService(new Intent(context,NeverForeground.class));due();Thread.sleep(5100);try{due();throw new AssertionError("Missing foreground promotion accepted");}catch(IllegalStateException expected){check(expected.getMessage().contains("five seconds"),"foreground deadline failure");}host[0].close();
   Intent tokenIntent=new Intent(context,Foreground.class);PendingIntent token=PendingIntent.getActivity(context,19,tokenIntent,PendingIntent.FLAG_IMMUTABLE);check(token==PendingIntent.getActivity(context,19,new Intent(tokenIntent).putExtra("changed",1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE),"PendingIntent stable identity ignores extras");check(PendingIntent.getActivity(context,20,tokenIntent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE)==null,"PendingIntent no-create");check(PendingIntent.getBroadcast(context,19,tokenIntent,PendingIntent.FLAG_IMMUTABLE)!=token,"PendingIntent operation isolation");
   notifications.shirazClose();policy.delete();
   System.out.println("STARTED_SERVICE_LIFECYCLE_AND_TEARDOWN_OK");}finally{try{host[0].close();}finally{Looper.shirazShutdownMain();}}
 }
}
