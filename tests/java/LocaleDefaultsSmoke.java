// SPDX-License-Identifier: GPL-3.0-only
import android.os.LocaleList;
import java.util.Locale;
public final class LocaleDefaultsSmoke {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  Locale original=Locale.getDefault();
  try{
   LocaleList chosen=LocaleList.forLanguageTags("fa-IR,en-US,de-DE");
   LocaleList.setDefault(chosen);
   check(Locale.getDefault().equals(chosen.get(0)),"Java default follows primary locale");
   check(LocaleList.getDefault().equals(chosen),"All preferences retained");
   LocaleList.setDefault(chosen,1);
   check(Locale.getDefault().equals(Locale.US)&&LocaleList.getDefault().equals(chosen),"Secondary selection preserves preferences");
   check(LocaleList.getAdjustedDefault().toLanguageTags().equals("en-US,fa-IR,de-DE"),"Adjusted order and deduplication");
   Locale.setDefault(Locale.FRANCE);
   check(LocaleList.getDefault().toLanguageTags().equals("fr-FR,fa-IR,en-US,de-DE"),"External default retains explicit preferences");
   Locale.setDefault(chosen.get(0));
   check(LocaleList.getAdjustedDefault().equals(chosen),"External primary selection restores order");
   try{LocaleList.setDefault(null);throw new AssertionError("null accepted");}catch(NullPointerException expected){}
   try{LocaleList.setDefault(LocaleList.getEmptyLocaleList());throw new AssertionError("empty accepted");}catch(IllegalArgumentException expected){}
   try{LocaleList.setDefault(chosen,-1);throw new AssertionError("bad index accepted");}catch(NullPointerException expected){}
   check(LocaleList.getDefault().equals(chosen),"Invalid input preserves state");
   System.out.println("LOCALE_DEFAULTS_OK");
  }finally{LocaleList.setDefault(new LocaleList(original));}
 }
}
