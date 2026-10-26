// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import shiraz.runtime.CapturePermissions;
import shiraz.runtime.NativeCapture;
/** PCM16 host microphone, gated on explicit per-app foreground consent. */
public class AudioRecord {
 public static final int STATE_UNINITIALIZED=0,STATE_INITIALIZED=1,RECORDSTATE_STOPPED=1,RECORDSTATE_RECORDING=3;
 public static final int SUCCESS=0,ERROR=-1,ERROR_BAD_VALUE=-2,ERROR_INVALID_OPERATION=-3,ERROR_DEAD_OBJECT=-6,READ_BLOCKING=0,READ_NON_BLOCKING=1;
 private final int source,rate,channels,bufferSize;private final AudioFormat format;
 private volatile long handle;private long lease;private volatile boolean released;private final CapturePermissions permissions;
 public AudioRecord(int source,int rate,int channelConfig,int encoding,int bufferSize){
  if(source<0||source>10||getMinBufferSize(rate,channelConfig,encoding)<0||bufferSize<2||bufferSize>4*1024*1024)throw new IllegalArgumentException("AudioRecord configuration");
  this.source=source;this.rate=rate;channels=channelConfig==AudioFormat.CHANNEL_IN_MONO?1:2;this.bufferSize=bufferSize;
  format=new AudioFormat.Builder().setSampleRate(rate).setChannelMask(channelConfig).setEncoding(encoding).build();
  permissions=CapturePermissions.current();if(permissions==null)throw new SecurityException("Microphone consent is unavailable");permissions.require(CapturePermissions.MICROPHONE);
 }
 public static int getMinBufferSize(int rate,int config,int encoding){if(rate<8000||rate>96000||(config!=16&&config!=12)||encoding!=2)return ERROR_BAD_VALUE;return Math.max(1024,rate/10*(config==16?1:2)*2);}
 public synchronized void startRecording(){if(released)throw new IllegalStateException("AudioRecord released");if(handle!=0)return;permissions.require(CapturePermissions.MICROPHONE);long opened=0;try{opened=NativeCapture.microphoneOpen(rate,channels);handle=opened;lease=permissions.begin(CapturePermissions.MICROPHONE,new Runnable(){public void run(){stop();}});}catch(RuntimeException e){if(opened!=0)NativeCapture.close(opened);handle=0;permissions.failure(CapturePermissions.MICROPHONE);throw e;}}
 public void stop(){long old,id;synchronized(this){old=handle;handle=0;id=lease;lease=0;}try{if(old!=0)NativeCapture.close(old);}finally{if(id!=0)permissions.end(id);}}
 public void release(){stop();synchronized(this){released=true;}}
 public int read(byte[] bytes,int offset,int size){if(bytes==null||offset<0||size<0||(long)offset+size>bytes.length)return ERROR_BAD_VALUE;long id=handle;if(id==0)return ERROR_INVALID_OPERATION;if(!permissions.granted(CapturePermissions.MICROPHONE)){stop();return ERROR_INVALID_OPERATION;}try{return NativeCapture.microphoneRead(id,bytes,offset,size-size%(channels*2));}catch(SecurityException revoked){stop();return ERROR_INVALID_OPERATION;}catch(IllegalStateException failure){if(handle==0)return ERROR_INVALID_OPERATION;permissions.failure(CapturePermissions.MICROPHONE);stop();return ERROR_DEAD_OBJECT;}}
 public int read(byte[] bytes,int offset,int size,int mode){if(mode!=0)throw new UnsupportedOperationException("Nonblocking capture read");return read(bytes,offset,size);}
 public int read(short[] samples,int offset,int size){if(samples==null||offset<0||size<0||(long)offset+size>samples.length||size>2*1024*1024)return ERROR_BAD_VALUE;byte[] bytes=new byte[size*2];int n=read(bytes,0,bytes.length);if(n<0)return n;for(int i=0;i<n/2;i++)samples[offset+i]=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8));return n/2;}
 public int read(java.nio.ByteBuffer buffer,int size){if(buffer==null||!buffer.isDirect()||size<0||size>buffer.capacity()||size>4*1024*1024)return ERROR_BAD_VALUE;byte[] bytes=new byte[size];int n=read(bytes,0,size);if(n>0){java.nio.ByteBuffer copy=buffer.duplicate();copy.clear();copy.put(bytes,0,n);}return n;}
 public int getState(){return released?0:1;}public int getRecordingState(){return handle!=0?3:1;}public int getAudioSessionId(){return 1;}public int getAudioSource(){return source;}public int getSampleRate(){return rate;}public int getChannelCount(){return channels;}public int getAudioFormat(){return 2;}public int getBufferSizeInFrames(){return bufferSize/(channels*2);}public AudioFormat getFormat(){return format;}
 public static final class Builder {
  private int source,bufferSize=8192;private AudioFormat format=new AudioFormat.Builder().setSampleRate(44100).setChannelMask(16).build();
  public Builder setAudioSource(int value){source=value;return this;}public Builder setBufferSizeInBytes(int value){bufferSize=value;return this;}public Builder setAudioFormat(AudioFormat value){if(value==null)throw new NullPointerException("format");format=value;return this;}
  public AudioRecord build(){return new AudioRecord(source,format.getSampleRate(),format.getChannelMask(),format.getEncoding(),bufferSize);}
 }
}
