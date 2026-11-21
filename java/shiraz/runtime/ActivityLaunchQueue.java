// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import java.util.ArrayDeque;
/** Validated launch snapshots, consumed by the runner between event callbacks. */
public final class ActivityLaunchQueue implements AutoCloseable {
 private final Thread owner=Thread.currentThread();
 private final ActivityLaunchResolver resolver;
 private final ArrayDeque<ActivityLaunchResolver.Request> pending=new ArrayDeque<ActivityLaunchResolver.Request>();
 private long bytes;
 private boolean closed,windowless;
 public ActivityLaunchQueue(ActivityLaunchResolver resolver){if(resolver==null)throw new NullPointerException("resolver");this.resolver=resolver;}
 private void live(){if(closed)throw new IllegalStateException("Activity launch session closed");}
 public void enqueue(Intent intent){enqueue(intent,false);}
 public void enqueue(Intent intent,boolean expectsResult){
  synchronized(this){live();}
  if("1".equals(System.getenv("SHIRAZ_TRACE_ACTIVITY_EXTRAS"))&&intent!=null){
   android.os.Bundle extras=intent.getExtras();int remaining=8192,count=0;
   if(extras!=null)for(String key:extras.keySet()){
    if(++count>32||remaining<=0)break;
    Object value=extras.get(key);
    if(value instanceof String){String text=(String)value;int length=Math.min(text.length(),remaining);System.err.println("SHIRAZ_ACTIVITY_EXTRA "+key.substring(0,Math.min(key.length(),128))+"="+text.substring(0,length));remaining-=length;}
   }
  }
  if(HostAppInstaller.open(intent,resolver.packageName()))return;
  if(ExternalLauncher.open(intent))return;
  if(HostContentPicker.handles(intent)){System.err.println("SHIRAZ_CONTENT_PICK_REQUIRES_FOR_RESULT");return;}
  String action=intent.getAction();
  if(intent.getComponent()==null&&action!=null&&(action.startsWith("android.settings.")||action.equals(Intent.ACTION_CHOOSER))){System.err.println("SHIRAZ_SYSTEM_ACTIVITY_UNAVAILABLE "+action);return;}
  ActivityLaunchResolver.Request request=resolver.resolve(intent);
  if(expectsResult&&request.launchMode==3)throw new UnsupportedOperationException("Single-instance activity result routing is not implemented");
  String current=android.app.Application.getProcessName();
  if(current==null)current=resolver.packageName();
  if(!current.equals(request.processName)){
   synchronized(this){live();if(windowless)throw new UnsupportedOperationException("Activity launch from windowless session");}
   if(expectsResult)throw new UnsupportedOperationException("Cross-process activity results are not implemented");
   Intent remote=request.intent();
   int allowed=Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION;
   if((remote.getFlags()&~allowed)!=0||remote.getSelector()!=null||remote.getClipData()!=null)throw new UnsupportedOperationException("Cross-process activity task or capability semantics are not implemented");
   android.os.Parcel parcel=android.os.Parcel.obtain();
   try{remote.writeToParcel(parcel,0);if(parcel.marshall().length>4*1024*1024)throw new IllegalArgumentException("Activity launch payload limit");}finally{parcel.recycle();}
   ServiceBrokerClient broker=RuntimeBroker.client();
   if(broker==null)throw new UnsupportedOperationException("Cross-process activity launch requires host supervision");
   try{
    ServiceBrokerClient.ActivityResolution target=broker.resolveActivity(resolver.packageName(),remote.getComponent().getClassName());
    if(!request.processName.equals(target.process.startsWith(":")?target.packageName+target.process:target.process)||target.launchMode!=request.launchMode)throw new SecurityException("Activity process metadata changed");
    remote.setComponent(new android.content.ComponentName(target.packageName,target.component));
    try(ServiceBrokerClient.ActivityConnection connection=broker.openActivity(target)){connection.send(remote);}
   }catch(java.io.IOException|android.os.RemoteException error){throw new IllegalStateException("Cross-process activity launch failed",error);}
   return;
  }
  synchronized(this){live();if(windowless)throw new UnsupportedOperationException("Activity relaunch from a windowless service session is not implemented");if(pending.size()>=128||bytes+request.bytes()>16*1024*1024L)throw new IllegalStateException("Activity launch queue limit");pending.addLast(request);bytes+=request.bytes();}
 }
 public synchronized void enterWindowless(){
  live();if(Thread.currentThread()!=owner)throw new IllegalStateException("Activity launch owner thread");
  if(!pending.isEmpty())throw new UnsupportedOperationException("Pending activity launch at background transition");
  windowless=true;
 }
 public synchronized ActivityLaunchResolver.Request poll(){
  if(Thread.currentThread()!=owner)throw new IllegalStateException("Activity launches must dispatch on their owner thread");live();
  ActivityLaunchResolver.Request request=pending.pollFirst();if(request!=null)bytes-=request.bytes();return request;
 }
 public synchronized void close(){closed=true;pending.clear();bytes=0;}
}
