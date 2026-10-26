// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
public final class ServiceMetadataFixture {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void check(Context context)throws Exception {
  PackageManager pm=context.getPackageManager();ComponentName name=new ComponentName(context,ServiceFixture.Sample.class);
  ServiceInfo info=pm.getServiceInfo(name,PackageManager.GET_META_DATA);Bundle data=info.metaData;
  check(info.name.equals(name.getClassName())&&info.packageName.equals(context.getPackageName())&&info.processName.equals(context.getPackageName()),"service identity");
  check(info.isEnabled()&&!info.exported&&info.directBootAware&&info.permission.equals("org.shiraz.fixture.PERMISSION")&&(info.flags&ServiceInfo.FLAG_STOP_WITH_TASK)!=0,"service manifest fields");
  check(data!=null&&data.getBoolean("boolean")&&data.getInt("integer")==-7&&data.getFloat("float")==1.25f&&data.getInt("color")==0xff123456,"typed metadata literals");
  check("true".equals(data.getString("text"))&&data.get("text") instanceof String&&data.getBoolean("resolved_bool")&&data.getInt("resolved_int")==-27&&data.getInt("resource")==R.string.service_meta_text,"metadata resource resolution/type preservation");
  data.putBoolean("boolean",false);info.applicationInfo.enabled=false;info.name="changed";
  ServiceInfo again=pm.getServiceInfo(name,PackageManager.GET_META_DATA);check(again.metaData.getBoolean("boolean")&&again.isEnabled()&&again.name.equals(name.getClassName()),"independent service query snapshots");check(pm.getServiceInfo(name,0).metaData==null,"metadata flag respected");
  ComponentName disabled=new ComponentName(context,ServiceFixture.Disabled.class);
  try{pm.getServiceInfo(disabled,0);throw new AssertionError("disabled component matched");}catch(PackageManager.NameNotFoundException expected){}
  check(!pm.getServiceInfo(disabled,PackageManager.MATCH_DISABLED_COMPONENTS|PackageManager.GET_META_DATA).isEnabled(),"disabled metadata query");
  try{pm.getServiceInfo(name,PackageManager.MATCH_DIRECT_BOOT_UNAWARE);throw new AssertionError("direct boot filter ignored");}catch(PackageManager.NameNotFoundException expected){}
  check(pm.getServiceInfo(name,PackageManager.MATCH_DIRECT_BOOT_AWARE).directBootAware,"direct boot aware match");
  check(pm.getServiceInfo(new ComponentName(context,ServiceFixture.Separate.class),0).processName.equals(context.getPackageName()+":separate"),"private process expansion");
  try{pm.getServiceInfo(new ComponentName("absent.package",name.getClassName()),0);throw new AssertionError("foreign package matched");}catch(PackageManager.NameNotFoundException expected){}
  try{pm.getServiceInfo(name,PackageManager.GET_SIGNATURES);throw new AssertionError("unsupported flags accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("SHIRAZ_FIXTURE_SERVICE_METADATA_OK");
 }
}
