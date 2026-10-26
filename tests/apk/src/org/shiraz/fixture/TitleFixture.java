// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import java.nio.charset.StandardCharsets;
public final class TitleFixture {
 static{System.loadLibrary("shiraz-test-input");}
 private static native void assertNativeTitle(byte[] expected);
 private static final String FIRST="OsmAnd café 🗺",SECOND="Updated route 🌍";
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static final class Probe extends Activity {
  int calls;protected void onTitleChanged(CharSequence title,int color){check(getTitle()==title&&getTitleColor()==color,"title state before callback");calls++;super.onTitleChanged(title,color);}
 }
 public static void initial(Activity activity){
  if(activity.getClass().getName().endsWith("$Handled"))check("Handled resize title".contentEquals(activity.getTitle()),"literal activity label");
  try{ActivityInfo info=activity.getPackageManager().getActivityInfo(activity.getComponentName(),0);CharSequence expected=info.nonLocalizedLabel!=null?info.nonLocalizedLabel:info.labelRes!=0?activity.getString(info.labelRes):activity.getPackageManager().getApplicationLabel(info.applicationInfo);check(expected.toString().equals(activity.getTitle().toString()),"manifest title before onCreate");}
  catch(android.content.pm.PackageManager.NameNotFoundException e){throw new AssertionError(e);}
 }
 public static void install(Activity activity){
  initial(activity);check(activity.getTitle().toString().equals(activity.getString(activity.getResources().getIdentifier("welcome","string",activity.getPackageName()))),"activity resource label overrides application label");Probe probe=new Probe();probe.setTitle("value");probe.setTitleColor(0xff123456);probe.setTitle((CharSequence)null);check(probe.calls==3&&probe.getTitle()==null,"title callbacks/null");
  try{probe.setTitle("invalid\0title");throw new AssertionError("NUL title accepted");}catch(IllegalArgumentException expected){}
  check(probe.getTitle()==null&&probe.calls==3,"invalid title mutated state");
  int resource=activity.getResources().getIdentifier("welcome","string",activity.getPackageName());activity.setTitle(resource);check(activity.getTitle().toString().equals(activity.getString(resource)),"resource title");activity.setTitle(FIRST);
 }
 public static void nativeTitle(String title){assertNativeTitle((title+" | Shiraz "+android.os.Build.VERSION.INCREMENTAL).getBytes(StandardCharsets.UTF_8));}
 public static void frame(Activity activity,int frame){if(frame==0){nativeTitle(FIRST);activity.setTitle(SECOND);}else if(frame==1){nativeTitle(SECOND);System.out.println("ACTIVITY_TITLE_SDL_OK");}}
}
