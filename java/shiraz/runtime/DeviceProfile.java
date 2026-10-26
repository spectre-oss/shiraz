// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Optional, run-scoped values returned by fabricated Android device APIs. */
public final class DeviceProfile {
 private static final Properties values=new Properties();
 static {
  String path=System.getProperty("shiraz.deviceProfile");
  if(path!=null){
   try(FileInputStream input=new FileInputStream(path)){values.load(input);}
   catch(IOException error){throw new ExceptionInInitializerError(error);}
  }
 }
 private DeviceProfile(){}
 public static String get(String key,String fallback){return values.getProperty(key,fallback);}
 public static int integer(String key,int fallback){
  String text=values.getProperty(key);
  if(text==null)return fallback;
  try{return Integer.parseInt(text);}catch(NumberFormatException error){throw new IllegalArgumentException("Invalid device profile integer: "+key,error);}
 }
 public static long longValue(String key,long fallback){
  String text=values.getProperty(key);
  if(text==null)return fallback;
  try{return Long.parseLong(text);}catch(NumberFormatException error){throw new IllegalArgumentException("Invalid device profile long: "+key,error);}
 }
 public static boolean selected(){return System.getProperty("shiraz.deviceProfile")!=null;}
}
