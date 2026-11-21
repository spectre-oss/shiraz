// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.*;
import shiraz.runtime.HostPackageManager;
public final class ForegroundTypeMetadataSmoke {
 public static void main(String[] args)throws Exception{
  java.util.Properties facts=new java.util.Properties();facts.setProperty("package","test.app");facts.setProperty("sourceDir","/app.apk");facts.setProperty("dataDir","/data");facts.setProperty("service.count","1");facts.setProperty("service.0.name","test.app.Worker");
  Context context=new ContextWrapper(null){public String getPackageName(){return "test.app";}};
  ComponentName name=new ComponentName("test.app","test.app.Worker");
  for(int value:new int[]{0,10,0x40000008}){
   facts.setProperty("service.0.foregroundServiceType",Integer.toString(value));
   HostPackageManager pm=new HostPackageManager(facts,context);
   ServiceInfo first=pm.getServiceInfo(name,0);
   if(first.getForegroundServiceType()!=value)throw new AssertionError("declared type");
   first.foregroundServiceType=123;
   if(pm.getServiceInfo(name,0).getForegroundServiceType()!=value)throw new AssertionError("snapshot mutation");
  }
  facts.remove("service.0.foregroundServiceType");
  if(new HostPackageManager(facts,context).getServiceInfo(name,0).getForegroundServiceType()!=0)throw new AssertionError("legacy default");
  facts.setProperty("service.0.foregroundServiceType","-1");
  try{new HostPackageManager(facts,context).getServiceInfo(name,0);throw new AssertionError("invalid declaration accepted");}catch(IllegalArgumentException expected){}
  System.out.println("FOREGROUND_TYPE_METADATA_OK");
 }
}
