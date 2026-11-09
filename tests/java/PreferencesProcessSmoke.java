// SPDX-License-Identifier: GPL-3.0-only
import android.content.SharedPreferences;
import java.io.File;
import shiraz.runtime.HostPreferences;
/** Synthetic state only; a second VM invocation must read the durable writes. */
public final class PreferencesProcessSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  String root=System.getenv("SHIRAZ_PREF_TEST_ROOT"),phase=System.getenv("SHIRAZ_PREF_TEST_PHASE");if(root==null||phase==null)throw new IllegalArgumentException("Fixture configuration");
  android.os.Looper.prepareMainLooper();try{
  try(HostPreferences owner=new HostPreferences(new File(root,"preferences"))){SharedPreferences p=owner.get("synthetic-session",0);
   if("write".equals(phase)){check(p.edit().putBoolean("signed_in_fixture",true).putString("state","synthetic-only").commit(),"synchronous durable write");p.edit().putInt("generation",2).apply();}
   else if("read".equals(phase)){check(p.getBoolean("signed_in_fixture",false),"committed state survives VM restart");check("synthetic-only".equals(p.getString("state",null)),"string survives VM restart");check(p.getInt("generation",0)==2,"async apply drained before VM exit");}
   else throw new IllegalArgumentException("Fixture phase");
  }
  System.out.println("PREFERENCES_PROCESS_DURABLE_OK");
  }finally{android.os.Looper.shirazShutdownMain();}
 }
}
