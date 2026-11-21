// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.provider.Settings;
public final class SecureLongSettingSmoke {
 static String stored;
 public static void main(String[] args)throws Exception{
  ContentResolver resolver=new ContentResolver(new ContextWrapper(null)){
   public String shirazReadSetting(String table,String name){if(!table.equals("secure")||!name.equals("value"))throw new AssertionError("setting namespace");return stored;}
  };
  for(long value:new long[]{Long.MIN_VALUE,Long.MAX_VALUE,0}){
   stored=Long.toString(value);if(Settings.Secure.getLong(resolver,"value")!=value||Settings.Secure.getLong(resolver,"value",5)!=value)throw new AssertionError("long precision");
  }
  for(String invalid:new String[]{null,"","9223372036854775808","bad"}){
   stored=invalid;if(Settings.Secure.getLong(resolver,"value",77)!=77)throw new AssertionError("fallback");
   try{Settings.Secure.getLong(resolver,"value");throw new AssertionError("missing exception");}catch(Settings.SettingNotFoundException expected){}
  }
  System.out.println("SECURE_LONG_SETTING_OK");
 }
}
