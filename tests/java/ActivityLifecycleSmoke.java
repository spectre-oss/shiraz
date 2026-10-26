// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Configuration;
import shiraz.runtime.*;
import org.shiraz.fixture.LifecycleFixture;
import java.util.Properties;
public final class ActivityLifecycleSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message+": "+LifecycleFixture.events);}
 private static Configuration size(int width,int height){Configuration c=new Configuration();c.screenWidthDp=width;c.screenHeightDp=height;c.smallestScreenWidthDp=Math.min(width,height);return c;}
 private static void buckets(final Context context,Properties metadata){
  ActivityConfigurationPolicy policy=new ActivityConfigurationPolicy(metadata,"sample.Main",27);Configuration qualifier=new Configuration();qualifier.screenWidthDp=600;policy.setSizeConfigurations(new Configuration[]{qualifier});
  final int[] detached={0};ActivityLifecycle life=new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){return new LifecycleFixture.Sample(context);}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity activity){detached[0]++;}},policy,27);
  life.start();LifecycleFixture.Sample first=(LifecycleFixture.Sample)life.current();first.value=79;LifecycleFixture.events.clear();
  check(!life.reconfigure(size(650,800),size(640,790),0xc00,new Runnable(){public void run(){LifecycleFixture.events.add("apply");}}),"same bucket recreated");check(life.current()==first&&detached[0]==0&&LifecycleFixture.events.toString().equals("[apply, configuration]"),"same bucket resources apply before callback without detaching");
  LifecycleFixture.events.clear();check(life.reconfigure(size(640,790),size(599,790),0xc00,new Runnable(){public void run(){LifecycleFixture.events.add("apply");}}),"resource bucket boundary not recreated");check(life.current()!=first&&detached[0]==1&&((LifecycleFixture.Sample)life.current()).value==79,"crossing restores activity state");life.close();
 }
 private static void covered(final Context context,Properties metadata){
  final int[] created={0},detached={0};
  ActivityLifecycle life=new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){created[0]++;return new LifecycleFixture.Sample(context);}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity activity){detached[0]++;}},new ActivityConfigurationPolicy(metadata,"sample.Main",27),27);
  try{life.resumeFromBackStack();throw new AssertionError("uncreated activity resumed");}catch(IllegalStateException expected){}
  life.start();LifecycleFixture.Sample original=(LifecycleFixture.Sample)life.current();original.value=91;LifecycleFixture.events.clear();
  try{life.stopCovered();throw new AssertionError("resumed activity stopped without pause");}catch(IllegalStateException expected){}
  check(LifecycleFixture.events.isEmpty(),"invalid transition changed callbacks");
  life.pauseForLaunch();life.pauseForLaunch();check(LifecycleFixture.events.toString().equals("[pause:false]"),"pause exactly once");
  life.resumeFromBackStack();check(LifecycleFixture.events.toString().equals("[pause:false, resume, postResume]"),"failed covering launch resumes without restart");
  LifecycleFixture.events.clear();life.pauseForLaunch();life.stopCovered();life.stopCovered();
  check(LifecycleFixture.events.toString().equals("[pause:false, stop]")&&detached[0]==0&&!original.isDestroyed(),"covered instance retained");
  life.resumeFromBackStack();life.resumeFromBackStack();
  check(LifecycleFixture.events.toString().equals("[pause:false, stop, restart, start, resume, postResume]"),"back-stack restart order exactly once");
  check(life.current()==original&&original.value==91&&created[0]==1,"back navigation preserves live instance state");
  LifecycleFixture.events.clear();life.pauseForLaunch();life.stopCovered();life.close();life.close();
  check(LifecycleFixture.events.toString().equals("[pause:false, stop, destroy]")&&detached[0]==1,"closing covered activity destroys without duplicate pause/stop");
  try{life.resumeFromBackStack();throw new AssertionError("closed activity resumed");}catch(IllegalStateException expected){}
 }
 private static void stack(final Context context,final Properties metadata){
  final java.util.ArrayList<LifecycleFixture.Sample> made=new java.util.ArrayList<LifecycleFixture.Sample>();final int[] detached={0};
  ActivityStack stack=new ActivityStack(new ActivityStack.Factory(){public ActivityLifecycle create(final Intent intent){if("fail".equals(intent.getAction()))throw new IllegalArgumentException("launch failure");return new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){LifecycleFixture.Sample a=new LifecycleFixture.Sample(context){protected void onCreate(android.os.Bundle state){super.onCreate(state);if("fail-create".equals(intent.getAction()))throw new IllegalArgumentException("create failure");}};a.setIntent(intent);made.add(a);return a;}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity a){detached[0]++;}},new ActivityConfigurationPolicy(metadata,"sample.Main",27),27);}});
  check(stack.current()==null&&stack.size()==0&&!stack.finishTop(),"empty stack");
  Intent launch=new Intent("map").putExtra("value",1);stack.launch(launch);launch.putExtra("value",2);LifecycleFixture.Sample map=made.get(0);map.value=73;check(map.getIntent().getIntExtra("value",0)==1,"launch intent snapshot");
  LifecycleFixture.events.clear();stack.launch(new Intent("download"));LifecycleFixture.Sample download=made.get(1);
  check(LifecycleFixture.events.toString().equals("[pause:false, create, start, postCreate, resume, postResume, stop]"),"new screen resumes before covered screen stops");
  check(stack.current()==download&&stack.size()==2&&!map.isDestroyed()&&detached[0]==0,"push retains old activity");
  LifecycleFixture.events.clear();download.finish();check(stack.finishTop()&&stack.current()==map&&map.value==73&&download.isDestroyed(),"back restores map instance");
  check(LifecycleFixture.events.toString().equals("[pause:false, restart, start, resume, postResume, stop, destroy]"),"back lifecycle order");
  LifecycleFixture.events.clear();try{stack.launch(new Intent("fail"));throw new AssertionError("factory failure swallowed");}catch(IllegalArgumentException expected){}
  check(stack.current()==map&&stack.size()==1&&LifecycleFixture.events.toString().equals("[pause:false, resume, postResume]"),"failed launch restores current activity");
  stack.launch(new Intent("download"));map.finish();made.get(2).finish();check(stack.finishTop()&&stack.current()==null&&stack.size()==0&&map.isDestroyed(),"skip finished covered activity");
  stack.launch(new Intent("root"));LifecycleFixture.Sample root=(LifecycleFixture.Sample)stack.current();
  try{stack.launch(new Intent("fail-create"));throw new AssertionError("creation failure swallowed");}catch(IllegalArgumentException expected){check(expected.getMessage().equals("create failure"),"creation exception identity");}
  check(stack.current()==root&&stack.size()==1&&made.get(made.size()-1).isDestroyed(),"failed creation cleaned up and old screen resumed");
  for(int i=1;i<32;i++)stack.launch(new Intent("depth"));int count=made.size();
  try{stack.launch(new Intent("overflow"));throw new AssertionError("stack budget bypassed");}catch(IllegalStateException expected){}
  check(stack.size()==32&&made.size()==count,"depth overflow rejected before creation");
  stack.close();stack.close();check(detached[0]==36,"all stack entries destroyed once");try{stack.launch(new Intent("closed"));throw new AssertionError("closed stack launched");}catch(IllegalStateException expected){}
 }
 private static final java.util.ArrayList<Intent> delivered=new java.util.ArrayList<Intent>();
 public static class RetainedMap extends LifecycleFixture.Sample {public RetainedMap(Context c){super(c);}protected void onNewIntent(Intent value){delivered.add(value);LifecycleFixture.events.add("newIntent");}}
 public static class DownloadScreen extends LifecycleFixture.Sample {public DownloadScreen(Context c){super(c);}}
 private static void retainedLaunches(final Context context,final Properties metadata){
  final int[] created={0},detached={0};delivered.clear();
  ActivityStack stack=new ActivityStack(new ActivityStack.Factory(){public ActivityLifecycle create(final Intent intent){return new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){created[0]++;LifecycleFixture.Sample a=intent.getComponent().getClassName().equals(RetainedMap.class.getName())?new RetainedMap(context):new DownloadScreen(context);a.setIntent(intent);a.shirazBindComponent(intent.getComponent());return a;}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity a){detached[0]++;}},new ActivityConfigurationPolicy(metadata,"sample.Main",27),27);}});
  Intent map=new Intent("initial").setComponent(new ComponentName("sample",RetainedMap.class.getName())),download=new Intent().setComponent(new ComponentName("sample",DownloadScreen.class.getName()));
  stack.launch(map,2);android.app.Activity original=stack.current();stack.launch(download);LifecycleFixture.events.clear();
  Intent returned=new Intent(map).setAction("return");stack.launch(returned,2);returned.setAction("mutated");
  check(stack.size()==1&&stack.current()==original&&created[0]==2&&detached[0]==1,"singleTask clears above and reuses map");
  check(delivered.size()==1&&delivered.get(0).getAction().equals("return")&&original.getIntent().getAction().equals("initial"),"new intent snapshot without replacing getIntent");
  check(LifecycleFixture.events.toString().equals("[pause:false, stop, destroy, newIntent, restart, start, resume, postResume]"),"retained task launch callbacks");
  stack.launch(map,1);check(stack.current()==original&&created[0]==2&&delivered.size()==2,"singleTop reuses top");stack.launch(download);stack.launch(map,1);check(stack.size()==3&&stack.current()!=original&&created[0]==4,"singleTop does not search below top");
  try{stack.launch(map,3);throw new AssertionError("unsupported separate task accepted");}catch(UnsupportedOperationException expected){}check(stack.size()==3,"unsupported launch preserves stack");
  android.app.Activity newMap=stack.current();stack.launch(new Intent(download).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));check(stack.size()==3&&stack.current().getClass()==DownloadScreen.class&&created[0]==4&&detached[0]==1,"reorder retains intermediate activities");stack.current().finish();stack.finishTop();check(stack.current()==newMap&&stack.size()==2&&detached[0]==2,"Back after reorder restores previous top");stack.close();check(detached[0]==4,"retained task cleanup");
 }
 public static void main(String[] args)throws Exception {
  final Context context=new ContextWrapper(null);
  Properties metadata=new Properties();metadata.setProperty("activity.count","1");metadata.setProperty("activity.0.name","sample.Main");metadata.setProperty("activity.0.config_changes","512");
  for(int sdk:new int[]{27,35}){
   final Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
   LifecycleFixture.events.clear();final int[] detached={0},applied={0};
   ActivityLifecycle lifecycle=new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){LifecycleFixture.Sample value=new LifecycleFixture.Sample(context);value.setIntent(launch);return value;}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity a){detached[0]++;}},new ActivityConfigurationPolicy(metadata,"sample.Main",sdk),sdk);
   lifecycle.start();LifecycleFixture.Sample first=(LifecycleFixture.Sample)lifecycle.current();first.value=79;check(first.getIntent()==launch&&first.createdIntent==launch,"Launch intent before onCreate");final Intent replacement=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("geo:1,2")).putExtra("route",17);first.setIntent(replacement);check(first.getIntent()==replacement,"setIntent preserves reference");
   check(LifecycleFixture.events.toString().equals("[create, start, postCreate, resume, postResume]"),"launch order");
   LifecycleFixture.events.clear();check(!lifecycle.reconfigure(new Configuration(),512,new Runnable(){public void run(){applied[0]++;}}),"handled change recreated");check(lifecycle.current()==first&&LifecycleFixture.events.toString().equals("[configuration]"),"handled callback");
   LifecycleFixture.events.clear();check(lifecycle.reconfigure(new Configuration(),128,new Runnable(){public void run(){applied[0]++;LifecycleFixture.events.add("apply");}}),"unhandled change did not recreate");
   String middle=sdk<28?"save, stop":"stop, save";
   check(LifecycleFixture.events.toString().equals("[pause:true, "+middle+", retain, destroy, apply, create, start, restore, postCreate, resume, postResume]"),"recreation order");
   LifecycleFixture.Sample second=(LifecycleFixture.Sample)lifecycle.current();check(second!=first&&first.isDestroyed()&&!second.isChangingConfigurations()&&second.value==79&&second.restored&&detached[0]==1&&applied[0]==2,"replacement ownership/state");
   check(second.getIntent()==replacement&&second.createdIntent==replacement&&second.getIntent().getIntExtra("route",0)==17,"Replacement intent survives recreation before onCreate");second.setIntent(null);check(second.getIntent()==null,"Nullable intent setter");
   LifecycleFixture.events.clear();second.value=83;second.recreate();second.recreate();
   check(lifecycle.current()==second&&LifecycleFixture.events.isEmpty(),"recreate is deferred outside click callback");
   check(second.shirazTakeRecreationRequest()&&!second.shirazTakeRecreationRequest(),"repeated recreation requests coalesce");
   lifecycle.recreate();LifecycleFixture.Sample third=(LifecycleFixture.Sample)lifecycle.current();
   check(third!=second&&second.isDestroyed()&&third.value==83&&third.getIntent()==null&&detached[0]==2,"explicit recreation preserves saved state and nullable intent");
   check(LifecycleFixture.events.toString().equals("[pause:true, "+middle+", retain, destroy, create, start, restore, postCreate, resume, postResume]"),"explicit recreation lifecycle order");
   final Throwable[] wrongThread={null};Thread requester=new Thread(new Runnable(){public void run(){try{third.recreate();}catch(Throwable t){wrongThread[0]=t;}}});requester.start();requester.join();check(wrongThread[0] instanceof IllegalStateException,"cross-thread recreation rejected");
   second.recreate();check(!second.shirazTakeRecreationRequest(),"destroyed activity cannot request recreation");
   final Throwable[] cross={null};Thread worker=new Thread(new Runnable(){public void run(){try{lifecycle.current();}catch(Throwable t){cross[0]=t;}}});worker.start();worker.join();check(cross[0] instanceof IllegalStateException,"cross-thread lifecycle");
   LifecycleFixture.events.clear();lifecycle.close();lifecycle.close();check(LifecycleFixture.events.toString().equals("[pause:false, stop, destroy]")&&detached[0]==3,"final teardown");
  }
  buckets(context,metadata);covered(context,metadata);stack(context,metadata);retainedLaunches(context,metadata);
  ActivityLifecycle failing=new ActivityLifecycle(new ActivityLifecycle.Factory(){public android.app.Activity create(){return new LifecycleFixture.Sample(context);}},new ActivityLifecycle.Cleanup(){public void detached(android.app.Activity a){}},new ActivityConfigurationPolicy(metadata,"sample.Main",27),27);failing.start();LifecycleFixture.events.clear();LifecycleFixture.failPause=true;
  try{failing.close();throw new AssertionError("callback failure hidden");}catch(IllegalStateException expected){check(expected.getMessage().equals("pause failure"),"failure identity");}finally{LifecycleFixture.failPause=false;}
  check(LifecycleFixture.events.toString().equals("[pause:false, stop, destroy]"),"failure cleanup");failing.close();
  System.out.println("ACTIVITY_LIFECYCLE_OK");
 }
}
