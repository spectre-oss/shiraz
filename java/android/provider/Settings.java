// SPDX-License-Identifier: GPL-3.0-only
package android.provider;
import android.content.ContentResolver;
/** Read-only access to absent Android settings; callers retain their own defaults. */
public final class Settings {
 private Settings(){}
 public static class SettingNotFoundException extends android.util.AndroidException {public SettingNotFoundException(String name){super(name);}}
 private static String read(ContentResolver resolver,String table,String name){if(resolver==null)throw new NullPointerException("resolver");return resolver.shirazReadSetting(table,name);}
 private static int integer(String value,int fallback){try{return value==null?fallback:Integer.parseInt(value);}catch(NumberFormatException error){return fallback;}}
 private static int integer(String value,String name)throws SettingNotFoundException{try{return Integer.parseInt(value);}catch(NumberFormatException error){throw new SettingNotFoundException(name);}}
 private static float floating(String value,float fallback){try{return value==null?fallback:Float.parseFloat(value);}catch(NumberFormatException error){return fallback;}}
 private static float floating(String value,String name)throws SettingNotFoundException{try{return Float.parseFloat(value);}catch(NumberFormatException error){throw new SettingNotFoundException(name);}}
 private static long longer(String value,long fallback){try{return value==null?fallback:Long.parseLong(value);}catch(NumberFormatException error){return fallback;}}
 private static long longer(String value,String name)throws SettingNotFoundException{try{return Long.parseLong(value);}catch(NumberFormatException error){throw new SettingNotFoundException(name);}}
 public static final class Global {
  public static final String AIRPLANE_MODE_ON="airplane_mode_on",ANIMATOR_DURATION_SCALE="animator_duration_scale",TRANSITION_ANIMATION_SCALE="transition_animation_scale",WINDOW_ANIMATION_SCALE="window_animation_scale";
  public static final android.net.Uri CONTENT_URI=android.net.Uri.parse("content://settings/global");
  public static android.net.Uri getUriFor(String name){return android.net.Uri.withAppendedPath(CONTENT_URI,name);}
  public static String getString(ContentResolver resolver,String name){return read(resolver,"global",name);}
  public static int getInt(ContentResolver resolver,String name,int fallback){return integer(getString(resolver,name),fallback);}
  public static int getInt(ContentResolver resolver,String name)throws SettingNotFoundException{return integer(getString(resolver,name),name);}
  public static float getFloat(ContentResolver resolver,String name,float fallback){return floating(getString(resolver,name),fallback);}
  public static float getFloat(ContentResolver resolver,String name)throws SettingNotFoundException{return floating(getString(resolver,name),name);}
  public static long getLong(ContentResolver resolver,String name,long fallback){return longer(getString(resolver,name),fallback);}
  public static long getLong(ContentResolver resolver,String name)throws SettingNotFoundException{return longer(getString(resolver,name),name);}
  public static boolean putString(ContentResolver resolver,String name,String value){throw new UnsupportedOperationException("Android global settings writes are unavailable");}
  public static boolean putInt(ContentResolver resolver,String name,int value){return putString(resolver,name,Integer.toString(value));}
  public static boolean putFloat(ContentResolver resolver,String name,float value){return putString(resolver,name,Float.toString(value));}
  public static boolean putLong(ContentResolver resolver,String name,long value){return putString(resolver,name,Long.toString(value));}
 }
 public static final class Secure {
  public static final String ANDROID_ID="android_id";
  public static final String ENABLED_NOTIFICATION_LISTENERS="enabled_notification_listeners";
  public static final android.net.Uri CONTENT_URI=android.net.Uri.parse("content://settings/secure");
  public static android.net.Uri getUriFor(String name){return android.net.Uri.withAppendedPath(CONTENT_URI,name);}
  public static String getString(ContentResolver resolver,String name){return read(resolver,"secure",name);}
  public static int getInt(ContentResolver resolver,String name,int fallback){return integer(getString(resolver,name),fallback);}
  public static int getInt(ContentResolver resolver,String name)throws SettingNotFoundException{return integer(getString(resolver,name),name);}
 }
 public static final class System {
  public static final String SOUND_EFFECTS_ENABLED="sound_effects_enabled";
  public static final String AIRPLANE_MODE_ON=Global.AIRPLANE_MODE_ON;
  public static final String RINGTONE="ringtone",NOTIFICATION_SOUND="notification_sound",ALARM_ALERT="alarm_alert";
  public static final android.net.Uri CONTENT_URI=android.net.Uri.parse("content://settings/system");
  public static final android.net.Uri DEFAULT_RINGTONE_URI=getUriFor(RINGTONE);
  public static final android.net.Uri DEFAULT_NOTIFICATION_URI=getUriFor(NOTIFICATION_SOUND);
  public static final android.net.Uri DEFAULT_ALARM_ALERT_URI=getUriFor(ALARM_ALERT);
  public static android.net.Uri getUriFor(String name){return android.net.Uri.withAppendedPath(CONTENT_URI,name);}
  public static String getString(ContentResolver resolver,String name){if(resolver==null)throw new NullPointerException("resolver");if(SOUND_EFFECTS_ENABLED.equals(name))return "0";return AIRPLANE_MODE_ON.equals(name)?Global.getString(resolver,name):read(resolver,"system",name);}
  public static int getInt(ContentResolver resolver,String name,int fallback){return integer(getString(resolver,name),fallback);}
  public static int getInt(ContentResolver resolver,String name)throws SettingNotFoundException{return integer(getString(resolver,name),name);}
  public static float getFloat(ContentResolver resolver,String name,float fallback){return floating(getString(resolver,name),fallback);}
  public static float getFloat(ContentResolver resolver,String name)throws SettingNotFoundException{return floating(getString(resolver,name),name);}
  public static long getLong(ContentResolver resolver,String name,long fallback){return longer(getString(resolver,name),fallback);}
  public static long getLong(ContentResolver resolver,String name)throws SettingNotFoundException{return longer(getString(resolver,name),name);}
 }
}
