// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class ActivityProcessIdentitySmoke {
 public static void main(String[] args){
  String pkg="sample.app";
  for(String value:new String[]{null,pkg,pkg+":restart","shared.process","sample.app:worker.child"}){
   String actual=ActivityRunner.activityProcessName(pkg,value);
   if(!actual.equals(value==null?pkg:value))throw new AssertionError("Process name changed");
  }
  for(String value:new String[]{"",":restart","foreign:restart","sample.app:","sample.app:a:b","../x","name\nextra","sample/app"}){
   try{ActivityRunner.activityProcessName(pkg,value);throw new AssertionError("Invalid process accepted");}catch(IllegalArgumentException expected){}
  }
  System.out.println("ACTIVITY_PROCESS_IDENTITY_OK");
 }
}
