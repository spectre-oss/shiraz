// SPDX-License-Identifier: GPL-3.0-only
package android.media.session;
import android.os.*;
import java.util.*;
/** Immutable in-process playback state. */
public final class PlaybackState implements Parcelable {
 public static final int STATE_NONE=0;
 public static final int STATE_STOPPED=1;
 public static final int STATE_PAUSED=2;
 public static final int STATE_PLAYING=3;
 public static final int STATE_FAST_FORWARDING=4;
 public static final int STATE_REWINDING=5;
 public static final int STATE_BUFFERING=6;
 public static final int STATE_ERROR=7;
 public static final int STATE_CONNECTING=8;
 public static final int STATE_SKIPPING_TO_PREVIOUS=9;
 public static final int STATE_SKIPPING_TO_NEXT=10;
 public static final int STATE_SKIPPING_TO_QUEUE_ITEM=11;
 public static final long ACTION_STOP=1L<<0;
 public static final long ACTION_PAUSE=1L<<1;
 public static final long ACTION_PLAY=1L<<2;
 public static final long ACTION_REWIND=1L<<3;
 public static final long ACTION_SKIP_TO_PREVIOUS=1L<<4;
 public static final long ACTION_SKIP_TO_NEXT=1L<<5;
 public static final long ACTION_FAST_FORWARD=1L<<6;
 public static final long ACTION_SET_RATING=1L<<7;
 public static final long ACTION_SEEK_TO=1L<<8;
 public static final long ACTION_PLAY_PAUSE=1L<<9;
 public static final long ACTION_PLAY_FROM_MEDIA_ID=1L<<10;
 public static final long ACTION_PLAY_FROM_SEARCH=1L<<11;
 public static final long ACTION_SKIP_TO_QUEUE_ITEM=1L<<12;
 public static final long ACTION_PLAY_FROM_URI=1L<<13;
 public static final long ACTION_PREPARE=1L<<14;
 public static final long ACTION_PREPARE_FROM_MEDIA_ID=1L<<15;
 public static final long ACTION_PREPARE_FROM_SEARCH=1L<<16;
 public static final long ACTION_PREPARE_FROM_URI=1L<<17;
 public static final long ACTION_SET_REPEAT_MODE=1L<<18;
 public static final long ACTION_SET_SHUFFLE_MODE_ENABLED=1L<<19;
 public static final long ACTION_SET_CAPTIONING_ENABLED=1L<<20;
 public static final long ACTION_SET_SHUFFLE_MODE=1L<<21;
 public static final long ACTION_SET_PLAYBACK_SPEED=1L<<22;
 public static final long PLAYBACK_POSITION_UNKNOWN=-1;
 private final Builder b;
 private PlaybackState(Builder source){b=new Builder(source);}
 public int getState(){return b.state;}
 public long getPosition(){return b.position;}
 public float getPlaybackSpeed(){return b.speed;}
 public long getLastPositionUpdateTime(){return b.updated;}
 public long getBufferedPosition(){return b.buffered;}
 public long getActions(){return b.actions;}
 public long getActiveQueueItemId(){return b.queueId;}
 public CharSequence getErrorMessage(){return b.error;}
 public Bundle getExtras(){return b.extras==null?null:new Bundle(b.extras);}
 public List<CustomAction> getCustomActions(){return Collections.unmodifiableList(b.custom);}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("PlaybackState IPC");}
 public static final Parcelable.Creator<PlaybackState> CREATOR=new Parcelable.Creator<PlaybackState>(){
  public PlaybackState createFromParcel(Parcel in){throw new UnsupportedOperationException("PlaybackState IPC");}
  public PlaybackState[] newArray(int n){return new PlaybackState[n];}
 };
 public static final class Builder {
  int state;long position,updated,buffered,actions,queueId=-1;float speed;String error;Bundle extras;
  final ArrayList<CustomAction> custom=new ArrayList<>();
  public Builder(){}
  public Builder(PlaybackState source){this(source.b);}
  private Builder(Builder source){state=source.state;position=source.position;updated=source.updated;buffered=source.buffered;actions=source.actions;queueId=source.queueId;speed=source.speed;error=source.error;extras=source.extras==null?null:new Bundle(source.extras);custom.addAll(source.custom);}
  public Builder setState(int state,long position,float speed){return setState(state,position,speed,SystemClock.elapsedRealtime());}
  public Builder setState(int state,long position,float speed,long updated){this.state=state;this.position=position;this.speed=speed;this.updated=updated;return this;}
  public Builder setBufferedPosition(long value){buffered=value;return this;}
  public Builder setActions(long value){actions=value;return this;}
  public Builder setActiveQueueItemId(long value){queueId=value;return this;}
  public Builder setErrorMessage(CharSequence value){error=value==null?null:value.toString();return this;}
  public Builder setExtras(Bundle value){extras=value==null?null:new Bundle(value);return this;}
  public Builder addCustomAction(CustomAction value){if(value==null)throw new IllegalArgumentException("action");custom.add(value);return this;}
  public Builder addCustomAction(String action,String name,int icon){return addCustomAction(new CustomAction.Builder(action,name,icon).build());}
  public PlaybackState build(){return new PlaybackState(this);}
 }
 public static final class CustomAction implements Parcelable {
  private final String action,name;private final int icon;private final Bundle extras;
  private CustomAction(Builder b){action=b.action;name=b.name;icon=b.icon;extras=b.extras==null?null:new Bundle(b.extras);}
  public String getAction(){return action;}public CharSequence getName(){return name;}public int getIcon(){return icon;}public Bundle getExtras(){return extras==null?null:new Bundle(extras);}
  public int describeContents(){return 0;}public void writeToParcel(Parcel p,int flags){throw new UnsupportedOperationException("CustomAction IPC");}
  public static final Parcelable.Creator<CustomAction> CREATOR=new Parcelable.Creator<CustomAction>(){public CustomAction createFromParcel(Parcel p){throw new UnsupportedOperationException("CustomAction IPC");}public CustomAction[] newArray(int n){return new CustomAction[n];}};
  public static final class Builder {
   final String action,name;final int icon;Bundle extras;
   public Builder(String action,CharSequence name,int icon){if(action==null||action.isEmpty()||name==null||name.length()==0||icon==0)throw new IllegalArgumentException("custom action");this.action=action;this.name=name.toString();this.icon=icon;}
   public Builder setExtras(Bundle value){extras=value==null?null:new Bundle(value);return this;}public CustomAction build(){return new CustomAction(this);}
  }
 }
}
