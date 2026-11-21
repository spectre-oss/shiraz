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
 public String permission,parentActivityName,targetActivity;
 public WindowLayout windowLayout;
 public static final class WindowLayout {
  public final int width;
  public final float widthFraction;
  public final int height;
  public final float heightFraction;
  public final int gravity;
  public final int minWidth;
  public final int minHeight;
  public WindowLayout(int width,float widthFraction,int height,float heightFraction,int gravity,int minWidth,int minHeight){
   this.width=width;this.widthFraction=widthFraction;this.height=height;this.heightFraction=heightFraction;
   this.gravity=gravity;this.minWidth=minWidth;this.minHeight=minHeight;
  }
 }
 public int describeContents(){return 0;}
 public ActivityInfo(){}
 public ActivityInfo(ActivityInfo orig){
  if(orig!=null){
   name=orig.name;packageName=orig.packageName;labelRes=orig.labelRes;icon=orig.icon;logo=orig.logo;banner=orig.banner;
   metaData=orig.metaData;nonLocalizedLabel=orig.nonLocalizedLabel;applicationInfo=orig.applicationInfo;
   processName=orig.processName;descriptionRes=orig.descriptionRes;enabled=orig.enabled;exported=orig.exported;
   directBootAware=orig.directBootAware;theme=orig.theme;launchMode=orig.launchMode;configChanges=orig.configChanges;
   screenOrientation=orig.screenOrientation;permission=orig.permission;parentActivityName=orig.parentActivityName;
   targetActivity=orig.targetActivity;windowLayout=orig.windowLayout;
  }
 }
 ActivityInfo(Parcel in){
  name=in.readString();packageName=in.readString();labelRes=in.readInt();icon=in.readInt();logo=in.readInt();banner=in.readInt();
  nonLocalizedLabel=android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
  metaData=in.readBundle();processName=in.readString();descriptionRes=in.readInt();
  enabled=in.readInt()!=0;exported=in.readInt()!=0;directBootAware=in.readInt()!=0;
  theme=in.readInt();launchMode=in.readInt();configChanges=in.readInt();screenOrientation=in.readInt();
  permission=in.readString();parentActivityName=in.readString();targetActivity=in.readString();
 }
 public void writeToParcel(Parcel out,int flags){
  out.writeString(name);out.writeString(packageName);out.writeInt(labelRes);out.writeInt(icon);out.writeInt(logo);out.writeInt(banner);
  android.text.TextUtils.writeToParcel(nonLocalizedLabel,out,flags);
  out.writeBundle(metaData);out.writeString(processName);out.writeInt(descriptionRes);
  out.writeInt(enabled?1:0);out.writeInt(exported?1:0);out.writeInt(directBootAware?1:0);
  out.writeInt(theme);out.writeInt(launchMode);out.writeInt(configChanges);out.writeInt(screenOrientation);
  out.writeString(permission);out.writeString(parentActivityName);out.writeString(targetActivity);
 }
 public static final Parcelable.Creator<ActivityInfo> CREATOR=new Parcelable.Creator<ActivityInfo>(){
  public ActivityInfo createFromParcel(Parcel in){return new ActivityInfo(in);}
  public ActivityInfo[] newArray(int size){return new ActivityInfo[size];}
 };
}
