// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.content.res.*;
import android.util.TypedValue;
public final class ThemeCacheSmoke {
 public static void main(String[] args) {
  final Resources day=Resources.shirazOpen(args[0],96,160),night=Resources.shirazOpen(args[0],96,160);
  Context context=new ContextWrapper(null){private final Resources.Theme theme=day.newTheme();public Resources getResources(){return day;}public String getPackageName(){return "org.shiraz.fixture";}public Resources.Theme getTheme(){return theme;}public void setTheme(int id){theme.applyStyle(id,true);}};
  try {
   ThemeFixture.check(context);ColorResourceFixture.check(context);
   Configuration config=night.getConfiguration();config.uiMode=Configuration.UI_MODE_TYPE_NORMAL|Configuration.UI_MODE_NIGHT_YES;night.updateConfiguration(config,null);
   Resources.Theme a=day.newTheme(),b=night.newTheme();a.applyStyle(R.style.FixtureBase,true);b.applyStyle(R.style.FixtureBase,true);
   for(int i=0;i<6;i++){
    TypedValue value=new TypedValue();a.resolveAttribute(R.attr.themeAccent,value,true);if(value.data!=0xff112233)throw new AssertionError("Day manager theme isolation");
    b.resolveAttribute(R.attr.themeAccent,value,true);if(value.data!=0xffabcdef)throw new AssertionError("Night manager theme isolation");
    if(day.getColor(R.color.theme_color,a)!=0xff112233||night.getColor(R.color.theme_color,b)!=0xffabcdef)throw new AssertionError("Color theme snapshots isolated by manager");
   }
   TypedValue cached=new TypedValue();a.resolveAttribute(R.attr.themeAccent,cached,true);cached.data=123;cached.string="caller mutation";
   a.resolveAttribute(R.attr.themeAccent,cached,true);if(cached.data!=0xff112233)throw new AssertionError("Caller cannot mutate cached values");
   Configuration changed=day.getConfiguration();changed.uiMode=Configuration.UI_MODE_TYPE_NORMAL|Configuration.UI_MODE_NIGHT_YES;day.updateConfiguration(changed,null);
   a.resolveAttribute(R.attr.themeAccent,cached,true);if(cached.data!=0xffabcdef)throw new AssertionError("Existing theme invalidated after configuration update");
   a.applyStyle(R.style.FixtureOverlay,true);a.resolveAttribute(R.attr.themeAccent,cached,true);if(cached.data!=0xff445566)throw new AssertionError("Style update invalidates resolved cache");
   a.setTo(b);a.resolveAttribute(R.attr.themeAccent,cached,true);if(cached.data!=0xffabcdef)throw new AssertionError("setTo invalidates resolved cache");
  } finally {day.shirazClose();night.shirazClose();}
  System.out.println("THEME_SNAPSHOTS_CONFIGURATION_AND_ISOLATION_OK");
 }
}
