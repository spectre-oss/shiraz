// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import java.util.ArrayList;
public final class LifecycleFixture {
 public static final ArrayList<String> events=new ArrayList<String>();
 public static final Object token=new Object();
 public static boolean failPause;
 public static class Sample extends Activity {
  public android.content.Intent createdIntent;
  public int value=42;public boolean restored;
  public Sample(Context context){attachBaseContext(context);}
  protected void onCreate(Bundle state){super.onCreate(state);createdIntent=getIntent();events.add("create");if(state!=null){value=state.getInt("value");if(getLastNonConfigurationInstance()!=token)throw new AssertionError("retained instance");}}
  protected void onRestart(){super.onRestart();events.add("restart");}
  protected void onStart(){super.onStart();events.add("start");}
  protected void onPostCreate(Bundle state){super.onPostCreate(state);events.add("postCreate");}
  protected void onResume(){super.onResume();events.add("resume");}
  protected void onPostResume(){super.onPostResume();events.add("postResume");}
  protected void onPause(){super.onPause();events.add("pause:"+isChangingConfigurations());if(failPause)throw new IllegalStateException("pause failure");}
  protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);events.add("save");state.putInt("value",value);}
  protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);events.add("restore");restored=true;if(value!=state.getInt("value"))throw new AssertionError("saved value");}
  protected void onStop(){super.onStop();events.add("stop");}
  protected void onDestroy(){super.onDestroy();events.add("destroy");if(!isDestroyed())throw new AssertionError("destroy flag");}
  public Object onRetainNonConfigurationInstance(){events.add("retain");return token;}
  public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);events.add("configuration");}
 }
}
