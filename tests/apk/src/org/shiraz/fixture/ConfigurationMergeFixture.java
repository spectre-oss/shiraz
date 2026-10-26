// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;
public final class ConfigurationMergeFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void check(Resources resources) {
  Configuration base=new Configuration();base.setLocale(Locale.ENGLISH);base.fontScale=1.5f;base.screenLayout|=2|16;base.uiMode=17;base.orientation=1;base.screenWidthDp=96;base.screenHeightDp=160;base.smallestScreenWidthDp=96;base.densityDpi=160;
  Configuration delta=new Configuration();delta.fontScale=0;delta.uiMode=32;delta.screenLayout=32;
  int flags=base.updateFrom(delta);
  check(base.uiMode==33&&base.screenLayout==(64|2|32)&&base.fontScale==1.5f,"partial masks preserve undefined fields");
  check(flags==(0x200|0x100)&&base.screenWidthDp==96,"change bits and dimensions");
  Configuration empty=new Configuration();empty.fontScale=0;check(base.updateFrom(empty)==0,"empty delta");
  delta=new Configuration();delta.fontScale=0;delta.setLocale(new Locale("ar"));delta.screenLayout=64;
  check(base.updateFrom(delta)==(4|0x2000)&&base.getLayoutDirection()==0&&base.locale.getLanguage().equals("ar"),"explicit layout direction wins locale inference");
  delta=new Configuration();delta.fontScale=2;delta.orientation=2;delta.screenWidthDp=160;delta.screenHeightDp=96;delta.smallestScreenWidthDp=90;delta.densityDpi=240;
  check(base.updateFrom(delta)==(0x40000000|0x80|0x400|0x800|0x1000),"size/density/font change bits");
  Configuration input=new Configuration();input.keyboard=Configuration.KEYBOARD_QWERTY;input.keyboardHidden=Configuration.KEYBOARDHIDDEN_NO;input.hardKeyboardHidden=Configuration.HARDKEYBOARDHIDDEN_NO;
  check(base.updateFrom(input)==0x30,"Keyboard type and visibility change bits");
  Configuration copy=new Configuration(base);check(copy.equals(base)&&copy.hashCode()==base.hashCode()&&copy.keyboard==2&&copy.keyboardHidden==1&&copy.hardKeyboardHidden==1,"Input fields copied and compared");
  check(copy.updateFrom(new Configuration())==0&&copy.keyboard==2,"Undefined input fields preserve values");
  input=new Configuration();input.hardKeyboardHidden=Configuration.HARDKEYBOARDHIDDEN_YES;check(copy.updateFrom(input)==0x20&&!copy.equals(base)&&copy.keyboard==2,"Hidden keyboard update is independent");
  Configuration saved=resources.getConfiguration();
  try {
   Configuration night=new Configuration();night.fontScale=0;night.uiMode=32;resources.updateConfiguration(night,null);
   Configuration result=resources.getConfiguration();
   check((result.uiMode&15)==(saved.uiMode&15)&&(result.uiMode&48)==32,"resource night merge");
   check(result.screenWidthDp==saved.screenWidthDp&&result.fontScale==saved.fontScale,"resource undefined preservation");
   Configuration invalid=new Configuration();invalid.fontScale=-1;
   try{resources.updateConfiguration(invalid,null);throw new AssertionError("invalid scale accepted");}catch(IllegalArgumentException expected){}
   check(resources.getConfiguration().equals(result),"invalid override rollback");
  } finally {resources.updateConfiguration(saved,null);}
  System.out.println("SHIRAZ_FIXTURE_CONFIGURATION_MERGE_OK");
 }
}
