// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
public final class ResourceIdentifierSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  String apk=new java.io.File("build/apk-fixture/fixture.apk").getAbsolutePath();
  Resources resources=Resources.shirazOpen(apk,96,160),other=Resources.shirazOpen(apk,96,160);
  try{
   int id=resources.getIdentifier("app_name","string","org.shiraz.fixture");check(id!=0,"fixture ID");
   for(int i=0;i<3;i++){
    check(resources.getIdentifier("app_name","string","org.shiraz.fixture")==id,"repeated name");
    check(resources.getIdentifier("org.shiraz.fixture:string/app_name",null,null)==id,"qualified name");
    check(resources.getIdentifier("org.shiraz.fixture:string/app_name","wrong","wrong")==id,"qualified defaults");
    check(resources.getIdentifier("app_name","drawable","org.shiraz.fixture")==0,"type isolation");
    check(resources.getIdentifier("app_name","string","missing")==0,"package isolation");
    check(resources.getIdentifier("app_name\0suffix","string","org.shiraz.fixture")==0,"embedded NUL");
    for(String numeric:new String[]{"+123","-123","١٢٣","2147483647","-2147483648"})check(resources.getIdentifier(numeric,null,null)==Integer.parseInt(numeric),"numeric semantics");
   }
   for(int i=0;i<4200;i++){check(resources.getIdentifier("missing"+i,"string","org.shiraz.fixture")==0,"negative lookup");check(resources.getIdentifier("org.shiraz.fixture:string/app_name","string","fallback"+i)==id,"qualified lookup");}
   char[] large=new char[1100];java.util.Arrays.fill(large,'x');check(resources.getIdentifier("org.shiraz.fixture:string/app_name",null,new String(large))==id,"large default");
   Configuration changed=new Configuration(resources.getConfiguration());changed.setLocale(java.util.Locale.FRENCH);resources.updateConfiguration(changed,null);
   check(resources.getIdentifier("app_name","string","org.shiraz.fixture")==id,"ID stable after locale");
   check("Langue française".equals(resources.getString(resources.getIdentifier("localized","string","org.shiraz.fixture"))),"localized values still update");
   resources.shirazClose();
   try{resources.getIdentifier("app_name","string","org.shiraz.fixture");throw new AssertionError("cached ID bypassed close");}catch(IllegalStateException expected){}
   check(other.getIdentifier("app_name","string","org.shiraz.fixture")==id,"owner isolation");
   System.out.println("RESOURCE_IDENTIFIER_OK");
  }finally{resources.shirazClose();other.shirazClose();}
 }
}
