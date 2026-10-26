// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Power and battery-optimization stubs for desktop hosts. */
public class PowerManager {
 public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS="android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
 public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS="android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
 public static final int PARTIAL_WAKE_LOCK=1,FULL_WAKE_LOCK=26,SCREEN_BRIGHT_WAKE_LOCK=10,SCREEN_DIM_WAKE_LOCK=6,ACQUIRE_CAUSES_WAKEUP=0x10000000,ON_AFTER_RELEASE=0x20000000;
 public class WakeLock {
  private final String tag;private boolean held;private int count;
  WakeLock(int levelAndFlags,String tag){this.tag=tag==null?"":tag;}
  public void acquire(){held=true;count++;}
  public void acquire(long timeout){acquire();}
  public void release(){if(count>0)count--;held=count>0;}
  public boolean isHeld(){return held;}
  public void setReferenceCounted(boolean value){}
  @Override public String toString(){return "WakeLock{"+tag+" held="+held+"}";}
 }
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
