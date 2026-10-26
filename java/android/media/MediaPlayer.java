// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.io.*;
import android.os.Handler;
import android.os.Looper;
import shiraz.runtime.NativeFd;
import shiraz.runtime.NativePlayback;
/** Local, bounded audio playback. Video and network sources are not implemented. */
public class MediaPlayer {
 public interface OnCompletionListener {void onCompletion(MediaPlayer player);}
 public interface OnErrorListener {boolean onError(MediaPlayer player,int what,int extra);}
 public static final int MEDIA_ERROR_UNKNOWN=1,MEDIA_ERROR_UNSUPPORTED=-1010;
 private enum State {IDLE,INITIALIZED,PREPARED,STARTED,PAUSED,STOPPED,COMPLETE,ERROR,RELEASED}
 private State state=State.IDLE;private byte[] source,pcm;private int rate,channels,offset,generation;private boolean looping;private float volume=1;private AudioTrack track;
 private OnCompletionListener completion;private OnErrorListener error;
 private final Handler handler;
 public MediaPlayer(){Looper looper=Looper.myLooper();if(looper==null)looper=Looper.getMainLooper();handler=looper==null?null:new Handler(looper);}
 private void live(){if(state==State.RELEASED)throw new IllegalStateException("MediaPlayer released");}
 public synchronized void setDataSource(FileDescriptor fd,long start,long length)throws IOException{live();if(state!=State.IDLE)throw new IllegalStateException("MediaPlayer is not idle");if(fd==null||start<0||length<0||length>16*1024*1024)throw new IOException("Audio source region limit");synchronized(fd){source=NativeFd.readRegion(fd,start,length);}state=State.INITIALIZED;}
 public void setDataSource(FileDescriptor fd)throws IOException{setDataSource(fd,0,NativeFd.size(fd));}
 public void setDataSource(String path)throws IOException{try(FileInputStream in=new FileInputStream(path)){setDataSource(in.getFD());}}
 public synchronized void prepare()throws IOException{live();if(state!=State.INITIALIZED&&state!=State.STOPPED)throw new IllegalStateException("MediaPlayer source not initialized");try{int[] format=new int[2];pcm=NativePlayback.decodeFile(source,format);rate=format[0];channels=format[1];offset=0;state=State.PREPARED;}catch(RuntimeException e){state=State.ERROR;throw new IOException("Audio decoding failed",e);}}
 public synchronized void start(){live();if(state==State.STARTED)return;if(state!=State.PREPARED&&state!=State.PAUSED&&state!=State.COMPLETE)throw new IllegalStateException("MediaPlayer not prepared");if(state==State.COMPLETE)offset=0;
  state=State.STARTED;final int token=++generation;new Thread(new Runnable(){public void run(){play(token);}},"Shiraz MediaPlayer").start();}
 private void play(final int token){AudioTrack output=null;int startOffset=0;try{
  synchronized(this){if(token!=generation||state!=State.STARTED)return;output=new AudioTrack(3,rate,channels==1?4:12,2,Math.max(4096,rate*channels/5),1);track=output;startOffset=offset;output.setVolume(volume);output.play();}
  for(;;){synchronized(this){if(token!=generation||state!=State.STARTED)return;if(offset>=pcm.length)break;int n=output.write(pcm,offset,Math.min(4096,pcm.length-offset),AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IllegalStateException("Audio output failed: "+n);offset+=n;}Thread.sleep(5);}
  output.shirazStartPending();
  long deadline=android.os.SystemClock.uptimeMillis()+getDuration()+2000;
  while(output.getPlaybackHeadPosition()<((pcm.length-startOffset)/(channels*2))&&android.os.SystemClock.uptimeMillis()<deadline){synchronized(this){if(token!=generation||state!=State.STARTED)return;}Thread.sleep(5);}
  synchronized(this){if(token!=generation||state!=State.STARTED)return;if(output.getPlaybackHeadPosition()<(pcm.length-startOffset)/(channels*2))throw new IllegalStateException("Audio drain timed out");state=State.COMPLETE;if(looping){start();return;}}
  post(token,false);
 }catch(Exception failure){synchronized(this){if(token!=generation||state==State.RELEASED)return;state=State.ERROR;}android.util.Log.w("MediaPlayer","Audio playback failed",failure);post(token,true);
 }finally{if(output!=null)output.release();synchronized(this){if(track==output)track=null;}}}
 private void post(final int token,final boolean failed){if(handler!=null)handler.post(new Runnable(){public void run(){synchronized(MediaPlayer.this){if(token!=generation||state==State.RELEASED)return;if(failed&&error!=null&&error.onError(MediaPlayer.this,MEDIA_ERROR_UNKNOWN,0))return;if(token==generation&&completion!=null)completion.onCompletion(MediaPlayer.this);}}});}
 private void cancel(){generation++;if(track!=null){track.release();track=null;}}
 public synchronized void pause(){live();if(state==State.STARTED){cancel();state=State.PAUSED;}}
 public synchronized void stop(){live();if(state==State.IDLE||state==State.INITIALIZED)throw new IllegalStateException("MediaPlayer not prepared");cancel();offset=0;state=State.STOPPED;}
 public synchronized void reset(){live();cancel();source=null;pcm=null;offset=0;state=State.IDLE;}
 public synchronized void release(){if(state==State.RELEASED)return;cancel();source=null;pcm=null;completion=null;error=null;state=State.RELEASED;}
 public synchronized boolean isPlaying(){live();return state==State.STARTED;}
 public synchronized int getDuration(){live();return pcm==null?0:(int)((long)pcm.length*1000/(rate*channels*2));}
 public synchronized int getCurrentPosition(){live();return pcm==null?0:(int)((long)offset*1000/(rate*channels*2));}
 public synchronized void setVolume(float left,float right){live();if(!Float.isFinite(left)||!Float.isFinite(right)||left!=right)throw new IllegalArgumentException("Equal finite channel gains required");volume=Math.max(0,Math.min(1,left));if(track!=null)track.setVolume(volume);}
 public synchronized void setLooping(boolean value){live();looping=value;}
 public synchronized boolean isLooping(){live();return looping;}
 public synchronized void setAudioAttributes(AudioAttributes attributes){live();if(attributes==null)throw new IllegalArgumentException("attributes");}
 public synchronized void setAudioStreamType(int stream){live();}
 public synchronized void setOnCompletionListener(OnCompletionListener value){completion=value;}
 public synchronized void setOnErrorListener(OnErrorListener value){error=value;}
}
