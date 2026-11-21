// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
public class ResolveInfo {
 public ProviderInfo providerInfo;public ActivityInfo activityInfo;public ServiceInfo serviceInfo;public android.content.IntentFilter filter;public int priority,preferredOrder,match,specificIndex=-1,labelRes,icon;public boolean isDefault;public CharSequence nonLocalizedLabel;public String resolvePackageName;
 private ComponentInfo getComponentInfo(){if(activityInfo!=null)return activityInfo;if(serviceInfo!=null)return serviceInfo;if(providerInfo!=null)return providerInfo;return null;}
 public CharSequence loadLabel(PackageManager pm){
  if(nonLocalizedLabel!=null)return nonLocalizedLabel;
  if(pm!=null&&resolvePackageName!=null&&labelRes!=0){CharSequence label=pm.getText(resolvePackageName,labelRes,null);if(label!=null)return label;}
  ComponentInfo ci=getComponentInfo();
  if(ci!=null&&pm!=null)return ci.loadLabel(pm);
  return ci!=null&&ci.name!=null?ci.name:"";
 }
 public android.graphics.drawable.Drawable loadIcon(PackageManager pm){
  ComponentInfo ci=getComponentInfo();
  if(ci!=null&&pm!=null)return ci.loadIcon(pm);
  return pm!=null?pm.getDefaultActivityIcon():null;
 }
 public static class DisplayNameComparator implements java.util.Comparator<ResolveInfo> {
  private final java.text.Collator mCollator=java.text.Collator.getInstance();
  private final PackageManager mPM;
  public DisplayNameComparator(PackageManager pm){mPM=pm;mCollator.setStrength(java.text.Collator.PRIMARY);}
  @Override public final int compare(ResolveInfo a,ResolveInfo b){
   CharSequence sa=a.loadLabel(mPM);if(sa==null&&a.activityInfo!=null)sa=a.activityInfo.name;if(sa==null)sa="";
   CharSequence sb=b.loadLabel(mPM);if(sb==null&&b.activityInfo!=null)sb=b.activityInfo.name;if(sb==null)sb="";
   return mCollator.compare(sa.toString(),sb.toString());
  }
 }
}
