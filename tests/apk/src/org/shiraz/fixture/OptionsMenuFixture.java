// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Context;
import android.view.*;
public final class OptionsMenuFixture {
 public static class Sample extends Activity {
  public int creates,prepares,selections;public boolean allowCreate=true,allowPrepare=true,again,fail;
  public Sample(Context context){attachBaseContext(context);}
  public boolean onCreateOptionsMenu(Menu menu){creates++;menu.add(0,42,0,"Map");if(again){again=false;invalidateOptionsMenu();}if(fail)throw new IllegalStateException("menu create failure");return allowCreate;}
  public boolean onPrepareOptionsMenu(Menu menu){prepares++;return allowPrepare;}
  public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==42){selections++;return true;}return false;}
 }
}
