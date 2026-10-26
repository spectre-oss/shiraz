// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.res.*;
import android.view.LayoutInflater;
import android.util.TypedValue;
import java.util.Locale;
public final class ConfigurationContextFixture {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void check(Context context) {
  Resources root=context.getResources();Configuration saved=root.getConfiguration();
  check(new Configuration().fontScale==0,"new configuration is an undefined delta");
  Configuration french=new Configuration();french.fontScale=0;french.setLocale(Locale.FRENCH);
  Context localized=context.createConfigurationContext(french);french.setLocale(Locale.GERMAN);
  check(localized!=context&&localized.getResources()!=root,"independent context/resources");
  check(localized.getString(R.string.localized).equals("Langue française")&&root.getConfiguration().equals(saved),"locale isolation and copied override");
  check(localized.getApplicationContext()==context.getApplicationContext()&&localized.getFilesDir().equals(context.getFilesDir()),"shared application/storage");
  check(LayoutInflater.from(localized).getContext()==localized,"scoped inflater");
  Resources.Theme original=root.newTheme();original.applyStyle(R.style.FixtureBase,true);
  Resources.Theme copied=localized.getResources().newTheme();copied.setTo(original);TypedValue value=new TypedValue();
  check(copied.resolveAttribute(R.attr.themeLabel,value,true)&&"Langue française".contentEquals(value.string),"copied theme uses scoped resources");
  Configuration night=new Configuration();night.fontScale=0;night.uiMode=32;
  Context nested=localized.createConfigurationContext(night);
  try {
   Configuration landscape=new Configuration();landscape.fontScale=1.25f;landscape.orientation=2;landscape.screenWidthDp=160;landscape.screenHeightDp=96;landscape.setLocale(Locale.ENGLISH);
   root.updateConfiguration(landscape,null);
   check(localized.getResources().getConfiguration().fontScale==1.25f&&localized.getResources().getConfiguration().orientation==2&&localized.getString(R.string.localized).equals("Langue française"),"base changes inherited except overrides");
   check(nested.getResources().getConfiguration().orientation==2&&(nested.getResources().getConfiguration().uiMode&48)==32&&nested.getString(R.string.localized).equals("Langue française"),"nested inherited configuration");
   check(nested.getString(R.string.orientation_probe).equals("landscape-selected")&&nested.getString(R.string.night_probe).equals("night-selected"),"real qualifier selection");
  } finally {root.updateConfiguration(saved,null);}
  Configuration probe=new Configuration();probe.uiMode=-1;
  Context reference=context.createConfigurationContext(probe);
  check(reference.getResources().getConfiguration().uiMode==63&&root.getConfiguration().equals(saved),"UI mode override masks unknown bits");
  try{context.createConfigurationContext(null);throw new AssertionError("null override accepted");}catch(NullPointerException expected){}
  Configuration invalid=new Configuration();invalid.densityDpi=-1;
  try{context.createConfigurationContext(invalid);throw new AssertionError("invalid override accepted");}catch(IllegalArgumentException expected){}
  check(root.getConfiguration().equals(saved),"base intact after invalid fork");
  System.out.println("SHIRAZ_FIXTURE_CONFIGURATION_CONTEXT_OK");
 }
}
