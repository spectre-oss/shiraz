// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Android property view of the selected fabricated device profile. */
public final class SystemProperties {
 private SystemProperties(){}
 public static String get(String key){return get(key,"");}
 public static String get(String key,String fallback){
  if(key==null)throw new NullPointerException("key");
  if(shiraz.runtime.DeviceProfile.selected())shiraz.runtime.HostAppInstaller.recordAccess("property:"+key);
  switch(key){
   case "ro.product.model":return Build.MODEL;
   case "ro.product.manufacturer":return Build.MANUFACTURER;
   case "ro.product.brand":return Build.BRAND;
   case "ro.product.device":return Build.DEVICE;
   case "ro.product.name":return Build.PRODUCT;
   case "ro.product.board":return Build.BOARD;
   case "ro.hardware":return Build.HARDWARE;
   case "ro.product.cpu.abi":return Build.CPU_ABI;
   case "ro.build.id":return Build.ID;
   case "ro.build.display.id":return Build.DISPLAY;
   case "ro.build.type":return Build.TYPE;
   case "ro.build.tags":return Build.TAGS;
   case "ro.build.fingerprint":return Build.FINGERPRINT;
   case "ro.build.version.release":return Build.VERSION.RELEASE;
   case "ro.build.version.incremental":return Build.VERSION.INCREMENTAL;
   case "ro.build.version.sdk":return Integer.toString(Build.VERSION.SDK_INT);
   case "ro.build.version.security_patch":return Build.VERSION.SECURITY_PATCH;
   case "ro.kernel.qemu":return "0";
   case "gsm.sim.state":return shiraz.runtime.DeviceProfile.selected()?"ABSENT":fallback;
   default:return fallback;
  }
 }
 public static int getInt(String key,int fallback){try{return Integer.parseInt(get(key));}catch(NumberFormatException error){return fallback;}}
 public static long getLong(String key,long fallback){try{return Long.parseLong(get(key));}catch(NumberFormatException error){return fallback;}}
 public static boolean getBoolean(String key,boolean fallback){String value=get(key);if(value.isEmpty())return fallback;if("1".equals(value)||"true".equalsIgnoreCase(value)||"yes".equalsIgnoreCase(value)||"on".equalsIgnoreCase(value))return true;if("0".equals(value)||"false".equalsIgnoreCase(value)||"no".equalsIgnoreCase(value)||"off".equalsIgnoreCase(value))return false;return fallback;}
}
