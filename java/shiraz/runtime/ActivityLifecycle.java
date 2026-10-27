// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import java.lang.reflect.*;
/** Main-thread activity instance ownership; display changes are supplied by the host. */
public final class ActivityLifecycle implements AutoCloseable {
 private static final boolean TRACE="1".equals(System.getenv("SHIRAZ_TRACE_FRAMES"));
 private static void timing(String phase,long start){if(TRACE)System.err.println("SHIRAZ_LIFECYCLE phase="+phase+" elapsed_us="+(System.nanoTime()-start)/1000);}
 public interface Factory { Activity create(); }
 public interface Cleanup { void detached(Activity activity); }
 private final Factory factory;private final Cleanup cleanup;private final ActivityConfigurationPolicy policy;
 private final int targetSdk;private final Thread owner=Thread.currentThread();
 private Activity activity;private boolean created,started,resumed,closed,transition;
 public ActivityLifecycle(Factory factory,Cleanup cleanup,ActivityConfigurationPolicy policy,int targetSdk){if(factory==null||cleanup==null||policy==null)throw new NullPointerException();this.factory=factory;this.cleanup=cleanup;this.policy=policy;this.targetSdk=targetSdk;}
 private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Activity lifecycle must run on its owner thread");}
 private void enter(){thread();if(closed||transition)throw new IllegalStateException("Activity lifecycle is closed or transitioning");transition=true;}
 private static Object invoke(Activity value,String name,Class<?>[] types,Object... args){
  long begin=TRACE?System.nanoTime():0;
  try{Method method=Activity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(value,args);}
  catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("Activity callback failed: "+name,cause);}
  catch(ReflectiveOperationException error){throw new IllegalStateException("Activity callback unavailable: "+name,error);}
  finally{timing(name,begin);}
 }
 private void dispatch(int kind,Bundle bundle){android.content.Context root=activity;while(root instanceof android.content.ContextWrapper){root=((android.content.ContextWrapper)root).getBaseContext();if(root==null)return;}android.content.Context context=activity.getApplicationContext();if(context instanceof android.app.Application)((android.app.Application)context).shirazDispatchLifecycle(kind,activity,bundle);}
 private void call(String name){invoke(activity,name,new Class<?>[0]);}
 private void state(String name,Bundle bundle){invoke(activity,name,new Class<?>[]{Bundle.class},bundle);if(name.equals("onSaveInstanceState")){activity.shirazSaveFragments(bundle);dispatch(5,bundle);}}
 public Activity current(){thread();if(activity==null)throw new IllegalStateException("No live activity");return activity;}
 private void launch(Bundle state,Object retained,android.content.Intent intent,PermissionRequests permissions){
  long begin=TRACE?System.nanoTime():0;
  activity=factory.create();if(activity==null)throw new IllegalStateException("Activity factory returned null");
  timing("factory",begin);
  if(state!=null)activity.setIntent(intent);
  activity.shirazSetLifecycleState(false,false,retained);
  activity.shirazRestorePermissionRequests(permissions);
  activity.shirazPrepareFragments(state);state("onCreate",state);created=true;dispatch(0,state);activity.shirazMoveFragments(2);
  if(activity.isFinishing())return;
  call("onStart");started=true;dispatch(1,null);activity.shirazMoveFragments(3);
  if(state!=null)state("onRestoreInstanceState",state);
  state("onPostCreate",state);
  if(activity.isFinishing())return;
  call("onResume");resumed=true;dispatch(2,null);activity.shirazMoveFragments(4);call("onPostResume");
 }
 public void start(){enter();try{if(activity!=null)throw new IllegalStateException("Activity already started");launch(null,null,null,null);}finally{transition=false;}}
 /** Pause before launching a covering activity; a failed launch can resume this instance. */
 public void pauseForLaunch(){enter();try{requireCreated();pause();}finally{transition=false;}}
 /** Stop only after the covering activity has resumed. Keep this instance and its state. */
 public void stopCovered(){enter();try{requireCreated();if(resumed)throw new IllegalStateException("Pause activity before covering it");stop();}finally{transition=false;}}
 /** Return from a covering activity without onCreate or configuration-retention callbacks. */
 public void resumeFromBackStack(){
  enter();try{
   requireCreated();if(activity.isFinishing())throw new IllegalStateException("Cannot resume a finishing activity");
   if(!started){call("onRestart");call("onStart");started=true;dispatch(1,null);activity.shirazMoveFragments(3);}
   if(!resumed){call("onResume");resumed=true;dispatch(2,null);activity.shirazMoveFragments(4);call("onPostResume");}
  }finally{transition=false;}
 }
 /** Deliver a new launch to a retained instance. Android leaves getIntent unchanged. */
 public void newIntent(android.content.Intent intent){
  if(intent==null)throw new NullPointerException("intent");enter();try{requireCreated();if(resumed)throw new IllegalStateException("Pause activity before delivering a new intent");invoke(activity,"onNewIntent",new Class<?>[]{android.content.Intent.class},intent);}finally{transition=false;}
 }
 private void requireCreated(){if(activity==null||!created)throw new IllegalStateException("Activity not created");}
 private void pause(){if(resumed){resumed=false;try{activity.shirazMoveFragments(3);}finally{call("onPause");dispatch(3,null);}}}
 private void stop(){if(started){started=false;try{activity.shirazMoveFragments(2);}finally{call("onStop");dispatch(4,null);}}}
 private void destroy(){
  Activity old=activity;if(old==null)return;
  try{try{old.shirazDestroyFragments();}finally{if(created){created=false;old.shirazSetDestroyed();call("onDestroy");dispatch(6,null);}}}finally{old.shirazSetDestroyed();try{old.shirazReleaseServiceBindings();}finally{try{old.shirazReleaseWindow();}finally{activity=null;cleanup.detached(old);}}}
 }
 public boolean reconfigure(Configuration configuration,int changes,Runnable apply){return reconfigure(configuration,changes,changes,apply);}
 public boolean reconfigure(Configuration before,Configuration configuration,int changes,Runnable apply){return reconfigure(configuration,changes,policy.significantChanges(before,configuration,changes),apply);}
 private boolean reconfigure(Configuration configuration,int changes,int significant,Runnable apply){
  if(configuration==null||apply==null)throw new NullPointerException();enter();
  try{
   if(activity==null)throw new IllegalStateException("Activity not started");
   if(changes==0||activity.isFinishing()){apply.run();return false;}
   if(!policy.requiresRecreation(significant)){apply.run();activity.onConfigurationChanged(new Configuration(configuration));return false;}
   replace(apply);return true;
  }finally{transition=false;}
 }
 /** Explicit application request, independent of manifest configChanges. */
 public void recreate(){enter();try{requireCreated();if(!activity.isFinishing())replace(new Runnable(){public void run(){}});}finally{transition=false;}}
 private void replace(Runnable apply){
   activity.shirazSetLifecycleState(true,false,activity.getLastNonConfigurationInstance());pause();
   Bundle saved=new Bundle();
   if(targetSdk<28)state("onSaveInstanceState",saved);
   stop();if(targetSdk>=28)state("onSaveInstanceState",saved);
   Object retained=activity.onRetainNonConfigurationInstance();
   android.content.Intent intent=activity.getIntent();
   PermissionRequests permissions=activity.shirazTakePermissionRequests();
   try{destroy();apply.run();launch(saved,retained,intent,permissions);}catch(RuntimeException|Error failure){if(permissions!=null)permissions.close();throw failure;}
 }

 @Override public void close(){close(null);}
 public void close(Runnable hidden){thread();if(closed)return;if(transition)throw new IllegalStateException("Activity lifecycle is transitioning");transition=true;
  try{try{pause();}finally{try{boolean wasStarted=started;stop();if(wasStarted&&activity!=null){activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);if(hidden!=null)hidden.run();}}finally{destroy();}}}finally{closed=true;transition=false;}
 }
}
