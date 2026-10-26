// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.ActivityConfigurationPolicy;
import java.util.Properties;
public final class ActivityPolicySmoke {
 private static android.content.res.Configuration size(int width,int height){android.content.res.Configuration c=new android.content.res.Configuration();c.screenWidthDp=width;c.screenHeightDp=height;c.smallestScreenWidthDp=Math.min(width,height);return c;}
 private static void buckets(Properties metadata){
  ActivityConfigurationPolicy policy=new ActivityConfigurationPolicy(metadata,"sample.Main",27);
  android.content.res.Configuration qualifier=new android.content.res.Configuration();qualifier.screenWidthDp=600;qualifier.screenHeightDp=720;qualifier.smallestScreenWidthDp=360;
  policy.setSizeConfigurations(new android.content.res.Configuration[]{qualifier,qualifier});qualifier.screenWidthDp=999;
  if(policy.significantChanges(size(650,800),size(620,780),0xc00)!=0)throw new AssertionError("Same bucket relayout");
  if(policy.significantChanges(size(599,800),size(600,800),0x400)!=0x400||policy.significantChanges(size(600,800),size(599,800),0x400)!=0x400)throw new AssertionError("Inclusive width crossing both directions");
  if(policy.significantChanges(size(650,719),size(650,720),0x400)!=0x400||policy.significantChanges(size(650,720),size(650,719),0x400)!=0x400)throw new AssertionError("Height crossing");
  if(policy.significantChanges(size(359,800),size(360,800),0x800)!=0x800||policy.significantChanges(size(360,800),size(359,800),0x800)!=0x800)throw new AssertionError("Smallest-width crossing");
  if(policy.significantChanges(size(650,800),size(620,780),0xd80)!=0x180)throw new AssertionError("Non-size changes remain significant");
  policy.setSizeConfigurations(new android.content.res.Configuration[0]);if(policy.significantChanges(size(300,500),size(1000,1500),0xc00)!=0)throw new AssertionError("No app size qualifiers");
  policy.setSizeConfigurations(null);if(policy.significantChanges(size(650,800),size(620,780),0xc00)!=0xc00)throw new AssertionError("Unknown qualifiers remain conservative");
 }
 public static void main(String[] args) {
  Properties data=new Properties();data.setProperty("activity.count","1");data.setProperty("activity.0.name","sample.Main");data.setProperty("activity.0.config_changes","512");data.setProperty("activity.0.screen_orientation","-1");
  buckets(data);
  ActivityConfigurationPolicy policy=new ActivityConfigurationPolicy(data,"sample.Main",27);
  if(policy.requestedOrientation()!=-1||policy.requiresRecreation(512)||policy.requiresRecreation(0)||!policy.requiresRecreation(128|1024)||!policy.requiresRecreation(512|1024))throw new AssertionError("manifest ownership");
  policy=new ActivityConfigurationPolicy(data,"sample.Main",12);if(policy.requiresRecreation(1024|2048)||!policy.requiresRecreation(128))throw new AssertionError("legacy size behavior");
  data.setProperty("activity.0.config_changes","1664");data.setProperty("activity.0.screen_orientation","1");policy=new ActivityConfigurationPolicy(data,"sample.Main",27);
  if(policy.requiresRecreation(128|512|1024)||policy.requestedOrientation()!=1)throw new AssertionError("explicit handled orientation");
  try{new ActivityConfigurationPolicy(data,"absent",27);throw new AssertionError("unknown activity accepted");}catch(IllegalArgumentException expected){}
  data.setProperty("activity.0.screen_orientation","15");try{new ActivityConfigurationPolicy(data,"sample.Main",27);throw new AssertionError("invalid orientation");}catch(IllegalArgumentException expected){}
  android.app.Activity activity=new android.app.Activity();
  if(activity.getComponentName()!=null)throw new AssertionError("unbound component");
  try{activity.shirazBindComponent(null);throw new AssertionError("null binding");}catch(NullPointerException expected){}
  try{activity.shirazBindComponent(new android.content.ComponentName("sample","wrong.Class"));throw new AssertionError("wrong class binding");}catch(IllegalArgumentException expected){}
  activity.shirazBindComponent(new android.content.ComponentName("android.app", "android.app.Activity"));
  if(!activity.getLocalClassName().equals("Activity"))throw new AssertionError("local class name");
  try{activity.shirazBindComponent(new android.content.ComponentName("android.app", "android.app.Activity"));throw new AssertionError("rebind accepted");}catch(IllegalStateException expected){}
  android.app.Activity foreignClass=new android.app.Activity();foreignClass.shirazBindComponent(new android.content.ComponentName("android", "android.app.Activity"));
  if(!foreignClass.getLocalClassName().equals("app.Activity"))throw new AssertionError("nested local class name");
  android.app.Activity differentPackage=new android.app.Activity();differentPackage.shirazBindComponent(new android.content.ComponentName("sample", "android.app.Activity"));
  if(!differentPackage.getLocalClassName().equals("android.app.Activity"))throw new AssertionError("foreign namespace class name");
  final android.app.Activity fragmentHost=new android.app.Activity();fragmentHost.shirazPrepareFragments(null);
  final android.app.FragmentManager manager=fragmentHost.getFragmentManager();android.app.Fragment fragment=new android.app.Fragment();
  if(fragment.isAdded()||fragment.isDetached())throw new AssertionError("initial fragment state");
  manager.beginTransaction().add(fragment,"one").commitNow();
  if(manager.findFragmentByTag("one")!=fragment||!fragment.isAdded())throw new AssertionError("fragment ownership");
  try{manager.beginTransaction().add(new android.app.Fragment(),"unused").add(fragment,"duplicate").commitNow();throw new AssertionError("duplicate accepted");}catch(IllegalStateException expected){}
  if(manager.findFragmentByTag("unused")!=null||manager.getFragments().size()!=1)throw new AssertionError("partial invalid transaction");
  final Throwable[] cross={null};Thread worker=new Thread(new Runnable(){public void run(){try{manager.beginTransaction();}catch(Throwable error){cross[0]=error;}}});worker.start();try{worker.join();}catch(InterruptedException error){throw new AssertionError(error);}
  if(!(cross[0] instanceof IllegalStateException))throw new AssertionError("cross-thread transaction");
  fragmentHost.shirazDestroyFragments();if(!manager.isDestroyed()||fragment.getActivity()!=null||fragment.getFragmentManager()!=null)throw new AssertionError("fragment teardown");
  try{manager.beginTransaction();throw new AssertionError("destroyed manager accepted");}catch(IllegalStateException expected){}
  android.app.Activity failingHost=new android.app.Activity();failingHost.shirazPrepareFragments(null);final boolean[] detached={false};
  android.app.Fragment broken=new android.app.Fragment(){public void onDestroy(){throw new IllegalStateException("destroy failure");}public void onDetach(){detached[0]=true;}};
  failingHost.getFragmentManager().beginTransaction().add(broken,"broken").commitNow();
  try{failingHost.shirazDestroyFragments();throw new AssertionError("destroy failure hidden");}catch(IllegalStateException expected){if(!expected.getMessage().equals("destroy failure"))throw new AssertionError(expected);}
  if(!detached[0]||broken.getActivity()!=null||broken.getFragmentManager()!=null||!failingHost.getFragmentManager().getFragments().isEmpty())throw new AssertionError("failed fragment not released");
  System.out.println("ACTIVITY_POLICY_OK");
 }
}
