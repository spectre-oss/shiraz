// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.session.MediaController;
import android.os.*;
import java.util.*;

final class MediaSessionFixture {
 static void pump(){try{Looper.class.getMethod("shirazDispatchDue",int.class).invoke(Looper.myLooper(),100);}catch(Exception e){throw new AssertionError(e);}}
 static void check(Context context) {
  final List<String> events=new ArrayList<>();
  final Thread owner=Thread.currentThread();
  MediaSession session=new MediaSession(context,"fixture");
  session.setCallback(new MediaSession.Callback(){
   public void onPlay(){if(Thread.currentThread()!=owner)throw new AssertionError("thread");events.add("play");}
   public void onSeekTo(long position){events.add("seek"+position);}
   public void onCommand(String command,Bundle args,ResultReceiver receiver){events.add(command+args.getInt("value"));}
  });
  MediaController controller=new MediaController(context,session.getSessionToken());
  if(!controller.getSessionToken().equals(session.getController().getSessionToken())||!context.getPackageName().equals(controller.getPackageName()))throw new AssertionError("session identity");
  MediaController.Callback callback=new MediaController.Callback(){
   public void onSessionEvent(String event,Bundle extras){events.add(event+extras.getInt("value"));}
   public void onSessionDestroyed(){events.add("destroyed");}
  };
  controller.registerCallback(callback,new Handler());
  Bundle args=new Bundle();args.putInt("value",7);
  controller.getTransportControls().play();controller.getTransportControls().seekTo(42);
  controller.sendCommand("command",args,null);session.sendSessionEvent("event",args);args.putInt("value",9);
  if(!events.isEmpty())throw new AssertionError("callbacks must be queued");
  for(int i=0;i<10&&events.size()<4;i++)pump();
  if(!events.equals(Arrays.asList("play","seek42","command7","event7")))throw new AssertionError(events.toString());
  controller.unregisterCallback(callback);session.sendSessionEvent("removed",null);
  controller.registerCallback(callback);controller.getTransportControls().play();session.release();session.release();
  session.setActive(true);controller.getTransportControls().play();
  for(int i=0;i<10&&events.size()<5;i++)pump();
  if(session.isActive()||!events.equals(Arrays.asList("play","seek42","command7","event7","destroyed")))throw new AssertionError("release "+events);
  Bundle stateExtras=new Bundle();stateExtras.putInt("value",3);
  PlaybackState.Builder builder=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,123,1f,456)
   .setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE).setBufferedPosition(200).setExtras(stateExtras);
  PlaybackState state=builder.build();builder.setState(PlaybackState.STATE_PAUSED,0,0,0);stateExtras.putInt("value",4);
  state.getExtras().putInt("value",5);
  if(state.getState()!=PlaybackState.STATE_PLAYING||state.getPosition()!=123||state.getLastPositionUpdateTime()!=456||state.getBufferedPosition()!=200||state.getExtras().getInt("value")!=3)
   throw new AssertionError("playback state snapshot");
  MediaSession stateSession=new MediaSession(context,"state");
  stateSession.setFlags(3);stateSession.setPlaybackState(state);stateSession.setExtras(stateExtras);
  stateExtras.putInt("value",6);stateSession.getController().getExtras().putInt("value",7);
  if(stateSession.getController().getFlags()!=3||stateSession.getController().getPlaybackState()!=state||stateSession.getController().getExtras().getInt("value")!=4)
   throw new AssertionError("controller state");
  final List<String> queues=new ArrayList<>();
  stateSession.getController().registerCallback(new MediaController.Callback(){
   public void onQueueChanged(List<MediaSession.QueueItem> queue){queues.add(queue==null?"null":queue.isEmpty()?"empty":queue.get(0).getDescription().getMediaId());}
   public void onQueueTitleChanged(CharSequence title){queues.add(String.valueOf(title));}
  });
  android.media.MediaDescription description=new android.media.MediaDescription.Builder().setMediaId("track").setTitle("Track").build();
  MediaSession.QueueItem item=new MediaSession.QueueItem(description,42);
  List<MediaSession.QueueItem> queue=new ArrayList<>();queue.add(item);
  stateSession.setQueue(queue);queue.clear();
  if(stateSession.getController().getQueue().get(0).getQueueId()!=42)throw new AssertionError("queue snapshot");
  stateSession.getController().getQueue().clear();
  if(stateSession.getController().getQueue().size()!=1)throw new AssertionError("controller queue isolation");
  try{stateSession.setQueue(Arrays.asList((MediaSession.QueueItem)null));throw new AssertionError("null item");}catch(IllegalArgumentException expected){}
  try{new MediaSession.QueueItem(description,-1);throw new AssertionError("invalid ID");}catch(IllegalArgumentException expected){}
  Parcel itemParcel=Parcel.obtain();item.writeToParcel(itemParcel,0);itemParcel.setDataPosition(0);
  MediaSession.QueueItem restored=MediaSession.QueueItem.CREATOR.createFromParcel(itemParcel);itemParcel.recycle();
  if(restored.getQueueId()!=42||!"Track".equals(restored.getDescription().getTitle()))throw new AssertionError("queue parcel");
  stateSession.setQueue(queue);stateSession.setQueue(null);
  StringBuilder title=new StringBuilder("Playlist");stateSession.setQueueTitle(title);title.append(" changed");
  if(!"Playlist".equals(stateSession.getController().getQueueTitle())||!queues.isEmpty())throw new AssertionError("title snapshot / async delivery");
  pump();
  if(!queues.equals(Arrays.asList("track","empty","null","Playlist")))throw new AssertionError(queues.toString());
  if(stateSession.getController().getQueue()!=null)throw new AssertionError("clear queue");
  stateSession.release();
  Parcel parcel=Parcel.obtain();
  try{session.getSessionToken().writeToParcel(parcel,0);throw new AssertionError("unsupported IPC token");}catch(UnsupportedOperationException expected){}finally{parcel.recycle();}
  System.out.println("SHIRAZ_FIXTURE_MEDIA_SESSION_OK");
 }
}
