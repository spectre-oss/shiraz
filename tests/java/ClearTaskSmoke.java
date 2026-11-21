// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import shiraz.runtime.*;
public final class ClearTaskSmoke {
 static int created,destroyed,delivered;
 static Resources resources;
 static final Application application=new Application();
 public static class Screen extends Activity {
  public Screen(){attachBaseContext(new ContextWrapper(null){public String getPackageName(){return "fixture.task";}public Context getApplicationContext(){return application;}public Resources getResources(){return resources;}});}
  protected void onCreate(android.os.Bundle saved){created++;}
  protected void onDestroy(){destroyed++;}
  protected void onNewIntent(Intent intent){delivered++;}
 }
 static void check(boolean ok){if(!ok)throw new AssertionError("created="+created+" destroyed="+destroyed+" delivered="+delivered);}
 public static void main(String[] args){
  resources=Resources.shirazOpen(new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath(),360,640);
  final java.util.Properties metadata=new java.util.Properties();metadata.setProperty("activity.count","1");metadata.setProperty("activity.0.name",Screen.class.getName());
  ActivityStack stack=new ActivityStack(new ActivityStack.Factory(){public ActivityLifecycle create(final Intent intent){
   return new ActivityLifecycle(new ActivityLifecycle.Factory(){public Activity create(){Screen screen=new Screen();screen.setIntent(intent);screen.shirazBindComponent(intent.getComponent());return screen;}},new ActivityLifecycle.Cleanup(){public void detached(Activity activity){}},new ActivityConfigurationPolicy(metadata,Screen.class.getName(),27),27);
  }});
  Intent launch=new Intent().setComponent(new ComponentName("fixture.task",Screen.class.getName()));
  try{
   stack.launch(launch,2);Activity original=stack.current();
   stack.launch(new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK),2);
   check(stack.current()==original&&created==1&&delivered==1&&destroyed==0);
   stack.launch(launch);check(stack.size()==2&&created==2);
   stack.launch(new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK),2);
   check(stack.size()==1&&stack.current()!=original&&created==3&&destroyed==2&&delivered==1&&stack.current().isTaskRoot());
   stack.current().finish();check(stack.finishTop()&&stack.size()==0&&destroyed==3);
  }finally{stack.close();resources.shirazClose();}
  System.out.println("CLEAR_TASK_OK");
 }
}
