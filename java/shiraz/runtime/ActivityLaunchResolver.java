// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.content.pm.*;
import java.util.List;
/** Resolves launches within the loaded APK; no external application or task registry. */
public final class ActivityLaunchResolver {
 private final String packageName;
 private final PackageManager packages;
 public ActivityLaunchResolver(String packageName,PackageManager packages){if(packageName==null||packages==null)throw new NullPointerException();this.packageName=packageName;this.packages=packages;}
 public static final class Request {
  private final Intent intent;
  public final int launchMode;
  private Request(Intent intent,int mode){this.intent=intent;launchMode=mode;}
  public Intent intent(){return intent.shirazBroadcastCopy();}
  public int bytes(){return intent.shirazBroadcastBytes();}
 }
 public String packageName(){return packageName;}
 public Request resolve(Intent intent){
  if(intent==null)throw new NullPointerException("intent");
  Intent snapshot=intent.shirazBroadcastCopy();
  int singleTop=Intent.FLAG_ACTIVITY_SINGLE_TOP;
  int ignored=Intent.FLAG_ACTIVITY_NEW_TASK|0x80000|Intent.FLAG_ACTIVITY_NO_ANIMATION|Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
  int unsupported=snapshot.getFlags()&~(singleTop|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_CLEAR_TOP|ignored);
  if(unsupported!=0)throw new UnsupportedOperationException("Activity task flags are not implemented: 0x"+Integer.toHexString(unsupported));
  ComponentName component=snapshot.getComponent();
  if(snapshot.getPackage()!=null&&!packageName.equals(snapshot.getPackage())||component!=null&&!packageName.equals(component.getPackageName()))throw new ActivityNotFoundException("No external activity registry");
  ActivityInfo info;
  if(component!=null){
   try{info=packages.getActivityInfo(component,0);}catch(PackageManager.NameNotFoundException missing){throw new ActivityNotFoundException("Activity is unavailable: "+component.flattenToString());}
  }else{
   List<ResolveInfo> matches=packages.queryIntentActivities(snapshot,PackageManager.MATCH_DEFAULT_ONLY);
   if(matches.isEmpty())throw new ActivityNotFoundException("No matching activity in loaded APK");
   ResolveInfo best=matches.get(0);
   if(matches.size()>1&&matches.get(1).priority==best.priority&&matches.get(1).match==best.match)throw new UnsupportedOperationException("Activity chooser is not implemented");
   info=best.activityInfo;component=new ComponentName(info.packageName,info.name);snapshot.setComponent(component);
  }
  if(!packageName.equals(info.packageName)||!info.isEnabled())throw new ActivityNotFoundException("Activity is unavailable in loaded APK");
  if(info.processName!=null&&!packageName.equals(info.processName))throw new UnsupportedOperationException("Separate activity processes are not implemented");
  if(info.launchMode<0||info.launchMode>2)throw new UnsupportedOperationException("Separate activity tasks are not implemented");
  int mode=info.launchMode==0&&(snapshot.getFlags()&singleTop)!=0?1:info.launchMode;
  return new Request(snapshot.shirazBroadcastCopy(),mode);
 }
}
