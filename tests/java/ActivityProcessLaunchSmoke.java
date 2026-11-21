// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import shiraz.runtime.*;
public final class ActivityProcessLaunchSmoke {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 private static void denied(ActivityLaunchQueue queue,Intent intent,boolean result,String message){
  try{queue.enqueue(intent,result);throw new AssertionError("Launch accepted");}
  catch(UnsupportedOperationException expected){check(expected.getMessage().contains(message));}
 }
 public static void main(String[] args){
  String pkg="fixture.process";
  java.util.Properties p=new java.util.Properties();
  p.setProperty("package",pkg);p.setProperty("sourceDir","fixture.apk");p.setProperty("dataDir","build/fixture");
  p.setProperty("activity.count","1");p.setProperty("activity.0.name",pkg+".Secondary");p.setProperty("activity.0.process",":helper");
  ActivityLaunchResolver resolver=new ActivityLaunchResolver(pkg,new HostPackageManager(p,null));
  Intent launch=new Intent().setComponent(new ComponentName(pkg,pkg+".Secondary"));
  check(resolver.resolve(launch).processName.equals(pkg+":helper"));
  android.app.Application.shirazSetProcessName(pkg);
  ActivityLaunchQueue queue=new ActivityLaunchQueue(resolver);
  denied(queue,launch,true,"results");
  denied(queue,new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT),false,"semantics");
  denied(queue,launch,false,"supervision");check(queue.poll()==null);
  android.app.Application.shirazSetProcessName(pkg+":helper");
  queue.enqueue(launch.putExtra("value",7));launch.putExtra("value",9);
  check(queue.poll().intent().getIntExtra("value",0)==7);check(queue.poll()==null);
  queue.close();System.out.println("ACTIVITY_PROCESS_LAUNCH_OK");
 }
}
