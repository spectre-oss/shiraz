// SPDX-License-Identifier: GPL-3.0-only
package android.media.session;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class MediaSession {
 public static final int FLAG_HANDLES_MEDIA_BUTTONS=1;
 public static final int FLAG_HANDLES_TRANSPORT_CONTROLS=2;
 private final Token token=new Token(this);
 private Handler handler;
 private boolean released;
 private final String packageName;
 private final MediaController controller;
 final java.util.Map<MediaController.Callback,Handler> controllers=new java.util.LinkedHashMap<>();
 private Callback callback;
 // AndroidX inspects this platform field as a Handler during teardown.
 private Handler mCallback;
 private boolean active;
 PlaybackState playbackState;
 Bundle extras;
 java.util.List<QueueItem> queue;
 CharSequence queueTitle;
 int ratingType;
 android.media.MediaMetadata metadata;
 long flags;
 android.app.PendingIntent activity;
 public MediaSession(Context context,String tag){if(context==null||tag==null)throw new IllegalArgumentException("MediaSession");packageName=context.getPackageName();controller=new MediaController(context,token);}
 public MediaSession(Context context,String tag,Bundle sessionInfo){this(context,tag);}
 public void setCallback(Callback callback){setCallback(callback,null);}
 public void setCallback(Callback callback,Handler handler){this.callback=callback;this.handler=callback==null?null:handler==null?new Handler():new Handler(handler.getLooper());this.mCallback=this.handler;}
 public void setRatingType(int type){if(type>=0&&type<=6)ratingType=type;}
 public void setMetadata(final android.media.MediaMetadata value){
  metadata=value;
  for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){
   final MediaController.Callback c=e.getKey();e.getValue().post(new Runnable(){public void run(){c.onMetadataChanged(value);}});
  }
 }
 public void setFlags(int flags){this.flags=flags;}
 public void setPlaybackState(PlaybackState state){playbackState=state;for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){final MediaController.Callback c=e.getKey();e.getValue().post(new Runnable(){public void run(){c.onPlaybackStateChanged(state);}});}}
 public void setQueue(java.util.List<QueueItem> value){
  final java.util.List<QueueItem> copy=value==null?null:new java.util.ArrayList<>(value);
  if(copy!=null&&copy.contains(null))throw new IllegalArgumentException("Queue contains null");
  queue=copy==null?null:java.util.Collections.unmodifiableList(copy);
  final java.util.List<QueueItem> snapshot=queue;
  for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){
   final MediaController.Callback c=e.getKey();e.getValue().post(new Runnable(){public void run(){c.onQueueChanged(snapshot);}});
  }
 }
 public void setQueueTitle(CharSequence value){
  final CharSequence snapshot=value==null?null:value.toString();queueTitle=snapshot;
  for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){
   final MediaController.Callback c=e.getKey();e.getValue().post(new Runnable(){public void run(){c.onQueueTitleChanged(snapshot);}});
  }
 }
 public static final class QueueItem implements android.os.Parcelable {
  public static final int UNKNOWN_ID=-1;
  private final android.media.MediaDescription description;private final long id;
  public QueueItem(android.media.MediaDescription description,long id){if(description==null||id==UNKNOWN_ID)throw new IllegalArgumentException("Queue description and valid ID required");this.description=description;this.id=id;}
  public android.media.MediaDescription getDescription(){return description;}
  public long getQueueId(){return id;}
  public int describeContents(){return 0;}
  public void writeToParcel(android.os.Parcel p,int flags){description.writeToParcel(p,flags);p.writeLong(id);}
  public static final android.os.Parcelable.Creator<QueueItem> CREATOR=new android.os.Parcelable.Creator<QueueItem>(){
   public QueueItem createFromParcel(android.os.Parcel p){return new QueueItem(android.media.MediaDescription.CREATOR.createFromParcel(p),p.readLong());}
   public QueueItem[] newArray(int size){return new QueueItem[size];}
  };
 }
 public void setPlaybackToLocal(android.media.AudioAttributes attributes){}
 public void setActive(boolean active){if(!released)this.active=active;}
 public boolean isActive(){return active;}
 public void sendSessionEvent(String event,Bundle extras){if(event==null||event.isEmpty())throw new IllegalArgumentException("event");for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){final MediaController.Callback c=e.getKey();final Bundle copy=extras==null?null:new Bundle(extras);e.getValue().post(new Runnable(){public void run(){c.onSessionEvent(event,copy);}});}}
 public void release(){if(released)return;released=true;active=false;callback=null;mCallback=null;handler=null;for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){final MediaController.Callback c=e.getKey();e.getValue().post(new Runnable(){public void run(){c.onSessionDestroyed();}});}controllers.clear();}
 public MediaController getController(){return controller;}
 String packageName(){return packageName;}
 boolean released(){return released;}
 interface Action {void accept(Callback callback);}
 void dispatch(final Action action){final Callback target=callback;final Handler dispatcher=handler;if(!released&&target!=null&&dispatcher!=null)dispatcher.post(new Runnable(){public void run(){if(!released&&callback==target)action.accept(target);}});}
 public Token getSessionToken(){return token;}
 public void setExtras(Bundle extras){this.extras=extras==null?null:new Bundle(extras);for(java.util.Map.Entry<MediaController.Callback,Handler> e:new java.util.LinkedHashMap<>(controllers).entrySet()){final MediaController.Callback c=e.getKey();final Bundle copy=extras==null?null:new Bundle(extras);e.getValue().post(new Runnable(){public void run(){c.onExtrasChanged(copy);}});}}
 public void setSessionActivity(android.app.PendingIntent pi){activity=pi;}
 public void setMediaButtonReceiver(android.app.PendingIntent mbr){}
 public static abstract class Callback {
  public void onCommand(String command,Bundle args,ResultReceiver cb){}
  public boolean onMediaButtonEvent(Intent mediaButtonIntent){return false;}
  public void onPlay(){}
  public void onPrepare(){}
  public void onPrepareFromMediaId(String id,Bundle extras){}
  public void onPrepareFromSearch(String query,Bundle extras){}
  public void onPrepareFromUri(android.net.Uri uri,Bundle extras){}
  public void onSetPlaybackSpeed(float speed){}
  public void onPlayFromMediaId(String mediaId,Bundle extras){}
  public void onPlayFromSearch(String query,Bundle extras){}
  public void onPlayFromUri(android.net.Uri uri,Bundle extras){}
  public void onSkipToQueueItem(long id){}
  public void onPause(){}
  public void onSkipToNext(){}
  public void onSkipToPrevious(){}
  public void onFastForward(){}
  public void onRewind(){}
  public void onStop(){}
  public void onSeekTo(long pos){}
  public void onCustomAction(String action,Bundle extras){}
 }
 public static final class Token implements android.os.Parcelable {
  final MediaSession session;
  Token(MediaSession session){this.session=session;}
  public static final android.os.Parcelable.Creator<Token> CREATOR=new android.os.Parcelable.Creator<Token>(){
   public Token createFromParcel(android.os.Parcel in){throw new UnsupportedOperationException("MediaSession tokens require in-process transport");}
   public Token[] newArray(int size){return new Token[size];}
  };
  public int describeContents(){return 0;}
  public void writeToParcel(android.os.Parcel dest,int flags){throw new UnsupportedOperationException("MediaSession tokens require in-process transport");}
 }
}
