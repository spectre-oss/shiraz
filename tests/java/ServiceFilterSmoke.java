// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.util.Properties;
public final class ServiceFilterSmoke {
 private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  Properties p=new Properties();p.setProperty("package","test.filters");p.setProperty("service.count","1");
  p.setProperty("sourceDir","/fixture/app.apk");p.setProperty("dataDir","/fixture/data");
  p.setProperty("service.0.name","test.filters.Worker");p.setProperty("service.0.filter.count","1");
  String f="service.0.filter.0.";
  p.setProperty(f+"action.count","1");p.setProperty(f+"action.0","test.BIND");
  p.setProperty(f+"category.count","1");p.setProperty(f+"category.0",Intent.CATEGORY_DEFAULT);
  p.setProperty(f+"mime.count","1");p.setProperty(f+"mime.0","text/plain");
  p.setProperty(f+"scheme.count","1");p.setProperty(f+"scheme.0","content");
  Context context=new ContextWrapper(null){public String getPackageName(){return "test.filters";}};
  PackageManager pm=new shiraz.runtime.HostPackageManager(p,context);
  check(pm.getLaunchIntentForPackage("test.filters")==null,"service-only metadata must not invent a launcher");
  Intent good=new Intent("test.BIND").setPackage("test.filters").setDataAndType(Uri.parse("content://fixture/item"),"text/plain");
  ResolveInfo match=pm.resolveService(good,PackageManager.GET_RESOLVED_FILTER|PackageManager.MATCH_DEFAULT_ONLY);
  check(match!=null&&match.match>0&&match.isDefault&&match.filter.hasDataType("text/plain"),"full matching metadata");
  check(pm.queryIntentServices(new Intent(good).addCategory("test.UNKNOWN"),0).isEmpty(),"category mismatch");
  check(pm.queryIntentServices(new Intent(good).setDataAndType(Uri.parse("https://fixture/item"),"text/plain"),0).isEmpty(),"scheme mismatch");
  check(pm.queryIntentServices(new Intent(good).setDataAndType(good.getData(),"image/png"),0).isEmpty(),"MIME mismatch");
  check(pm.queryIntentServices(new Intent("test.BIND"),0).isEmpty(),"missing required data");
  check(pm.queryIntentServices(new Intent(good).setPackage("test.other"),0).isEmpty(),"package restriction");
  check(pm.resolveService(new Intent("unmatched").setComponent(new ComponentName("test.filters","test.filters.Worker")),0)!=null,"explicit component bypasses filters");
  p.setProperty(f+"category.count","0");pm=new shiraz.runtime.HostPackageManager(p,context);
  check(pm.resolveService(good,PackageManager.MATCH_DEFAULT_ONLY)==null,"default category required");
  p.setProperty(f+"unsupported","true");pm=new shiraz.runtime.HostPackageManager(p,context);
  try{pm.resolveService(good,0);throw new AssertionError("unsupported authority/path accepted");}catch(UnsupportedOperationException expected){}
  Properties alias=new Properties();alias.setProperty("package","test.filters");
  alias.setProperty("sourceDir","/fixture/app.apk");alias.setProperty("dataDir","/fixture/data");
  alias.setProperty("activity.count","1");alias.setProperty("activity.0.name","test.filters.Main");alias.setProperty("activity.0.enabled","false");
  alias.setProperty("launcher.count","1");alias.setProperty("launcher.0.name","test.filters.Alias");alias.setProperty("launcher.0.target","test.filters.Main");
  alias.setProperty("alias.count","1");alias.setProperty("alias.0.name","test.filters.Alias");alias.setProperty("alias.0.target","test.filters.Main");
  pm=new shiraz.runtime.HostPackageManager(alias,context);
  check("test.filters.Alias".equals(pm.getLaunchIntentForPackage("test.filters").getComponent().getClassName()),"alias component must survive discovery");
  ActivityInfo aliasInfo=pm.getActivityInfo(new ComponentName("test.filters","test.filters.Alias"),0);
  check("test.filters.Main".equals(aliasInfo.targetActivity)&&aliasInfo.enabled,"alias ActivityInfo target and independent enabled");
  ActivityInfo[] listed=pm.getPackageInfo("test.filters",PackageManager.GET_ACTIVITIES).activities;
  check(listed.length==1&&"test.filters.Alias".equals(listed[0].name),"enumeration includes alias but excludes disabled target");
  check(pm.getPackageInfo("test.filters",PackageManager.GET_ACTIVITIES|PackageManager.MATCH_DISABLED_COMPONENTS).activities.length==2,"disabled component flag includes target");
  check(pm.resolveActivity(new Intent().setComponent(new ComponentName("test.filters","test.filters.Alias")),0).activityInfo.targetActivity.equals("test.filters.Main"),"explicit alias resolves");
  alias.setProperty("alias.0.filter.count","1");alias.setProperty("alias.0.filter.0.action.count","1");alias.setProperty("alias.0.filter.0.action.0","test.ALIAS");
  alias.setProperty("alias.0.filter.0.category.count","1");alias.setProperty("alias.0.filter.0.category.0",Intent.CATEGORY_DEFAULT);
  pm=new shiraz.runtime.HostPackageManager(alias,context);
  check("test.filters.Alias".equals(pm.resolveActivity(new Intent("test.ALIAS"),PackageManager.MATCH_DEFAULT_ONLY).activityInfo.name),"implicit alias filter");
  check(pm.resolveActivity(new Intent("test.ALIAS").addCategory("test.UNKNOWN"),0)==null,"alias category mismatch");
  check(pm.resolveActivity(new Intent("test.OTHER"),0)==null,"alias action mismatch");
  alias.setProperty("alias.0.enabled","false");pm=new shiraz.runtime.HostPackageManager(alias,context);
  check(pm.getLaunchIntentForPackage("test.filters")==null,"disabled alias excluded");
  alias.setProperty("alias.0.enabled","true");alias.setProperty("enabled","false");pm=new shiraz.runtime.HostPackageManager(alias,context);
  check(pm.getLaunchIntentForPackage("test.filters")==null,"disabled application excluded");
  System.out.println("SERVICE_FILTER_MATCHING_OK");
 }
}
