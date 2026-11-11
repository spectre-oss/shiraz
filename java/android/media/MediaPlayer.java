// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.io.*;
import java.util.Map;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import shiraz.runtime.NativeFd;
import shiraz.runtime.NativePlayback;
/** Local, bounded audio playback. Video output and network streams fail explicitly through the Android error paths. */
public class MediaPlayer {
 public interface OnCompletionListener {void onCompletion(MediaPlayer player);}
 public interface OnErrorListener {boolean onError(MediaPlayer player,int what,int extra);}
 public interface OnPreparedListener {void onPrepared(MediaPlayer player);}
 public interface OnSeekCompleteListener {void onSeekComplete(MediaPlayer player);}
 public interface OnInfoListener {boolean onInfo(MediaPlayer player,int what,int extra);}
 public interface OnBufferingUpdateListener {void onBufferingUpdate(MediaPlayer player,int percent);}
 public interface OnVideoSizeChangedListener {void onVideoSizeChanged(MediaPlayer player,int width,int height);}
 public static final int MEDIA_ERROR_UNKNOWN=1,MEDIA_ERROR_SERVER_DIED=100,MEDIA_ERROR_IO=-1004,MEDIA_ERROR_MALFORMED=-1007,MEDIA_ERROR_UNSUPPORTED=-1010,MEDIA_ERROR_TIMED_OUT=-110;
 public static final int MEDIA_INFO_UNKNOWN=1,MEDIA_INFO_BUFFERING_START=701,MEDIA_INFO_BUFFERING_END=702;
 public static final int SEEK_PREVIOUS_SYNC=0,SEEK_NEXT_SYNC=1,SEEK_CLOSEST_SYNC=2,SEEK_CLOSEST=3;
 private static final String TAG="MediaPlayer";
 private static final int MAX_SOURCE=16*1024*1024;
 private static final java.util.concurrent.atomic.AtomicInteger SESSIONS=new java.util.concurrent.atomic.AtomicInteger(1);
 private enum State {IDLE,INITIALIZED,PREPARING,PREPARED,STARTED,PAUSED,STOPPED,COMPLETE,ERROR,RELEASED}
 private State state=State.IDLE;private byte[] source,pcm;private int rate,channels,offset,generation;private boolean looping,video;private float volume=1;private AudioTrack track;
 private int sessionId=SESSIONS.getAndIncrement();
 private OnCompletionListener completion;private OnErrorListener error;private OnPreparedListener prepared;private OnSeekCompleteListener seekComplete;
 private final Handler handler;
 public MediaPlayer(){Looper looper=Looper.myLooper();if(looper==null)looper=Looper.getMainLooper();handler=looper==null?null:new Handler(looper);}
 public static MediaPlayer create(Context context,int resid){return create(context,resid,null,0);}
 public static MediaPlayer create(Context context,int resid,AudioAttributes attributes,int audioSessionId){
  if(context==null)throw new NullPointerException("context");
  MediaPlayer player=new MediaPlayer();
  try{
   if(attributes!=null)player.setAudioAttributes(attributes);
   if(audioSessionId>0)player.setAudioSessionId(audioSessionId);
   Resources resources=context.getResources();AssetFileDescriptor asset=null;
   try{asset=resources.openRawResourceFd(resid);}catch(Resources.NotFoundException compressed){asset=null;}
   if(asset!=null){try{player.setDataSource(asset);}finally{asset.close();}}
   else{try(InputStream in=resources.openRawResource(resid)){player.setSource(readBounded(in));}}
   player.prepare();return player;
  }catch(IOException|IllegalArgumentException|SecurityException|Resources.NotFoundException failure){
   android.util.Log.w(TAG,"create failed",failure);player.release();return null;
  }
 }
 public static MediaPlayer create(Context context,Uri uri){return create(context,uri,null,null,0);}
 public static MediaPlayer create(Context context,Uri uri,SurfaceHolder holder){return create(context,uri,holder,null,0);}
 public static MediaPlayer create(Context context,Uri uri,SurfaceHolder holder,AudioAttributes attributes,int audioSessionId){
  MediaPlayer player=new MediaPlayer();
  try{
   if(attributes!=null)player.setAudioAttributes(attributes);
   if(audioSessionId>0)player.setAudioSessionId(audioSessionId);
   player.setDataSource(context,uri);if(holder!=null)player.setDisplay(holder);player.prepare();return player;
  }catch(IOException|IllegalArgumentException|SecurityException failure){
   android.util.Log.w(TAG,"create failed",failure);player.release();return null;
  }
 }
 private void live(){if(state==State.RELEASED)throw new IllegalStateException("MediaPlayer released");}
 private static byte[] readBounded(InputStream in)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
  while((n=in.read(buffer))>0){if(out.size()+(long)n>MAX_SOURCE)throw new IOException("Audio source region limit");out.write(buffer,0,n);}
  return out.toByteArray();
 }
 private synchronized void setSource(byte[] bytes)throws IOException{live();if(state!=State.IDLE)throw new IllegalStateException("MediaPlayer is not idle");if(bytes.length>MAX_SOURCE)throw new IOException("Audio source region limit");source=bytes;state=State.INITIALIZED;}
 public synchronized void setDataSource(FileDescriptor fd,long start,long length)throws IOException{live();if(state!=State.IDLE)throw new IllegalStateException("MediaPlayer is not idle");if(fd==null||start<0||length<0||length>MAX_SOURCE)throw new IOException("Audio source region limit");synchronized(fd){source=NativeFd.readRegion(fd,start,length);}state=State.INITIALIZED;}
 public void setDataSource(FileDescriptor fd)throws IOException{setDataSource(fd,0,NativeFd.size(fd));}
 public void setDataSource(AssetFileDescriptor asset)throws IOException{
  if(asset==null)throw new IllegalArgumentException("asset");
  if(asset.getDeclaredLength()<0)setDataSource(asset.getFileDescriptor());else setDataSource(asset.getFileDescriptor(),asset.getStartOffset(),asset.getDeclaredLength());
 }
 public void setDataSource(MediaDataSource data)throws IOException{
  if(data==null)throw new IllegalArgumentException("data source");
  long size=data.getSize();if(size<0||size>MAX_SOURCE)throw new IOException("Audio data source must have a known size up to the region limit");
  byte[] bytes=new byte[(int)size];int done=0;
  while(done<bytes.length){int n=data.readAt(done,bytes,done,bytes.length-done);if(n<=0)throw new IOException("Short media data source");done+=n;}
  setSource(bytes);
 }
 public void setDataSource(String path)throws IOException{
  if(path==null)throw new IllegalArgumentException("path");
  if(network(path))throw new IOException("Network media streams are not implemented: "+path);
  if(path.startsWith("file://"))path=Uri.parse(path).getPath();
  try(FileInputStream in=new FileInputStream(path)){setDataSource(in.getFD());}
 }
 public void setDataSource(Context context,Uri uri)throws IOException{setDataSource(context,uri,null);}
 public void setDataSource(Context context,Uri uri,Map<String,String> headers)throws IOException{
  if(context==null||uri==null)throw new IllegalArgumentException("context/uri");
  if(network(uri.toString()))throw new IOException("Network media streams are not implemented: "+uri.getScheme());
  if("file".equals(uri.getScheme())||uri.getScheme()==null){setDataSource(uri.getPath());return;}
  try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new FileNotFoundException(uri.toString());setSource(readBounded(in));}
 }
 private static boolean network(String location){String lower=location.toLowerCase(java.util.Locale.ROOT);return lower.startsWith("http://")||lower.startsWith("https://")||lower.startsWith("rtsp://");}
 private static final class Decoded {final byte[] pcm;final int rate,channels;Decoded(byte[] pcm,int rate,int channels){this.pcm=pcm;this.rate=rate;this.channels=channels;}}
 private static Decoded decode(byte[] bytes,boolean video)throws IOException{
  if(video)throw new IOException("MediaPlayer video output is not implemented");
  try{int[] format=new int[2];byte[] samples=NativePlayback.decodeFile(bytes,format);return new Decoded(samples,format[0],format[1]);}
  catch(RuntimeException e){throw new IOException("Audio decoding failed",e);}
 }
 private void install(Decoded decoded){pcm=decoded.pcm;rate=decoded.rate;channels=decoded.channels;offset=0;state=State.PREPARED;}
 public void prepare()throws IOException{
  byte[] input;boolean wantsVideo;
  synchronized(this){live();if(state!=State.INITIALIZED&&state!=State.STOPPED)throw new IllegalStateException("MediaPlayer source not initialized");input=source;wantsVideo=video;}
  try{Decoded decoded=decode(input,wantsVideo);synchronized(this){live();install(decoded);}}
  catch(IOException failure){synchronized(this){if(state!=State.RELEASED)state=State.ERROR;}throw failure;}
 }
 public synchronized void prepareAsync(){
  live();if(state!=State.INITIALIZED&&state!=State.STOPPED)throw new IllegalStateException("MediaPlayer source not initialized");
  state=State.PREPARING;final int token=generation;final byte[] input=source;final boolean wantsVideo=video;
  new Thread(new Runnable(){public void run(){
   try{
    final Decoded decoded=decode(input,wantsVideo);
    synchronized(MediaPlayer.this){if(token!=generation||state!=State.PREPARING)return;install(decoded);}
    dispatch(token,new Runnable(){public void run(){if(prepared!=null)prepared.onPrepared(MediaPlayer.this);}});
   }catch(IOException failure){
    synchronized(MediaPlayer.this){if(token!=generation||state!=State.PREPARING)return;state=State.ERROR;}
    android.util.Log.w(TAG,"prepareAsync failed",failure);post(token,true,wantsVideo?MEDIA_ERROR_UNSUPPORTED:MEDIA_ERROR_MALFORMED);
   }
  }},"Shiraz MediaPlayer prepare").start();
 }
 public synchronized void start(){live();if(state==State.STARTED)return;if(state!=State.PREPARED&&state!=State.PAUSED&&state!=State.COMPLETE)throw new IllegalStateException("MediaPlayer not prepared");if(state==State.COMPLETE)offset=0;
  state=State.STARTED;spawn();}
 private void spawn(){final int token=++generation;new Thread(new Runnable(){public void run(){play(token);}},"Shiraz MediaPlayer").start();}
 private void play(final int token){AudioTrack output=null;int startOffset=0;try{
  synchronized(this){if(token!=generation||state!=State.STARTED)return;output=new AudioTrack(3,rate,channels==1?4:12,2,Math.max(4096,rate*channels/5),1);track=output;startOffset=offset;output.setVolume(volume);output.play();}
  for(;;){synchronized(this){if(token!=generation||state!=State.STARTED)return;if(offset>=pcm.length)break;int n=output.write(pcm,offset,Math.min(4096,pcm.length-offset),AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IllegalStateException("Audio output failed: "+n);offset+=n;}Thread.sleep(5);}
  output.shirazStartPending();
  long deadline=android.os.SystemClock.uptimeMillis()+getDuration()+2000;
  while(output.getPlaybackHeadPosition()<((pcm.length-startOffset)/(channels*2))&&android.os.SystemClock.uptimeMillis()<deadline){synchronized(this){if(token!=generation||state!=State.STARTED)return;}Thread.sleep(5);}
  synchronized(this){if(token!=generation||state!=State.STARTED)return;if(output.getPlaybackHeadPosition()<(pcm.length-startOffset)/(channels*2))throw new IllegalStateException("Audio drain timed out");state=State.COMPLETE;if(looping){start();return;}}
  post(token,false,0);
 }catch(Exception failure){synchronized(this){if(token!=generation||state==State.RELEASED)return;state=State.ERROR;}android.util.Log.w(TAG,"Audio playback failed",failure);post(token,true,MEDIA_ERROR_UNKNOWN);
 }finally{if(output!=null)output.release();synchronized(this){if(track==output)track=null;}}}
 private void dispatch(final int token,final Runnable callback){if(handler!=null)handler.post(new Runnable(){public void run(){synchronized(MediaPlayer.this){if(token!=generation||state==State.RELEASED)return;}callback.run();}});}
 private void post(final int token,final boolean failed,final int what){dispatch(token,new Runnable(){public void run(){OnErrorListener onError;OnCompletionListener onCompletion;synchronized(MediaPlayer.this){onError=error;onCompletion=completion;}if(failed&&onError!=null&&onError.onError(MediaPlayer.this,what,0))return;if(onCompletion!=null)onCompletion.onCompletion(MediaPlayer.this);}});}
 private void cancel(){generation++;if(track!=null){track.release();track=null;}}
 public synchronized void pause(){live();if(state==State.STARTED){cancel();state=State.PAUSED;}}
 public synchronized void stop(){live();if(state==State.IDLE||state==State.INITIALIZED)throw new IllegalStateException("MediaPlayer not prepared");cancel();offset=0;state=State.STOPPED;}
 public synchronized void reset(){live();cancel();source=null;pcm=null;offset=0;video=false;state=State.IDLE;}
 public synchronized void release(){if(state==State.RELEASED)return;cancel();source=null;pcm=null;completion=null;error=null;prepared=null;seekComplete=null;state=State.RELEASED;}
 public synchronized void seekTo(int msec){
  live();if(state!=State.PREPARED&&state!=State.STARTED&&state!=State.PAUSED&&state!=State.COMPLETE)throw new IllegalStateException("MediaPlayer cannot seek in state "+state);
  int frame=channels*2;long target=(long)Math.max(0,msec)*rate/1000*frame;offset=(int)Math.min(target,pcm.length-pcm.length%frame);
  if(state==State.STARTED){cancel();spawn();}else if(state==State.COMPLETE)state=State.PAUSED;
  dispatch(generation,new Runnable(){public void run(){OnSeekCompleteListener listener;synchronized(MediaPlayer.this){listener=seekComplete;}if(listener!=null)listener.onSeekComplete(MediaPlayer.this);}});
 }
 public void seekTo(long msec,int mode){if(mode<SEEK_PREVIOUS_SYNC||mode>SEEK_CLOSEST)throw new IllegalArgumentException("Illegal seek mode: "+mode);seekTo((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,msec)));}
 public synchronized boolean isPlaying(){live();return state==State.STARTED;}
 public synchronized int getDuration(){live();return pcm==null?0:(int)((long)pcm.length*1000/(rate*channels*2));}
 public synchronized int getCurrentPosition(){live();return pcm==null?0:(int)((long)offset*1000/(rate*channels*2));}
 public synchronized int getVideoWidth(){live();return 0;}
 public synchronized int getVideoHeight(){live();return 0;}
 public synchronized void setVolume(float left,float right){live();if(!Float.isFinite(left)||!Float.isFinite(right)||left!=right)throw new IllegalArgumentException("Equal finite channel gains required");volume=Math.max(0,Math.min(1,left));if(track!=null)track.setVolume(volume);}
 public synchronized void setLooping(boolean value){live();looping=value;}
 public synchronized boolean isLooping(){live();return looping;}
 public synchronized void setAudioAttributes(AudioAttributes attributes){live();if(attributes==null)throw new IllegalArgumentException("attributes");}
 public synchronized void setAudioStreamType(int stream){live();}
 public synchronized int getAudioSessionId(){live();return sessionId;}
 public synchronized void setAudioSessionId(int id){live();if(state!=State.IDLE)throw new IllegalStateException("Audio session must be set while idle");if(id<=0)throw new IllegalArgumentException("Audio session id");sessionId=id;}
 /** Wake locks and screen-on requests have no host effect; accepted like a device without power management. */
 public synchronized void setWakeMode(Context context,int mode){live();}
 public synchronized void setScreenOnWhilePlaying(boolean value){live();}
 /** A video surface is recorded; prepare then fails with MEDIA_ERROR_UNSUPPORTED because only audio is decoded. */
 public synchronized void setDisplay(SurfaceHolder holder){live();video=holder!=null;}
 public synchronized void setSurface(Surface surface){live();video=surface!=null;}
 public synchronized void setNextMediaPlayer(MediaPlayer next){live();if(next!=null)throw new UnsupportedOperationException("Gapless MediaPlayer chaining is not implemented");}
 public synchronized void setOnCompletionListener(OnCompletionListener value){completion=value;}
 public synchronized void setOnErrorListener(OnErrorListener value){error=value;}
 public synchronized void setOnPreparedListener(OnPreparedListener value){prepared=value;}
 public synchronized void setOnSeekCompleteListener(OnSeekCompleteListener value){seekComplete=value;}
 /** No buffering, info or video-size events are produced for local audio sources. */
 public void setOnInfoListener(OnInfoListener value){}
 public void setOnBufferingUpdateListener(OnBufferingUpdateListener value){}
 public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener value){}
}
