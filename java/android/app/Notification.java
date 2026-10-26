// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.Context;
import android.os.*;
import android.net.Uri;
import android.media.AudioAttributes;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.widget.RemoteViews;
/** Notification data consumed by the session-owned desktop notification manager. */
public class Notification implements Parcelable, Cloneable {
 public static final AudioAttributes AUDIO_ATTRIBUTES_DEFAULT=new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
 public static final int FLAG_SHOW_LIGHTS=1,FLAG_ONGOING_EVENT=2,FLAG_INSISTENT=4,FLAG_ONLY_ALERT_ONCE=8,FLAG_AUTO_CANCEL=16,FLAG_NO_CLEAR=32,FLAG_FOREGROUND_SERVICE=64,FLAG_LOCAL_ONLY=256,FLAG_GROUP_SUMMARY=512;
 public static final int DEFAULT_ALL=-1,DEFAULT_SOUND=1,DEFAULT_VIBRATE=2,DEFAULT_LIGHTS=4;
 public static final String EXTRA_TITLE="android.title",EXTRA_TEXT="android.text",EXTRA_PROGRESS="android.progress",EXTRA_PROGRESS_MAX="android.progressMax",EXTRA_PROGRESS_INDETERMINATE="android.progressIndeterminate";
 public long when=System.currentTimeMillis();public int icon,iconLevel,flags,defaults,ledARGB,ledOnMS,ledOffMS,audioStreamType=-1,number,priority,color,visibility;
 public CharSequence tickerText;public PendingIntent contentIntent,deleteIntent,fullScreenIntent;public RemoteViews contentView,bigContentView,headsUpContentView;
 public Uri sound;public long[] vibrate;public AudioAttributes audioAttributes=AUDIO_ATTRIBUTES_DEFAULT;public Bundle extras=new Bundle();
 public Action[] actions;public Bitmap largeIcon;
 private String channelId,group,sortKey,category;private long timeout;private Icon smallIcon,largeIconObj;
 public Notification(){}public Notification(int icon,CharSequence ticker,long when){this.icon=icon;this.tickerText=ticker;this.when=when;}
 public String getChannelId(){return channelId;}public String getGroup(){return group;}public String getSortKey(){return sortKey;}public String getCategory(){return category;}public long getTimeoutAfter(){return timeout;}public Icon getSmallIcon(){return smallIcon;}public Icon getLargeIcon(){return largeIconObj;}
 public Notification clone(){try{Notification n=(Notification)super.clone();n.extras=extras==null?new Bundle():new Bundle(extras);n.vibrate=vibrate==null?null:vibrate.clone();n.actions=actions==null?null:actions.clone();return n;}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Cross-process notification transport is not implemented");}
 public static class Action implements Parcelable {
  public int icon;public CharSequence title;public PendingIntent actionIntent;public Bundle extras=new Bundle();public boolean allowGeneratedReplies=true;private Icon actionIcon;
  public Action(int icon,CharSequence title,PendingIntent intent){this.icon=icon;this.title=title;this.actionIntent=intent;if(icon!=0)actionIcon=Icon.createWithResource("",icon);}
  public Action(Icon icon,CharSequence title,PendingIntent intent){this.actionIcon=icon;this.icon=icon==null?0:icon.getType()==Icon.TYPE_RESOURCE?icon.getResId():0;this.title=title;this.actionIntent=intent;}
  public Icon getIcon(){return actionIcon;}public CharSequence getTitle(){return title;}public PendingIntent getActionIntent(){return actionIntent;}public Bundle getExtras(){return extras;}public boolean getAllowGeneratedReplies(){return allowGeneratedReplies;}
  public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Notification action transport");}
  public static final class Builder {
   private final Action action;
   public Builder(int icon,CharSequence title,PendingIntent intent){action=new Action(icon,title,intent);}
   public Builder(Icon icon,CharSequence title,PendingIntent intent){action=new Action(icon,title,intent);}
   public Builder(Action a){if(a==null)throw new NullPointerException("action");action=new Action(a.actionIcon,a.title,a.actionIntent);action.icon=a.icon;action.extras=new Bundle(a.extras);action.allowGeneratedReplies=a.allowGeneratedReplies;}
   public Builder addExtras(Bundle extras){if(extras!=null)action.extras.putAll(extras);return this;}
   public Builder setAllowGeneratedReplies(boolean allow){action.allowGeneratedReplies=allow;return this;}
   public Bundle getExtras(){return action.extras;}
   public Action build(){return action;}
  }
 }
 public static class Builder {
  private final Notification n=new Notification();private Bundle suppliedExtras=new Bundle();private final java.util.ArrayList<Action> builtActions=new java.util.ArrayList<Action>();
  public Builder(Context context){if(context==null)throw new NullPointerException("context");}
  public Builder(Context context,String channel){this(context);n.channelId=channel;}
  private Builder flag(int flag,boolean set){n.flags=set?n.flags|flag:n.flags&~flag;return this;}
  private Builder text(String key,CharSequence value){if(value!=null&&value.length()>8192)throw new IllegalArgumentException("Notification text limit");n.extras.putCharSequence(key,value==null?null:value.toString());return this;}
  public Builder setContentTitle(CharSequence v){return text(EXTRA_TITLE,v);}public Builder setContentText(CharSequence v){return text(EXTRA_TEXT,v);}public Builder setContentInfo(CharSequence v){return text("android.infoText",v);}public Builder setSubText(CharSequence v){return text("android.subText",v);}
  public Builder setWhen(long v){n.when=v;return this;}public Builder setSmallIcon(int v){return setSmallIcon(v,0);}public Builder setSmallIcon(int v,int level){n.icon=v;n.iconLevel=level;return this;}public Builder setSmallIcon(Icon v){n.smallIcon=v;return this;}
  public Builder setContentIntent(PendingIntent v){n.contentIntent=v;return this;}public Builder setDeleteIntent(PendingIntent v){n.deleteIntent=v;return this;}
  public Builder setTicker(CharSequence v){n.tickerText=v;return this;}public Builder setTicker(CharSequence v,RemoteViews views){custom(views);return setTicker(v);}
  public Builder setOngoing(boolean v){return flag(FLAG_ONGOING_EVENT,v);}public Builder setOnlyAlertOnce(boolean v){return flag(FLAG_ONLY_ALERT_ONCE,v);}public Builder setAutoCancel(boolean v){return flag(FLAG_AUTO_CANCEL,v);}public Builder setLocalOnly(boolean v){return flag(FLAG_LOCAL_ONLY,v);}public Builder setGroupSummary(boolean v){return flag(FLAG_GROUP_SUMMARY,v);}
  public Builder setDefaults(int v){n.defaults=v;return this;}public Builder setPriority(int v){n.priority=v;return this;}public Builder setNumber(int v){n.number=v;return this;}public Builder setColor(int v){n.color=v;return this;}public Builder setVisibility(int v){n.visibility=v;return this;}
  public Builder setGroup(String v){n.group=v;return this;}public Builder setSortKey(String v){n.sortKey=v;return this;}public Builder setCategory(String v){n.category=v;return this;}
  public Builder setProgress(int max,int value,boolean indefinite){if(max<0||value<0)throw new IllegalArgumentException("Notification progress");n.extras.putInt(EXTRA_PROGRESS_MAX,max);n.extras.putInt(EXTRA_PROGRESS,value);n.extras.putBoolean(EXTRA_PROGRESS_INDETERMINATE,indefinite);return this;}
  public Builder setExtras(Bundle v){suppliedExtras=v==null?new Bundle():new Bundle(v);return this;}public Builder addExtras(Bundle v){if(v!=null)suppliedExtras.putAll(v);return this;}
  public Builder setSound(Uri v){n.sound=v;return this;}public Builder setSound(Uri v,AudioAttributes a){n.sound=v;n.audioAttributes=a;return this;}public Builder setVibrate(long[] v){if(v!=null&&v.length>256)throw new IllegalArgumentException("Vibration limit");n.vibrate=v==null?null:v.clone();return this;}public Builder setLights(int argb,int on,int off){n.ledARGB=argb;n.ledOnMS=on;n.ledOffMS=off;return this;}
  public Builder setSettingsText(CharSequence v){return text("android.settingsText",v);}
  public Builder setShowWhen(boolean v){n.extras.putBoolean("android.showWhen",v);return this;}public Builder setUsesChronometer(boolean v){n.extras.putBoolean("android.showChronometer",v);return this;}
  public Builder setTimeoutAfter(long v){if(v<0)throw new IllegalArgumentException("timeout");n.timeout=v;return this;}
  public Builder setBadgeIconType(int v){n.extras.putInt("shiraz.badge",v);return this;}public Builder setGroupAlertBehavior(int v){n.extras.putInt("shiraz.groupAlert",v);return this;}public Builder setShortcutId(String v){n.extras.putString("shiraz.shortcut",v);return this;}public Builder setColorized(boolean v){n.extras.putBoolean("shiraz.colorized",v);return this;}
  public Builder setPublicVersion(Notification v){if(v!=null)n.extras.putBundle("shiraz.public",new Bundle(v.extras));return this;}
  public Builder setRemoteInputHistory(CharSequence[] v){if(v!=null&&v.length!=0)throw new UnsupportedOperationException("Notification remote input");return this;}
  public Builder setFullScreenIntent(PendingIntent v,boolean high){if(v!=null)throw new UnsupportedOperationException("Full-screen notifications");return this;}
  public Builder setLargeIcon(Bitmap v){n.largeIcon=v;n.largeIconObj=v==null?null:Icon.createWithBitmap(v);return this;}public Builder setLargeIcon(Icon v){n.largeIconObj=v;n.largeIcon=null;return this;}
  public Builder addAction(int icon,CharSequence title,PendingIntent intent){return addAction(new Action(icon,title,intent));}
  public Builder addAction(Action action){if(action==null)throw new NullPointerException("action");if(builtActions.size()>=10)throw new IllegalStateException("Notification action limit");builtActions.add(action);return this;}
  private static void custom(RemoteViews v){if(v!=null)throw new UnsupportedOperationException("Custom notification views");}
  public Builder setContent(RemoteViews v){custom(v);return this;}public Builder setCustomContentView(RemoteViews v){custom(v);return this;}public Builder setCustomBigContentView(RemoteViews v){custom(v);return this;}public Builder setCustomHeadsUpContentView(RemoteViews v){custom(v);return this;}
  public Builder addPerson(String person){if(person!=null)throw new UnsupportedOperationException("Person notifications");return this;}
  public Builder setStyle(Style style){if(style!=null){style.builder=this;style.apply();}return this;}
  public Notification build(){Notification result=n.clone();Bundle merged=new Bundle(suppliedExtras);merged.putAll(result.extras);result.extras=merged;if(!builtActions.isEmpty())result.actions=builtActions.toArray(new Action[builtActions.size()]);return result;}public Notification getNotification(){return build();}
 }
 public static abstract class Style {protected Builder builder;protected abstract void apply();}
 public static class MediaStyle extends Style {
  private android.media.session.MediaSession.Token token;private int[] compact;
  public MediaStyle(){}public MediaStyle(Builder builder){this.builder=builder;builder.setStyle(this);}
  public MediaStyle setMediaSession(android.media.session.MediaSession.Token token){this.token=token;if(builder!=null)apply();return this;}
  public MediaStyle setShowActionsInCompactView(int... actions){if(actions==null||actions.length>3)throw new IllegalArgumentException("Compact media actions");compact=actions.clone();if(builder!=null)apply();return this;}
  protected void apply(){builder.n.extras.putParcelable("android.mediaSession",token);if(compact!=null)builder.n.extras.putIntArray("android.compactActions",compact.clone());builder.setCategory("transport");}
 }
 public static class BigTextStyle extends Style {
  private CharSequence bigText,bigTitle,summary;
  public BigTextStyle(){}public BigTextStyle(Builder b){builder=b;b.setStyle(this);}
  public BigTextStyle bigText(CharSequence text){bigText=text;if(builder!=null)apply();return this;}
  public BigTextStyle setBigContentTitle(CharSequence text){bigTitle=text;if(builder!=null)apply();return this;}
  public BigTextStyle setSummaryText(CharSequence text){summary=text;if(builder!=null)apply();return this;}
  protected void apply(){if(bigText!=null)builder.text("android.bigText",bigText);if(bigTitle!=null)builder.text("android.title.big",bigTitle);if(summary!=null)builder.text("android.summaryText",summary);}
 }
}
