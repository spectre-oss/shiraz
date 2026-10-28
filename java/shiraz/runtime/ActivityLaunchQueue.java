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
 private boolean closed;
 public ActivityLaunchQueue(ActivityLaunchResolver resolver){if(resolver==null)throw new NullPointerException("resolver");this.resolver=resolver;}
 private void live(){if(closed)throw new IllegalStateException("Activity launch session closed");}
 public void enqueue(Intent intent){
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
  synchronized(this){live();if(pending.size()>=128||bytes+request.bytes()>16*1024*1024L)throw new IllegalStateException("Activity launch queue limit");pending.addLast(request);bytes+=request.bytes();}
 }
 public synchronized ActivityLaunchResolver.Request poll(){
  if(Thread.currentThread()!=owner)throw new IllegalStateException("Activity launches must dispatch on their owner thread");live();
  ActivityLaunchResolver.Request request=pending.pollFirst();if(request!=null)bytes-=request.bytes();return request;
 }
 public synchronized void close(){closed=true;pending.clear();bytes=0;}
}
