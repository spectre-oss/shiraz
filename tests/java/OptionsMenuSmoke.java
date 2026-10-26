// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.os.Looper;
import android.view.*;
import org.shiraz.fixture.OptionsMenuFixture.Sample;
public final class OptionsMenuSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static final class InflationActivity extends android.app.Activity {int clicks;InflationActivity(Context c){attachBaseContext(c);}public boolean selectInflated(MenuItem item){clicks++;return true;}}
 static void inflation(String apk){final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(apk,100,100);final android.content.res.Resources.Theme theme=resources.newTheme();Context context=new ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return theme;}public boolean isRestricted(){return false;}};
  try{InflationActivity a=new InflationActivity(context);check(a.getMenuInflater()==a.getMenuInflater(),"cached activity inflater");shiraz.runtime.HostOptionsMenu menu=new shiraz.runtime.HostOptionsMenu(a,null);int resource=resources.getIdentifier("inflate_test","menu","org.shiraz.fixture");a.getMenuInflater().inflate(resource,menu);int first=resources.getIdentifier("inflate_first","id","org.shiraz.fixture"),second=resources.getIdentifier("inflate_second","id","org.shiraz.fixture"),nested=resources.getIdentifier("inflate_nested","id","org.shiraz.fixture");check(menu.size()==3&&"Parent".contentEquals(menu.getItem(0).getTitle())&&menu.getItem(1).getItemId()==second,"menu nesting and ordering");check(menu.findItem(first).isChecked()&&!menu.findItem(second).isChecked(),"inflated radio state");menu.findItem(second).setChecked(true);check(!menu.findItem(first).isChecked()&&menu.findItem(second).isChecked(),"exclusive selection");check(shiraz.runtime.HostContextMenu.shirazShowAsAction(menu.findItem(second))==5,"action placement metadata");try{menu.findItem(second).setShowAsAction(3);throw new AssertionError("conflicting placement accepted");}catch(IllegalArgumentException expected){}check(!menu.findItem(nested).isEnabled(),"nested enabled attribute");check(menu.performIdentifierAction(second,0)&&a.clicks==1,"inflated click handler");try{a.getMenuInflater().inflate(resources.getIdentifier("inflate_unsupported","menu","org.shiraz.fixture"),menu);throw new AssertionError("unsupported inflation accepted");}catch(InflateException expected){check(expected.getCause() instanceof UnsupportedOperationException,"explicit unsupported attribute");}}finally{resources.shirazClose();}
 }
 public static void main(String[] args){inflation(args[0]);
  Looper.prepareMainLooper();Context context=new ContextWrapper(null);Sample activity=new Sample(context);Window window=activity.getWindow();
  activity.invalidateOptionsMenu();activity.invalidateOptionsMenu();check(activity.creates==0,"invalidation must not invoke inline callbacks");
  Looper.myLooper().shirazDispatchDue(16);check(activity.creates==0,"closed panel lazily creates menu");
  Menu first=window.shirazPrepareOptionsPanel();check(first.size()==1&&activity.creates==1&&activity.prepares==1,"create/prepare callbacks");
  check(window.shirazPrepareOptionsPanel()==first&&activity.prepares==1,"prepared panel cache");
  check(first.performIdentifierAction(42,Menu.FLAG_PERFORM_NO_CLOSE)&&activity.selections==1,"options item routing");
  activity.invalidateOptionsMenu();activity.invalidateOptionsMenu();Looper.myLooper().shirazDispatchDue(16);check(!first.performIdentifierAction(42,0),"retired menu actions");
  Menu second=window.shirazPrepareOptionsPanel();check(second!=first&&activity.creates==2,"invalidated menu rebuilt");
  activity.closeOptionsMenu();activity.allowPrepare=false;check(window.shirazPrepareOptionsPanel()==null,"prepare veto");activity.allowPrepare=true;check(window.shirazPrepareOptionsPanel()==second,"prepare retry preserves created menu");
  activity.allowCreate=false;activity.invalidateOptionsMenu();check(window.shirazPrepareOptionsPanel()==null,"create veto");activity.allowCreate=true;
  activity.again=true;Menu reentrant=window.shirazPrepareOptionsPanel();Looper.myLooper().shirazDispatchDue(16);check(window.shirazPrepareOptionsPanel()!=reentrant,"reentrant invalidation deferred");
  activity.fail=true;activity.invalidateOptionsMenu();try{window.shirazPrepareOptionsPanel();throw new AssertionError("create failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("menu create failure"),"original failure");}
  activity.fail=false;check(window.shirazPrepareOptionsPanel().size()==1,"failed create rollback");
  try{activity.openOptionsMenu();throw new AssertionError("unsupported popup silently opened");}catch(UnsupportedOperationException expected){}
  activity.invalidateOptionsMenu();window.shirazDestroy();Looper.myLooper().shirazDispatchDue(16);activity.invalidateOptionsMenu();try{window.shirazPrepareOptionsPanel();throw new AssertionError("destroyed window prepared menu");}catch(IllegalStateException expected){}
  System.out.println("OPTIONS_MENU_OK");
 }
}
