// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.Properties;
import java.util.concurrent.*;
import shiraz.runtime.HostBroadcasts;
import shiraz.runtime.HostPackageManager;
/** Per-delivery outcome must not confuse finish(), return, timeout and cancellation. */
public final class InboundBroadcastSmoke {
 private static BroadcastReceiver.PendingResult outstanding;
 private static HostBroadcasts active;
 public static final class Receiver extends BroadcastReceiver {
  public void onReceive(Context context,Intent intent){
   check(Looper.myLooper()==Looper.getMainLooper(),"main thread");
   String action=intent.getAction();
   if("async".equals(action)||"timeout".equals(action)){outstanding=goAsync();return;}
   if("finish-throw".equals(action)){
    goAsync().finish();check(active.hasWork(),"finish before callback return retains ownership");
    throw new IllegalStateException("expected callback failure");
   }
   if("throw".equals(action))throw new IllegalStateException("expected callback failure");
  }
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static Intent intent(String action){return new Intent(action).setComponent(new ComponentName("test.inbound",Receiver.class.getName()));}
 private static void failed(Future<Void> completion)throws Exception {
  check(completion.isDone(),"failed delivery completes outcome");
  try{completion.get();throw new AssertionError("failure reported success");}
  catch(ExecutionException|CancellationException expected){}
 }
 public static void run()throws Exception {
  final Properties facts=new Properties();facts.setProperty("package","test.inbound");facts.setProperty("sourceDir","/fixture.apk");facts.setProperty("dataDir","/fixture");
  facts.setProperty("receiver.count","1");facts.setProperty("receiver.0.name",Receiver.class.getName());
  Context context=new ContextWrapper(null){
   public String getPackageName(){return "test.inbound";}
   public Context getApplicationContext(){return this;}
   public PackageManager getPackageManager(){return new HostPackageManager(facts,this);}
  };
  active=new HostBroadcasts(context,30);
  try{
   for(Intent invalid:new Intent[]{new Intent("sync"),intent("sync").setPackage("test.other"),
       new Intent("sync").setComponent(new ComponentName("test.other",Receiver.class.getName()))}){
    try{active.receiveAdmitted(invalid);throw new AssertionError("inbound target accepted");}catch(SecurityException expected){}
   }
   try{active.receiveAdmitted(intent("sync").addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY));throw new AssertionError("registered-only accepted");}catch(UnsupportedOperationException expected){}
   try{active.receiveAdmitted(new Intent().setComponent(new ComponentName("test.inbound","Missing")));throw new AssertionError("missing receiver accepted");}catch(IllegalArgumentException expected){}
   check(!active.hasWork(),"rejected admission does not queue work");
   Future<Void> success=active.receiveAdmitted(intent("sync"));check(!success.isDone(),"queued delivery");
   Looper.myLooper().shirazDispatchDue(256);success.get(1,TimeUnit.SECONDS);check(!active.hasWork(),"sync cleanup");
   Future<Void> async=active.receiveAdmitted(intent("async"));Looper.myLooper().shirazDispatchDue(256);
   check(!async.isDone()&&active.hasWork(),"async remains owned");
   Thread finisher=new Thread(new Runnable(){public void run(){outstanding.finish();}});finisher.start();finisher.join();
   async.get(1,TimeUnit.SECONDS);check(!active.hasWork(),"off-main completion");
   for(String action:new String[]{"throw","finish-throw"}){
    Future<Void> broken=active.receiveAdmitted(intent(action));
    try{Looper.myLooper().shirazDispatchDue(256);throw new AssertionError("callback failure swallowed");}catch(IllegalStateException expected){}
    failed(broken);check(!active.hasWork(),"failure cleanup");
   }
   Future<Void> expired=active.receiveAdmitted(intent("timeout"));Looper.myLooper().shirazDispatchDue(256);Thread.sleep(50);
   try{Looper.myLooper().shirazDispatchDue(256);throw new AssertionError("deadline swallowed");}catch(IllegalStateException expected){}
   failed(expired);check(!active.hasWork(),"deadline cleanup");
   Future<Void> canceled=active.receiveAdmitted(intent("sync"));active.close();failed(canceled);check(!active.hasWork(),"close cleanup");
  }finally{active.close();}
  active=new HostBroadcasts(context,30);
  try{
   Future<Void> canceled=active.receiveAdmitted(intent("async"));Looper.myLooper().shirazDispatchDue(256);
   active.close();failed(canceled);
   try{outstanding.finish();throw new AssertionError("finish after teardown accepted");}catch(IllegalStateException expected){}
  }finally{active.close();}
  System.out.println("INBOUND_BROADCAST_OUTCOMES_OK");
 }
}
