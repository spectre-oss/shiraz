// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Wake-lock lifecycle; runtime-owned partial locks request native host idle-sleep inhibition. */
public class PowerManager {
 private final android.content.Context context;
 private boolean nativePartial,closed;
 private final java.util.HashSet<WakeLock> active=new java.util.HashSet<WakeLock>();
 public static PowerManager shirazForContext(android.content.Context context){PowerManager result=new PowerManager(context);result.nativePartial=true;return result;}
 public void shirazClose(){
  WakeLock[] pending;synchronized(this){closed=true;pending=active.toArray(new WakeLock[active.size()]);}
  RuntimeException failure=null;
  for(WakeLock lock:pending)try{lock.closeOwned();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
  if(failure!=null)throw failure;
 }
 public PowerManager(){context=null;}
 public PowerManager(android.content.Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 private void enforceWakeLock(){if(context==null||context.checkSelfPermission("android.permission.WAKE_LOCK")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Wake lock requires android.permission.WAKE_LOCK");}
 private void enforceAttribution(WorkSource source){if(source!=null&&!source.isEmpty()&&(context==null||context.checkSelfPermission("android.permission.UPDATE_DEVICE_STATS")!=android.content.pm.PackageManager.PERMISSION_GRANTED))throw new SecurityException("Wake-lock attribution requires android.permission.UPDATE_DEVICE_STATS");}
 public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS="android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
 public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS="android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
 public static final int PARTIAL_WAKE_LOCK=1,FULL_WAKE_LOCK=26,SCREEN_BRIGHT_WAKE_LOCK=10,SCREEN_DIM_WAKE_LOCK=6,ACQUIRE_CAUSES_WAKEUP=0x10000000,ON_AFTER_RELEASE=0x20000000;
 public class WakeLock {
  private final String tag;
  private final int level;
  private shiraz.runtime.NativePower nativeOwner;
  private boolean held,referenceCounted=true;
  private int internalCount,externalCount;
  private Handler handler;
  private WorkSource workSource;
  private Runnable timeoutRelease;
  WakeLock(int levelAndFlags,String tag){this.tag=tag==null?"":tag;level=levelAndFlags&0xffff;}
  private Runnable newTimeout(){return new Runnable(){public void run(){synchronized(WakeLock.this){if(timeoutRelease==this)releaseInternal(true);}}};}
  private void cancelTimeouts(){if(handler!=null&&timeoutRelease!=null)handler.removeCallbacks(timeoutRelease);timeoutRelease=null;}
  private void acquireInternal(Long timeout){
   synchronized(PowerManager.this){
    if(closed)throw new IllegalStateException("Power session closed");
    enforceWakeLock();enforceAttribution(workSource);
    if(internalCount==Integer.MAX_VALUE||externalCount==Integer.MAX_VALUE)throw new IllegalStateException("WakeLock acquisition limit");
    boolean reset=!referenceCounted||internalCount==0;
    Runnable next=reset||timeoutRelease==null?newTimeout():timeoutRelease;
    boolean fresh=!held;
    if(fresh&&active.size()>=256)throw new IllegalStateException("Session wake-lock limit");
    if(fresh)active.add(this);
    shiraz.runtime.NativePower acquired=null;
    try{
     if(fresh&&nativePartial&&level==PARTIAL_WAKE_LOCK){
      try{acquired=shiraz.runtime.NativePower.acquireIdleSleep();}
      catch(LinkageError unavailable){nativePartial=false;System.err.println("SHIRAZ_UNSUPPORTED wake-lock-native: host idle-sleep library unavailable");}
     }
     if(timeout!=null){
      if(handler==null)handler=new Handler(Looper.getMainLooper());
      if(!handler.postDelayed(next,Math.max(0,timeout.longValue())))throw new IllegalStateException("WakeLock timeout queue stopped");
     }
    }catch(RuntimeException|Error error){
     if(acquired!=null)try{acquired.close();}catch(RuntimeException cleanup){
      nativeOwner=acquired;closed=true;error.addSuppressed(cleanup);
     }
     if(fresh&&nativeOwner==null)active.remove(this);
     throw error;
    }
    if(reset)cancelTimeouts();
    if(acquired!=null)nativeOwner=acquired;
    timeoutRelease=next;internalCount++;externalCount++;held=true;
   }
  }
  public synchronized void acquire(){acquireInternal(null);}
  public synchronized void acquire(long timeout){acquireInternal(Long.valueOf(timeout));}
  private void releaseInternal(boolean timedOut){
   boolean last=!referenceCounted||internalCount<=1;
   if(last&&nativeOwner!=null){nativeOwner.close();nativeOwner=null;}
   if(internalCount>0)internalCount--;
   if(!timedOut)externalCount--;
   if(last){cancelTimeouts();held=false;synchronized(PowerManager.this){active.remove(this);}}
   if(referenceCounted&&externalCount<0)throw new RuntimeException("WakeLock under-locked: "+tag);
  }
  private synchronized void closeOwned(){
   if(nativeOwner!=null){nativeOwner.close();nativeOwner=null;}
   cancelTimeouts();internalCount=externalCount=0;held=false;
   synchronized(PowerManager.this){active.remove(this);}
  }
  public synchronized void release(){enforceWakeLock();releaseInternal(false);}
  public void release(int flags){release();}
  public synchronized void setWorkSource(WorkSource value){
   WorkSource next=value==null||value.isEmpty()?null:new WorkSource(value);
   if(java.util.Objects.equals(workSource,next))return;
   if(held){enforceWakeLock();enforceAttribution(next);}
   workSource=next;
  }
  public synchronized boolean isHeld(){return held;}
  public synchronized void setReferenceCounted(boolean value){referenceCounted=value;}
  @Override public synchronized String toString(){return "WakeLock{"+tag+" held="+held+"}";}

 }
 public interface OnThermalStatusChangedListener {
  void onThermalStatusChanged(int status);
 }
 public interface WakeLockStateListener {
  void onStateChanged(boolean enabled);
 }
 public static final int THERMAL_STATUS_NONE = 0, THERMAL_STATUS_LIGHT = 1, THERMAL_STATUS_MODERATE = 2,
  THERMAL_STATUS_SEVERE = 3, THERMAL_STATUS_CRITICAL = 4, THERMAL_STATUS_EMERGENCY = 5, THERMAL_STATUS_SHUTDOWN = 6;
 public int getCurrentThermalStatus() { return THERMAL_STATUS_NONE; }
 public void addThermalStatusListener(OnThermalStatusChangedListener listener) { java.util.Objects.requireNonNull(listener, "listener"); }
 public void addThermalStatusListener(java.util.concurrent.Executor executor, OnThermalStatusChangedListener listener) {
  java.util.Objects.requireNonNull(executor, "executor"); java.util.Objects.requireNonNull(listener, "listener");
 }
 public void removeThermalStatusListener(OnThermalStatusChangedListener listener) { java.util.Objects.requireNonNull(listener, "listener"); }
 public WakeLock newWakeLock(int levelAndFlags,String tag){return new WakeLock(levelAndFlags,tag);}
 public boolean isIgnoringBatteryOptimizations(String packageName){return true;}
 public boolean isPowerSaveMode(){return false;}
 public boolean isDeviceIdleMode(){return false;}
 public boolean isInteractive(){return true;}
 public boolean isScreenOn(){return true;}
 public int getLocationPowerSaveMode(){return 0;}
 public void userActivity(long when,boolean noChangeLights){}
 public void goToSleep(long time){}
 public void wakeUp(long time){}
 public void reboot(String reason){throw new UnsupportedOperationException("reboot");}
}
