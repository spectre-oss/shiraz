// SPDX-License-Identifier: GPL-3.0-only
import android.app.Activity;
import android.os.*;
import android.content.res.Configuration;
import shiraz.runtime.*;
public final class PermissionSmoke {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static class Sample extends Activity {
  int calls,code;String[] names;int[] results;boolean grant,again,fail;
  public int checkSelfPermission(String permission){return grant&&permission.equals("explicit.test")?0:-1;}
  public void onRequestPermissionsResult(int code,String[] names,int[] results){this.code=code;this.names=names;this.results=results;calls++;if(again){again=false;requestPermissions(new String[]{"second"},99);}if(fail)throw new IllegalStateException("callback");}
  protected void onSaveInstanceState(Bundle b){}protected void onRestoreInstanceState(Bundle b){}
 }
 static void dispatch(){Looper.myLooper().shirazDispatchDue(100);}
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();try{
   Sample a=new Sample();String[] input={"android.permission.ACCESS_FINE_LOCATION","explicit.test"};a.grant=true;a.requestPermissions(input,7);input[0]="mutated";check(a.calls==0,"result must be asynchronous");dispatch();check(a.calls==1&&a.code==7&&a.names[0].equals("android.permission.ACCESS_FINE_LOCATION")&&a.results[0]==-1&&a.results[1]==0,"snapshot and policy results");check(!a.shouldShowRequestPermissionRationale("android.permission.ACCESS_FINE_LOCATION"),"unavailable grant UI has no rationale");
   a.requestPermissions(new String[]{"first"},8);a.requestPermissions(new String[]{"overlap"},9);check(a.calls==2&&a.code==9&&a.names.length==0&&a.results.length==0,"API27 overlap canceled immediately");dispatch();check(a.calls==3&&a.code==8,"original request preserved");
   a.again=true;a.requestPermissions(new String[0],10);dispatch();check(a.code==99&&a.calls==5,"empty request and callback reentrancy");
   for(int kind=0;kind<4;kind++){try{a.requestPermissions(kind==0?null:kind==1?new String[]{null}:kind==2?new String[]{""}:new String[0],kind==3?-1:0);throw new AssertionError("invalid request accepted");}catch(IllegalArgumentException|NullPointerException expected){}}
   a.fail=true;a.requestPermissions(new String[]{"one"},11);try{dispatch();throw new AssertionError("callback failure hidden");}catch(IllegalStateException expected){}a.fail=false;a.requestPermissions(new String[]{"two"},12);dispatch();check(a.code==12,"callback failure releases pending request");
   final Sample cross=a;final Throwable[] error={null};Thread worker=new Thread(new Runnable(){public void run(){try{cross.requestPermissions(new String[]{"x"},1);}catch(Throwable t){error[0]=t;}}});worker.start();worker.join();check(error[0] instanceof IllegalStateException,"off-main request rejected");
   java.util.Properties metadata=new java.util.Properties();metadata.setProperty("activity.count","1");metadata.setProperty("activity.0.name","sample.Main");
   ActivityLifecycle life=new ActivityLifecycle(new ActivityLifecycle.Factory(){public Activity create(){return new Sample();}},new ActivityLifecycle.Cleanup(){public void detached(Activity value){}},new ActivityConfigurationPolicy(metadata,"sample.Main",27),27);life.start();Sample old=(Sample)life.current();old.requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION"},32);life.reconfigure(new Configuration(),128,new Runnable(){public void run(){}});Sample replacement=(Sample)life.current();dispatch();check(old.calls==0&&old.isDestroyed()&&replacement.calls==1&&replacement.code==32&&replacement.results[0]==-1,"pending result transferred across recreation");
   replacement.requestPermissions(new String[]{"x"},33);life.close();dispatch();check(replacement.calls==1,"destroy cancels stale delivery");
   System.out.println("PERMISSION_ASYNC_LIFECYCLE_OK");
  }finally{Looper.shirazShutdownMain();}
 }
}
