// SPDX-License-Identifier: GPL-3.0-only
package android.media.session;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/** Controller for a session hosted in this runtime process. */
public final class MediaController {
 private final MediaSession.Token token;
 private final MediaSession session;
 private final TransportControls controls=new TransportControls();
 public MediaController(Context context,MediaSession.Token token){
  if(context==null||token==null)throw new IllegalArgumentException("context and token required");
  this.token=token;this.session=token.session;
 }
 public MediaSession.Token getSessionToken(){return token;}
 public String getPackageName(){return session.packageName();}
 public PlaybackState getPlaybackState(){return session.playbackState;}
 public java.util.List<MediaSession.QueueItem> getQueue(){return session.queue==null?null:new java.util.ArrayList<>(session.queue);}
 public CharSequence getQueueTitle(){return session.queueTitle;}
 public Bundle getExtras(){return session.extras==null?null:new Bundle(session.extras);}
 public int getRatingType(){return session.ratingType;}
 public android.media.MediaMetadata getMetadata(){return session.metadata;}
 public long getFlags(){return session.flags;}
 public android.app.PendingIntent getSessionActivity(){return session.activity;}
 public TransportControls getTransportControls(){return controls;}
 public boolean controlsSameSession(MediaController other){return other!=null&&other.session==session;}
 public void registerCallback(Callback callback){registerCallback(callback,null);}
 public void registerCallback(Callback callback,Handler handler){
  if(callback==null)throw new IllegalArgumentException("callback");
  Handler delivery=handler==null?new Handler():handler;
  if(session.released())delivery.post(new Runnable(){public void run(){callback.onSessionDestroyed();}});
  else session.controllers.put(callback,delivery);
 }
 public void unregisterCallback(Callback callback){if(callback==null)throw new IllegalArgumentException("callback");session.controllers.remove(callback);}
 public void sendCommand(String command,Bundle args,ResultReceiver receiver){
  if(command==null||command.isEmpty())throw new IllegalArgumentException("command");
  final Bundle copy=args==null?null:new Bundle(args);
  session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onCommand(command,copy,receiver);}});
 }
 public boolean dispatchMediaButtonEvent(android.view.KeyEvent event){
  if(event==null)throw new IllegalArgumentException("event");
  if(session.released())return false;
  android.content.Intent intent=new android.content.Intent("android.intent.action.MEDIA_BUTTON");
  intent.putExtra("android.intent.extra.KEY_EVENT",event);
  session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onMediaButtonEvent(intent);}});return true;
 }
 public static abstract class Callback {
  public void onQueueChanged(java.util.List<MediaSession.QueueItem> queue){}
  public void onQueueTitleChanged(CharSequence title){}
  public void onMetadataChanged(android.media.MediaMetadata metadata){}
  public void onSessionDestroyed(){}
  public void onPlaybackStateChanged(PlaybackState state){}
  public void onExtrasChanged(Bundle extras){}
  public void onSessionEvent(String event,Bundle extras){}
 }
 public final class TransportControls {
  public void prepareFromUri(android.net.Uri value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPrepareFromUri(value,copy);}});}
  public void prepareFromSearch(String value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPrepareFromSearch(value,copy);}});}
  public void prepareFromMediaId(String value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPrepareFromMediaId(value,copy);}});}
  public void play(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPlay();}});}
  public void pause(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPause();}});}
  public void stop(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onStop();}});}
  public void skipToNext(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onSkipToNext();}});}
  public void skipToPrevious(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onSkipToPrevious();}});}
  public void fastForward(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onFastForward();}});}
  public void rewind(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onRewind();}});}
  public void prepare(){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPrepare();}});}
  public void setPlaybackSpeed(float speed){if(speed==0||Float.isNaN(speed)||Float.isInfinite(speed))throw new IllegalArgumentException("speed");session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onSetPlaybackSpeed(speed);}});}
  public void seekTo(long position){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onSeekTo(position);}});}
  public void skipToQueueItem(long id){session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onSkipToQueueItem(id);}});}
  public void playFromMediaId(String value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPlayFromMediaId(value,copy);}});}
  public void playFromSearch(String value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPlayFromSearch(value,copy);}});}
  public void playFromUri(android.net.Uri value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onPlayFromUri(value,copy);}});}
  public void sendCustomAction(String value,Bundle extras){final Bundle copy=extras==null?null:new Bundle(extras);session.dispatch(new MediaSession.Action(){public void accept(MediaSession.Callback c){c.onCustomAction(value,copy);}});}
 }
}
