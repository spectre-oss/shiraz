// SPDX-License-Identifier: GPL-3.0-only
package android.app;
/** App-op checks delegate to the loaded application's existing permission policy. */
public final class AppOpsManager {
 public static final int MODE_ALLOWED=0,MODE_IGNORED=1,MODE_ERRORED=2,MODE_DEFAULT=3;
 public static final String OPSTR_CAMERA="android:camera",OPSTR_RECORD_AUDIO="android:record_audio",OPSTR_FINE_LOCATION="android:fine_location",OPSTR_COARSE_LOCATION="android:coarse_location";
 public static final String OPSTR_SYSTEM_ALERT_WINDOW="android:system_alert_window";
 public interface OnOpChangedListener {void onOpChanged(String op,String packageName);}
 private static final String[] OPS={OPSTR_CAMERA,OPSTR_RECORD_AUDIO,OPSTR_FINE_LOCATION,OPSTR_COARSE_LOCATION,OPSTR_SYSTEM_ALERT_WINDOW};
 private static final java.util.ArrayList<java.lang.ref.WeakReference<AppOpsManager>> MANAGERS=new java.util.ArrayList<java.lang.ref.WeakReference<AppOpsManager>>();
 private final java.util.ArrayList<Watch> watches=new java.util.ArrayList<Watch>();
 private volatile android.os.Handler handler;
 private final Object notificationLock=new Object();private boolean notificationPending;
 private static final class Watch {final String op,pkg;final OnOpChangedListener listener;final java.util.HashMap<String,Integer> modes=new java.util.HashMap<String,Integer>();Watch(String op,String pkg,OnOpChangedListener listener){this.op=op;this.pkg=pkg;this.listener=listener;}}
 private final android.content.Context context;
 public AppOpsManager(android.content.Context context){if(context==null)throw new NullPointerException("context");this.context=context;synchronized(MANAGERS){for(int i=MANAGERS.size()-1;i>=0;i--)if(MANAGERS.get(i).get()==null)MANAGERS.remove(i);if(MANAGERS.size()>=1024)throw new IllegalStateException("AppOps context limit");MANAGERS.add(new java.lang.ref.WeakReference<AppOpsManager>(this));}}
 public static String permissionToOp(String permission){if("android.permission.CAMERA".equals(permission))return OPSTR_CAMERA;if("android.permission.RECORD_AUDIO".equals(permission))return OPSTR_RECORD_AUDIO;if("android.permission.ACCESS_FINE_LOCATION".equals(permission))return OPSTR_FINE_LOCATION;if("android.permission.ACCESS_COARSE_LOCATION".equals(permission))return OPSTR_COARSE_LOCATION;if("android.permission.SYSTEM_ALERT_WINDOW".equals(permission))return OPSTR_SYSTEM_ALERT_WINDOW;return null;}
 private static String permission(String op){if(OPSTR_CAMERA.equals(op))return "android.permission.CAMERA";if(OPSTR_RECORD_AUDIO.equals(op))return "android.permission.RECORD_AUDIO";if(OPSTR_FINE_LOCATION.equals(op))return "android.permission.ACCESS_FINE_LOCATION";if(OPSTR_COARSE_LOCATION.equals(op))return "android.permission.ACCESS_COARSE_LOCATION";if(OPSTR_SYSTEM_ALERT_WINDOW.equals(op))return "android.permission.SYSTEM_ALERT_WINDOW";return null;}
 public void checkPackage(int uid,String packageName){if(uid!=android.os.Process.myUid()||!context.getPackageName().equals(packageName))throw new SecurityException("Package does not belong to UID");}
 public synchronized void startWatchingMode(String op,String packageName,OnOpChangedListener listener){
  if(listener==null)throw new NullPointerException("listener");if(op!=null&&permission(op)==null)throw new IllegalArgumentException("Unsupported app operation: "+op);
  for(Watch watch:watches)if(watch.listener==listener&&java.util.Objects.equals(op,watch.op)&&java.util.Objects.equals(packageName,watch.pkg))return;
  if(watches.size()>=1024)throw new IllegalStateException("AppOps watch limit");
  if(handler==null){android.os.Looper main=android.os.Looper.getMainLooper();if(main==null)throw new IllegalStateException("AppOps watches need a main Looper");handler=new android.os.Handler(main);}
  Watch watch=new Watch(op,packageName,listener);for(String known:OPS)if(op==null||op.equals(known))watch.modes.put(known,checkOpNoThrow(known,android.os.Process.myUid(),packageName==null?context.getPackageName():packageName));watches.add(watch);
 }
 public synchronized void stopWatchingMode(OnOpChangedListener listener){for(int i=watches.size()-1;i>=0;i--)if(watches.get(i).listener==listener)watches.remove(i);}
 public static void shirazPermissionChanged(final String permission){
  java.util.ArrayList<AppOpsManager> targets=new java.util.ArrayList<AppOpsManager>();synchronized(MANAGERS){for(java.lang.ref.WeakReference<AppOpsManager> reference:MANAGERS){AppOpsManager manager=reference.get();if(manager!=null)targets.add(manager);}}
  for(AppOpsManager manager:targets)manager.queueChanges();
 }
 private void queueChanges(){
  android.os.Handler target=handler;if(target==null)return;
  synchronized(notificationLock){if(notificationPending)return;notificationPending=true;}
  if(!target.post(new Runnable(){public void run(){synchronized(notificationLock){notificationPending=false;}dispatchChanges(null);}})){
   synchronized(notificationLock){notificationPending=false;}android.util.Log.w("AppOps","Mode change notification could not be queued");
  }
 }
 private void dispatchChanges(String changed){
  java.util.ArrayList<Watch> snapshot;synchronized(this){snapshot=new java.util.ArrayList<Watch>(watches);}
  for(Watch watch:snapshot)for(String op:OPS)if((watch.op==null||watch.op.equals(op))&&(changed==null||changed.equals(permission(op)))){
   String pkg=watch.pkg==null?context.getPackageName():watch.pkg;int mode=checkOpNoThrow(op,android.os.Process.myUid(),pkg);boolean notify;
   synchronized(this){Integer previous=watch.modes.get(op);notify=watches.contains(watch)&&previous!=null&&previous.intValue()!=mode;if(notify)watch.modes.put(op,mode);}
   if(notify)watch.listener.onOpChanged(op,pkg);
  }
 }
 public int checkOpNoThrow(String op,int uid,String packageName){String permission=permission(op);if(permission==null||uid!=android.os.Process.myUid()||!context.getPackageName().equals(packageName))return MODE_ERRORED;return context.checkPermission(permission,android.os.Process.myPid(),uid)==android.content.pm.PackageManager.PERMISSION_GRANTED?MODE_ALLOWED:MODE_IGNORED;}
 public int noteOpNoThrow(String op,int uid,String packageName){return checkOpNoThrow(op,uid,packageName);}
 public int noteProxyOpNoThrow(String op,String packageName){return checkOpNoThrow(op,android.os.Process.myUid(),packageName);}
 public int noteProxyOp(String op,String packageName){int result=noteProxyOpNoThrow(op,packageName);if(result==MODE_ERRORED)throw new SecurityException("Unknown app operation");return result;}
}
