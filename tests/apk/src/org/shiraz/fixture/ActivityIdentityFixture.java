// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
/** SDK-level identity checks also run on every resize recreation. */
public final class ActivityIdentityFixture {
 public static void check(Activity activity) {
  if(activity.getVolumeControlStream()!=Integer.MIN_VALUE)throw new AssertionError("new window default volume stream");
  activity.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
  if(activity.getWindow().getVolumeControlStream()!=android.media.AudioManager.STREAM_MUSIC)throw new AssertionError("activity to window stream selection");
  activity.getWindow().setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);
  if(activity.getVolumeControlStream()!=android.media.AudioManager.STREAM_ALARM)throw new AssertionError("window to activity stream selection");
  activity.setVolumeControlStream(Integer.MIN_VALUE);
  android.widget.FrameLayout awakeRoot=new android.widget.FrameLayout(activity);android.view.View awakeChild=new android.view.View(activity);awakeRoot.addView(awakeChild);
  if(awakeChild.getKeepScreenOn())throw new AssertionError("screen awake default");awakeChild.setKeepScreenOn(true);
  try{java.lang.reflect.Method requests=android.view.View.class.getMethod("shirazRequestsKeepScreenOn");
   if(!((Boolean)requests.invoke(awakeRoot)))throw new AssertionError("visible descendant screen request");
   awakeChild.setVisibility(android.view.View.GONE);if((Boolean)requests.invoke(awakeRoot))throw new AssertionError("hidden child screen request");
   awakeChild.setVisibility(android.view.View.VISIBLE);awakeRoot.setVisibility(android.view.View.INVISIBLE);if((Boolean)requests.invoke(awakeRoot))throw new AssertionError("hidden ancestor screen request");
   awakeRoot.setVisibility(android.view.View.VISIBLE);awakeRoot.removeView(awakeChild);if((Boolean)requests.invoke(awakeRoot))throw new AssertionError("removed child screen request");
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}
  android.content.pm.PackageManager pm=activity.getPackageManager();
  android.content.Intent main=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(activity.getPackageName());
  java.util.List<android.content.pm.ResolveInfo> launchers=pm.queryIntentActivities(main,android.content.pm.PackageManager.GET_RESOLVED_FILTER);
  if(launchers.isEmpty()||launchers.get(0).filter==null||!launchers.get(0).filter.hasAction(android.content.Intent.ACTION_MAIN))throw new AssertionError("manifest launcher resolution");
  String resolvedName=launchers.get(0).activityInfo.name;launchers.get(0).activityInfo.name="mutated";
  if(!resolvedName.equals(pm.queryIntentActivities(main,0).get(0).activityInfo.name))throw new AssertionError("resolution snapshots");
  if(!pm.queryIntentActivities(new android.content.Intent("org.shiraz.NO_MATCH"),0).isEmpty())throw new AssertionError("unmatched action");
  if(!pm.queryIntentActivities(new android.content.Intent(main).setPackage("foreign.package"),0).isEmpty())throw new AssertionError("foreign package resolution");
  if(!pm.queryIntentActivities(new android.content.Intent(main).addCategory("org.shiraz.NO_CATEGORY"),0).isEmpty())throw new AssertionError("category mismatch");
  if(pm.queryIntentActivities(new android.content.Intent("ignored").setComponent(new ComponentName(activity,activity.getClass())),0).size()!=1)throw new AssertionError("explicit activity resolution");
  android.util.TypedValue beforeDialog=new android.util.TypedValue();boolean hadTextColor=activity.getTheme().resolveAttribute(android.R.attr.textColor,beforeDialog,true);
  android.app.Dialog themedDialog=new android.app.Dialog(activity,R.style.TypographyBase);
  android.util.TypedValue dialogColor=new android.util.TypedValue();if(!themedDialog.getContext().getTheme().resolveAttribute(android.R.attr.textColor,dialogColor,true)||dialogColor.data!=0xff223344)throw new AssertionError("dialog explicit theme");
  if(themedDialog.getContext()==activity||themedDialog.getLayoutInflater().getContext()!=themedDialog.getContext())throw new AssertionError("dialog inflater theme context");
  android.util.TypedValue afterDialog=new android.util.TypedValue();if(activity.getTheme().resolveAttribute(android.R.attr.textColor,afterDialog,true)!=hadTextColor||hadTextColor&&(beforeDialog.type!=afterDialog.type||beforeDialog.data!=afterDialog.data))throw new AssertionError("dialog theme leaked to activity");
  android.widget.TextView dialogText=(android.widget.TextView)themedDialog.getLayoutInflater().inflate(R.layout.dialog_theme_fixture,null,false);if(dialogText.getCurrentTextColor()!=0xff223344)throw new AssertionError("dialog child theme color");
  themedDialog.setContentView(dialogText);themedDialog.create();
  ComponentName expected=new ComponentName(activity,activity.getClass());
  if(!expected.equals(activity.getComponentName())||!activity.getLocalClassName().equals(activity.getClass().getName().substring(activity.getPackageName().length()+1)))throw new AssertionError("activity launch identity");
  android.content.Intent intent=activity.getIntent();if(intent==null||!android.content.Intent.ACTION_MAIN.equals(intent.getAction())||!intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)||!expected.equals(intent.getComponent()))throw new AssertionError("launcher intent identity");
  android.content.SharedPreferences preferences=activity.getPreferences(Activity.MODE_PRIVATE);
  android.content.SharedPreferences named=activity.getSharedPreferences(activity.getLocalClassName(),Activity.MODE_PRIVATE);
  String marker="activity-scope-"+activity.getClass().getName();
  if(preferences.contains("fixture.scope")&&!marker.equals(preferences.getString("fixture.scope",null)))throw new AssertionError("activity preferences scope collision");
  if(!preferences.edit().putString("fixture.scope",marker).commit()||!marker.equals(named.getString("fixture.scope",null)))throw new AssertionError("activity preferences persistent alias");
  android.content.SharedPreferences other=activity.getSharedPreferences("different-activity-scope",Activity.MODE_PRIVATE);if(other.contains("fixture.scope"))throw new AssertionError("activity preferences escaped scope");
  try {
   ActivityInfo info=activity.getPackageManager().getActivityInfo(activity.getComponentName(),0);
   if(!info.name.equals(activity.getClass().getName())||!info.packageName.equals(activity.getPackageName()))throw new AssertionError("activity metadata identity");
  }catch(android.content.pm.PackageManager.NameNotFoundException error){throw new AssertionError(error);}
  System.out.println("SHIRAZ_FIXTURE_ACTIVITY_IDENTITY_OK");
 }
}
