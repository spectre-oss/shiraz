// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.*;
import android.content.*;
import android.os.IBinder;
/** Compiled with the stock SDK; explicit started-service lifecycle and copied intents. */
public final class ServiceFixture {
 public static android.view.accessibility.AccessibilityManager teardownAccessibility;
 public static int creates,starts,destroys,legacyStarts,persistentStarts,persistentDestroys,memory;
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static class Sample extends Service {
  public void onCreate(){super.onCreate();creates++;check(getApplication()!=null&&getApplicationContext()==getApplication()&&getPackageName().equals("org.shiraz.fixture"),"attached service context/application");}
  public int onStartCommand(Intent intent,int flags,int id){starts++;check(flags==0&&id==starts,"start flags/IDs");if(id==1)check(intent.getIntArrayExtra("values")[0]==7,"copied start extras");if(id==2){check(!stopSelfResult(1)&&stopSelfResult(2)&&destroys==0,"stopSelfResult/latest start and queued destruction");}return START_NOT_STICKY;}
  public void onDestroy(){super.onDestroy();destroys++;}
  public void onLowMemory(){memory++;}
  public IBinder onBind(Intent intent){return null;}
 }
 public static class Legacy extends Service {public void onStart(Intent intent,int id){legacyStarts++;check(id==1,"legacy start ID");stopSelf();}public IBinder onBind(Intent intent){return null;}}
 public static class Persistent extends Service {public int onStartCommand(Intent intent,int flags,int id){persistentStarts++;return START_NOT_STICKY;}public void onDestroy(){if(teardownAccessibility!=null)check(!teardownAccessibility.isEnabled(),"Accessibility manager survives service destruction");persistentDestroys++;check(getApplicationContext()==getApplication(),"teardown context lifetime");System.out.println("SHIRAZ_FIXTURE_SERVICE_TEARDOWN_OK");}public IBinder onBind(Intent intent){return null;}}
 public static class Disabled extends Sample{}public static class Separate extends Sample{}
 public static void start(Context context){
  creates=starts=destroys=legacyStarts=persistentStarts=persistentDestroys=memory=0;
  int[] values={7};Intent first=new Intent(context,Sample.class).putExtra("values",values);ComponentName name=context.startService(first);values[0]=99;first.setAction("changed");check(name.equals(new ComponentName(context,Sample.class))&&creates==0,"queued start result");
  context.startService(new Intent(context,Sample.class));context.startService(new Intent(context,Legacy.class));context.startService(new Intent(context,Persistent.class));
  check(context.startService(new Intent(context,ServiceFixture.class))==null&&!context.stopService(new Intent(context,ServiceFixture.class)),"undeclared service");check(context.startService(new Intent(context,Disabled.class))==null,"disabled service");
  try{context.startService(new Intent("implicit"));throw new AssertionError("implicit service accepted");}catch(UnsupportedOperationException expected){}
  try{context.startService(new Intent(context,Separate.class));throw new AssertionError("separate process accepted");}catch(UnsupportedOperationException expected){}
  check(context.startForegroundService(new Intent(context,ServiceFixture.class))==null,"undeclared foreground service");
 }
 public static void verify(Context context){check(creates==1&&starts==2&&destroys==1&&legacyStarts==1&&persistentStarts==1&&persistentDestroys==0,"started-service lifecycle");check(!context.stopService(new Intent(context,Sample.class)),"stopped service absent");System.out.println("SHIRAZ_FIXTURE_STARTED_SERVICES_OK");}
}
