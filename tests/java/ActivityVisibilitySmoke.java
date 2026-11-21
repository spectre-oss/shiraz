// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import shiraz.runtime.*;
public final class ActivityVisibilitySmoke {
 static int created,destroyed,delivered,resumed,paused,started,stopped;
 static Resources resources;
 static final Application application=new Application();
 public static class Screen extends Activity {
  public Screen(){attachBaseContext(new ContextWrapper(null){public String getPackageName(){return "fixture.task";}public Context getApplicationContext(){return application;}public Resources getResources(){return resources;}});}
  protected void onCreate(android.os.Bundle saved){created++;}
  protected void onDestroy(){destroyed++;}
  protected void onResume(){resumed++;}
  protected void onPause(){paused++;}
  protected void onStart(){started++;}
  protected void onStop(){stopped++;}
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
   stack.launch(launch);Activity original=stack.current();check(resumed==1);
   stack.setVisible(false);stack.setVisible(false);check(paused==1&&stopped==1);
   stack.launch(launch);check(resumed==1&&started==2&&stopped==2);
   stack.currentLifecycle().recreate();check(resumed==1&&created==3&&stopped==3);
   stack.current().finish();check(stack.finishTop()&&stack.current()==original&&resumed==1);
   stack.setVisible(true);stack.setVisible(true);check(resumed==2&&started==4&&created==3);
   stack.setVisible(false);stack.launch(launch,2);check(resumed==2&&delivered==1);
   stack.setVisible(true);check(resumed==3&&started==5);
   stack.setVisible(false);int oldPaused=paused,oldStopped=stopped;stack.close();
   check(paused==oldPaused&&stopped==oldStopped&&destroyed==3);
  }finally{stack.close();resources.shirazClose();}
  System.out.println("ACTIVITY_VISIBILITY_OK");
 }
}
