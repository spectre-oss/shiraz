// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
/** Loaded activity configuration and component metadata; no parcel transport. */
public class ActivityInfo extends ComponentInfo implements Parcelable {
 public static final int CONFIG_MCC=1,CONFIG_MNC=2,CONFIG_LOCALE=4,CONFIG_TOUCHSCREEN=8,
  CONFIG_KEYBOARD=16,CONFIG_KEYBOARD_HIDDEN=32,CONFIG_NAVIGATION=64,CONFIG_ORIENTATION=128,
  CONFIG_SCREEN_LAYOUT=256,CONFIG_UI_MODE=512,CONFIG_SCREEN_SIZE=1024,
  CONFIG_SMALLEST_SCREEN_SIZE=2048,CONFIG_DENSITY=4096,CONFIG_LAYOUT_DIRECTION=8192,
  CONFIG_COLOR_MODE=16384,CONFIG_FONT_SCALE=0x40000000;
 public static final int LAUNCH_MULTIPLE=0,LAUNCH_SINGLE_TOP=1,LAUNCH_SINGLE_TASK=2,LAUNCH_SINGLE_INSTANCE=3,LAUNCH_SINGLE_INSTANCE_PER_TASK=4;
 public static final int SCREEN_ORIENTATION_UNSPECIFIED=-1,SCREEN_ORIENTATION_LANDSCAPE=0,SCREEN_ORIENTATION_PORTRAIT=1,
  SCREEN_ORIENTATION_USER=2,SCREEN_ORIENTATION_BEHIND=3,SCREEN_ORIENTATION_SENSOR=4,SCREEN_ORIENTATION_NOSENSOR=5,
  SCREEN_ORIENTATION_SENSOR_LANDSCAPE=6,SCREEN_ORIENTATION_SENSOR_PORTRAIT=7,SCREEN_ORIENTATION_REVERSE_LANDSCAPE=8,
  SCREEN_ORIENTATION_REVERSE_PORTRAIT=9,SCREEN_ORIENTATION_FULL_SENSOR=10,SCREEN_ORIENTATION_USER_LANDSCAPE=11,
  SCREEN_ORIENTATION_USER_PORTRAIT=12,SCREEN_ORIENTATION_FULL_USER=13,SCREEN_ORIENTATION_LOCKED=14;
 public int theme;
 public int getThemeResource(){return theme!=0?theme:applicationInfo==null?0:applicationInfo.theme;}
 public int launchMode,configChanges,screenOrientation=-1;
 public String permission,parentActivityName;
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("ActivityInfo parcel transport is not implemented");}
}
