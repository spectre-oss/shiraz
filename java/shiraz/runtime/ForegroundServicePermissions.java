// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.content.pm.PackageManager;
/** Normal type permissions only; runtime grants, roles and timeouts are separate policy. */
final class ForegroundServicePermissions {
 private static final int[] TYPES={1,2,4,8,16,32,64,128,256,512,1024,0x40000000};
 private static final String[] PERMISSIONS={
  "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
  "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
  "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
  "android.permission.FOREGROUND_SERVICE_LOCATION",
  "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
  "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
  "android.permission.FOREGROUND_SERVICE_CAMERA",
  "android.permission.FOREGROUND_SERVICE_MICROPHONE",
  "android.permission.FOREGROUND_SERVICE_HEALTH",
  "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
  "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
  "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
 };
 private ForegroundServicePermissions(){}
 static boolean isNormalPermission(String permission){
  if("android.permission.FOREGROUND_SERVICE".equals(permission))return true;
  for(String known:PERMISSIONS)if(known.equals(permission))return true;
  return false;
 }
 static void enforce(Context context,int type){
  for(int i=0;i<TYPES.length;i++)if((type&TYPES[i])!=0&&context.checkSelfPermission(PERMISSIONS[i])!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Foreground service requires "+PERMISSIONS[i]);
 }
}
