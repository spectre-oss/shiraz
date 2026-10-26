// SPDX-License-Identifier: GPL-3.0-only
package android.app;
/** App-op checks delegate to the loaded application's existing permission policy. */
public final class AppOpsManager {
 public static final int MODE_ALLOWED=0,MODE_IGNORED=1,MODE_ERRORED=2,MODE_DEFAULT=3;
 public static final String OPSTR_CAMERA="android:camera",OPSTR_RECORD_AUDIO="android:record_audio",OPSTR_FINE_LOCATION="android:fine_location",OPSTR_COARSE_LOCATION="android:coarse_location";
 private final android.content.Context context;
 public AppOpsManager(android.content.Context context){this.context=context;}
 public static String permissionToOp(String permission){if("android.permission.CAMERA".equals(permission))return OPSTR_CAMERA;if("android.permission.RECORD_AUDIO".equals(permission))return OPSTR_RECORD_AUDIO;if("android.permission.ACCESS_FINE_LOCATION".equals(permission))return OPSTR_FINE_LOCATION;if("android.permission.ACCESS_COARSE_LOCATION".equals(permission))return OPSTR_COARSE_LOCATION;return null;}
 private static String permission(String op){if(OPSTR_CAMERA.equals(op))return "android.permission.CAMERA";if(OPSTR_RECORD_AUDIO.equals(op))return "android.permission.RECORD_AUDIO";if(OPSTR_FINE_LOCATION.equals(op))return "android.permission.ACCESS_FINE_LOCATION";if(OPSTR_COARSE_LOCATION.equals(op))return "android.permission.ACCESS_COARSE_LOCATION";return null;}
 public int checkOpNoThrow(String op,int uid,String packageName){String permission=permission(op);if(permission==null||uid!=android.os.Process.myUid()||!context.getPackageName().equals(packageName))return MODE_ERRORED;return context.checkPermission(permission,android.os.Process.myPid(),uid)==android.content.pm.PackageManager.PERMISSION_GRANTED?MODE_ALLOWED:MODE_IGNORED;}
 public int noteOpNoThrow(String op,int uid,String packageName){return checkOpNoThrow(op,uid,packageName);}
 public int noteProxyOpNoThrow(String op,String packageName){return checkOpNoThrow(op,android.os.Process.myUid(),packageName);}
 public int noteProxyOp(String op,String packageName){int result=noteProxyOpNoThrow(op,packageName);if(result==MODE_ERRORED)throw new SecurityException("Unknown app operation");return result;}
}
