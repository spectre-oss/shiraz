// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.nio.ByteBuffer;
import shiraz.runtime.NativePlayback;
/** Streaming PCM16 output to the host audio device. */
public class AudioTrack implements AudioRouting {
 public static final int STATE_UNINITIALIZED=0,STATE_INITIALIZED=1,STATE_NO_STATIC_DATA=2;
 public static final int PLAYSTATE_STOPPED=1,PLAYSTATE_PAUSED=2,PLAYSTATE_PLAYING=3,MODE_STATIC=0,MODE_STREAM=1;
 public static final int SUCCESS=0,ERROR=-1,ERROR_BAD_VALUE=-2,ERROR_INVALID_OPERATION=-3,ERROR_DEAD_OBJECT=-6,WRITE_BLOCKING=0,WRITE_NON_BLOCKING=1;
 private long handle;private final int rate,channels,buffer,session;private int state=PLAYSTATE_STOPPED;private float volume=1;
 public AudioTrack(int stream,int rate,int mask,int encoding,int bytes,int mode){this(stream,rate,mask,encoding,bytes,mode,0);}
 public AudioTrack(int stream,int rate,int mask,int encoding,int bytes,int mode,int session){
  if(getMinBufferSize(rate,mask,encoding)<0||bytes<2||bytes>4*1024*1024||mode!=MODE_STREAM||session<0)throw new IllegalArgumentException("PCM streaming AudioTrack configuration");
  this.rate=rate;channels=mask==4?1:2;buffer=bytes;this.session=session==0?1:session;handle=NativePlayback.trackOpen(rate,channels,bytes);
 }
 public AudioTrack(AudioAttributes attributes,AudioFormat format,int bytes,int mode,int session){this(3,format.getSampleRate(),format.getChannelMask(),format.getEncoding(),bytes,mode,session);}
 public static int getMinBufferSize(int rate,int mask,int encoding){return rate<8000||rate>192000||(mask!=4&&mask!=12)||encoding!=2?ERROR_BAD_VALUE:Math.max(1024,rate/10*(mask==4?1:2)*2);}
 public static int getNativeOutputSampleRate(int stream){return 48000;}
 public static float getMaxVolume(){return 1;}public static float getMinVolume(){return 0;}
 private void live(){if(handle==0)throw new IllegalStateException("AudioTrack released");}
 public synchronized void play(){live();NativePlayback.control(handle,2);state=PLAYSTATE_PLAYING;}
 public synchronized void shirazStartPending(){live();NativePlayback.control(handle,3);}
 public synchronized void pause(){live();NativePlayback.control(handle,1);state=PLAYSTATE_PAUSED;}
 public synchronized void stop(){live();NativePlayback.control(handle,0);state=PLAYSTATE_STOPPED;}
 public synchronized void release(){if(handle!=0)NativePlayback.close(handle);handle=0;state=PLAYSTATE_STOPPED;}
 public synchronized void flush(){if(handle!=0&&state!=PLAYSTATE_PLAYING)NativePlayback.control(handle,0);}
 public int write(byte[] bytes,int offset,int length){return write(bytes,offset,length,WRITE_BLOCKING);}
 public synchronized int write(byte[] bytes,int offset,int length,int mode){
  if(handle==0)return ERROR_INVALID_OPERATION;
  if(bytes==null||offset<0||length<0||(long)offset+length>bytes.length||length>4*1024*1024||(mode!=0&&mode!=1))return ERROR_BAD_VALUE;
  return NativePlayback.write(handle,bytes,offset,length,mode==0,volume);
 }
 public int write(short[] samples,int offset,int length){
  if(samples==null||offset<0||length<0||(long)offset+length>samples.length||length>2*1024*1024)return ERROR_BAD_VALUE;
  byte[] bytes=new byte[length*2];for(int i=0;i<length;i++){bytes[2*i]=(byte)samples[offset+i];bytes[2*i+1]=(byte)(samples[offset+i]>>8);}int result=write(bytes,0,bytes.length);return result<0?result:result/2;
 }
 public int write(ByteBuffer bytes,int length,int mode){
  if(bytes==null||length<0||length>bytes.remaining()||length>4*1024*1024)return ERROR_BAD_VALUE;
  byte[] copy=new byte[length];bytes.duplicate().get(copy);int n=write(copy,0,length,mode);if(n>0)bytes.position(bytes.position()+n);return n;
 }
 public int write(ByteBuffer bytes,int length,int mode,long time){return write(bytes,length,mode);}
 public synchronized int getState(){return handle==0?STATE_UNINITIALIZED:STATE_INITIALIZED;}
 public int getPlayState(){return state;}public int getAudioSessionId(){return session;}public int getSampleRate(){return rate;}public int getChannelCount(){return channels;}
 public int getBufferSizeInFrames(){return buffer/(channels*2);}public int getBufferCapacityInFrames(){return getBufferSizeInFrames();}
 public int setBufferSizeInFrames(int frames){return frames<1?ERROR_BAD_VALUE:getBufferSizeInFrames();}
 public synchronized int getPlaybackHeadPosition(){return handle==0?0:(int)NativePlayback.position(handle);}
 public boolean getTimestamp(AudioTimestamp timestamp){if(timestamp==null)throw new NullPointerException("timestamp");return false;}
 public int getPlaybackRate(){return rate;}public int getUnderrunCount(){return 0;}
 public synchronized int setVolume(float gain){if(!Float.isFinite(gain))return ERROR_BAD_VALUE;volume=Math.max(0,Math.min(1,gain));return SUCCESS;}
 public int setStereoVolume(float left,float right){if(left!=right)throw new UnsupportedOperationException("Independent channel volume");return setVolume(left);}
 public boolean isOffloadedPlayback(){return false;}
 public static boolean isDirectPlaybackSupported(AudioFormat format,AudioAttributes attributes){return false;}
 public boolean setPreferredDevice(AudioDeviceInfo device){return device==null;}
 public AudioDeviceInfo getPreferredDevice(){return null;}public AudioDeviceInfo getRoutedDevice(){return state==PLAYSTATE_PLAYING?new AudioDeviceInfo():null;}
 public void addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener,android.os.Handler handler){if(listener==null)throw new IllegalArgumentException("listener");}
 public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener){}
 public PlaybackParams getPlaybackParams(){return new PlaybackParams();}
 public void setPlaybackParams(PlaybackParams params){if(params.getSpeed()!=1||params.getPitch()!=1)throw new UnsupportedOperationException("Playback speed and pitch changes");}
 public static final class Builder {
  private AudioAttributes attributes=new AudioAttributes.Builder().build();private AudioFormat format=new AudioFormat.Builder().setSampleRate(48000).setChannelMask(12).build();private int bytes=19200,mode=1,session;
  public Builder setAudioAttributes(AudioAttributes v){attributes=v;return this;}public Builder setAudioFormat(AudioFormat v){format=v;return this;}public Builder setBufferSizeInBytes(int v){bytes=v;return this;}public Builder setTransferMode(int v){mode=v;return this;}public Builder setSessionId(int v){session=v;return this;}
  public Builder setPerformanceMode(int v){return this;}public Builder setContext(android.content.Context context){return this;}
  public Builder setOffloadedPlayback(boolean v){if(v)throw new UnsupportedOperationException("Offloaded playback");return this;}
  public AudioTrack build(){return new AudioTrack(attributes,format,bytes,mode,session);}
 }
}
