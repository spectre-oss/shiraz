// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.pm.*;
/** Compiles against the Android SDK; exercises facts from the compiled APK. */
public final class ActivityInfoFixture {
 public static class Sample extends android.app.Activity {}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void check(Context context)throws Exception {
  PackageManager pm=context.getPackageManager();ComponentName name=new ComponentName(context,Sample.class);
  int flags=PackageManager.GET_META_DATA|PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.MATCH_DIRECT_BOOT_AWARE;
  ActivityInfo info=pm.getActivityInfo(name,flags);
  check(info.launchMode==ActivityInfo.LAUNCH_SINGLE_TASK,"manifest launch mode");
  check(info.name.equals(name.getClassName())&&info.packageName.equals(context.getPackageName()),"activity identity");
  check(!info.isEnabled()&&!info.exported&&info.directBootAware&&info.processName.equals(context.getPackageName()+":screen"),"activity state/process");
  check(info.permission.equals("org.shiraz.fixture.PERMISSION")&&info.parentActivityName.equals("org.shiraz.fixture.FixtureActivity"),"permission and parent");
  check(info.configChanges==(ActivityInfo.CONFIG_ORIENTATION|ActivityInfo.CONFIG_SCREEN_SIZE|ActivityInfo.CONFIG_UI_MODE)&&info.screenOrientation==1,"activity configuration facts");
  check("true".equals(info.metaData.getString("text"))&&info.metaData.getInt("integer")==-7&&info.metaData.getInt("resource")==R.string.service_meta_text,"typed activity metadata");
  info.metaData.putInt("integer",0);info.configChanges=0;info.launchMode=0;info.applicationInfo.enabled=false;
  ActivityInfo again=pm.getActivityInfo(name,flags);
  check(again.launchMode==ActivityInfo.LAUNCH_SINGLE_TASK&&again.configChanges==1664&&again.applicationInfo.enabled&&again.metaData.getInt("integer")==-7,"fresh activity snapshots");
  check(pm.getActivityInfo(name,PackageManager.MATCH_DISABLED_COMPONENTS).metaData==null,"metadata flag");
  try{pm.getActivityInfo(name,0);throw new AssertionError("disabled activity matched");}catch(PackageManager.NameNotFoundException expected){}
  try{pm.getActivityInfo(name,PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.MATCH_DIRECT_BOOT_UNAWARE);throw new AssertionError("boot filter ignored");}catch(PackageManager.NameNotFoundException expected){}
  try{pm.getActivityInfo(new ComponentName("foreign.package",name.getClassName()),flags);throw new AssertionError("foreign activity matched");}catch(PackageManager.NameNotFoundException expected){}
  try{pm.getActivityInfo(new ComponentName(context.getPackageName(),"absent.Activity"),flags);throw new AssertionError("absent activity matched");}catch(PackageManager.NameNotFoundException expected){}
  try{pm.getActivityInfo(name,PackageManager.GET_SIGNATURES);throw new AssertionError("unsupported flags accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("SHIRAZ_FIXTURE_ACTIVITY_INFO_OK");
 }
}
