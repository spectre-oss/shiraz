// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.io.*;
import android.os.Handler;
import android.os.Looper;
import shiraz.runtime.NativeCapture;
/** Foreground-consent audio recording through the host PCM/AAC bridges. */
public class MediaRecorder {
 public static final int MEDIA_RECORDER_ERROR_UNKNOWN=1,MEDIA_RECORDER_INFO_MAX_DURATION_REACHED=800,MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED=801;
 public static final class AudioSource {public static final int DEFAULT=0,MIC=1,VOICE_RECOGNITION=6,VOICE_COMMUNICATION=7;private AudioSource(){}}
 public static final class AudioEncoder {public static final int DEFAULT=0,AMR_NB=1,AMR_WB=2,AAC=3;private AudioEncoder(){}}
 public static final class OutputFormat {public static final int DEFAULT=0,THREE_GPP=1,MPEG_4=2,AAC_ADTS=6;private OutputFormat(){}}
 public static final class VideoSource {public static final int DEFAULT=0,CAMERA=1,SURFACE=2;private VideoSource(){}}
 public static final class VideoEncoder {public static final int DEFAULT=0,H263=1,H264=2;private VideoEncoder(){}}
 public interface OnErrorListener {void onError(MediaRecorder recorder,int what,int extra);}
 public interface OnInfoListener {void onInfo(MediaRecorder recorder,int what,int extra);}
 private final Handler handler;
 private int source=-1,format=-1,encoder=-1,rate=44100,channels=1,bitrate=64000,maxDuration;
 private long maxFileSize=256L*1024*1024;private String path;private boolean prepared,hasRun;private volatile boolean released;private volatile boolean recording;
 private volatile int amplitude;private volatile RuntimeException failure;private volatile long packets;private Thread worker;private AudioRecord audio;
 private OnErrorListener errors;private OnInfoListener information;
 public MediaRecorder(){Looper looper=Looper.myLooper();if(looper==null)looper=Looper.getMainLooper();handler=looper==null?null:new Handler(looper);}
 private void configurable(){if(released||prepared||hasRun)throw new IllegalStateException("Recorder is not configurable");}
 public synchronized void setAudioSource(int value){configurable();if(value<0||value>10)throw new IllegalArgumentException("Audio source");source=value;}
 public synchronized void setOutputFormat(int value){configurable();if(value!=OutputFormat.MPEG_4&&value!=OutputFormat.AAC_ADTS)throw new UnsupportedOperationException("Only AAC MPEG-4 and ADTS recording are supported");format=value;}
 public synchronized void setAudioEncoder(int value){configurable();if(value!=AudioEncoder.AAC&&value!=AudioEncoder.DEFAULT)throw new UnsupportedOperationException("Only AAC-LC recording is supported");encoder=value;}
 public synchronized void setAudioSamplingRate(int value){configurable();int[] supported={8000,11025,12000,16000,22050,24000,32000,44100,48000};boolean found=false;for(int n:supported)if(n==value)found=true;if(!found)throw new IllegalArgumentException("AAC sample rate");rate=value;}
 public synchronized void setAudioChannels(int value){configurable();if(value<1||value>2)throw new IllegalArgumentException("Audio channels");channels=value;}
 public synchronized void setAudioEncodingBitRate(int value){configurable();if(value<8000||value>320000)throw new IllegalArgumentException("AAC bit rate");bitrate=value;}
 public synchronized void setOutputFile(String value){configurable();if(value==null||value.length()==0)throw new IllegalArgumentException("Output path");path=value;}
 public void setOutputFile(File file){setOutputFile(file==null?null:file.getPath());}
 public void setOutputFile(FileDescriptor descriptor){throw new UnsupportedOperationException("Recorder file descriptor output unavailable");}
 public synchronized void setMaxDuration(int milliseconds){configurable();if(milliseconds<0)throw new IllegalArgumentException("Duration");maxDuration=milliseconds;}
 public synchronized void setMaxFileSize(long bytes){configurable();if(bytes<0)throw new IllegalArgumentException("File size");maxFileSize=bytes==0?256L*1024*1024:Math.min(bytes,256L*1024*1024);}
 public synchronized void setOnErrorListener(OnErrorListener listener){errors=listener;}public synchronized void setOnInfoListener(OnInfoListener listener){information=listener;}
 public void setProfile(CamcorderProfile profile){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setVideoSource(int source){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setVideoEncoder(int encoder){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setVideoSize(int width,int height){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setVideoFrameRate(int rate){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setVideoEncodingBitRate(int rate){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setCamera(android.hardware.Camera camera){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setPreviewDisplay(android.view.Surface surface){throw new UnsupportedOperationException("Host video recording is not implemented");}
 public void setOrientationHint(int degrees){if(degrees!=0&&degrees!=90&&degrees!=180&&degrees!=270)throw new IllegalArgumentException("Orientation");}
 public synchronized void prepare()throws IOException{configurable();if(source<0||format<0||encoder<0||path==null)throw new IllegalStateException("Incomplete audio recording configuration");File parent=new File(path).getAbsoluteFile().getParentFile();if(parent==null||!parent.isDirectory())throw new IOException("Recording output directory unavailable");prepared=true;}
 public synchronized void start(){if(released||!prepared||hasRun)throw new IllegalStateException("Recorder is not prepared");
  AudioRecord opened=new AudioRecord(source,rate,channels==1?AudioFormat.CHANNEL_IN_MONO:AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT,Math.max(8192,AudioRecord.getMinBufferSize(rate,channels==1?16:12,2)));
  try{opened.startRecording();}catch(RuntimeException e){opened.release();throw e;}audio=opened;recording=true;hasRun=true;packets=0;failure=null;worker=new Thread(new Runnable(){public void run(){capture();}},"Shiraz audio recorder");worker.start();
 }
 private void notifyInfo(final int code){final OnInfoListener target; synchronized(this){target=information;}if(target!=null&&handler!=null)handler.post(new Runnable(){public void run(){if(!released)target.onInfo(MediaRecorder.this,code,0);}});}
 private void capture(){long codec=0;MediaMuxer muxer=null;OutputStream adts=null;boolean muxerStarted=false;int reason=0;long total=0;try{
  codec=NativeCapture.aacOpen(rate,channels,bitrate);MediaFormat config=MediaFormat.createAudioFormat("audio/mp4a-latm",rate,channels);int index=0;int[] rates={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};while(rates[index]!=rate)index++;config.setByteBuffer("csd-0",java.nio.ByteBuffer.wrap(new byte[]{(byte)(16|(index>>1)),(byte)((index&1)<<7|channels<<3)}));
  if(format==OutputFormat.MPEG_4){muxer=new MediaMuxer(path,0);muxer.addTrack(config);muxer.start();muxerStarted=true;}else adts=new BufferedOutputStream(new FileOutputStream(path));
  byte[] pcm=new byte[1024*channels*2];int filled=0;while(recording){int n=audio.read(pcm,filled,pcm.length-filled);if(n<0){if(recording)throw new IllegalStateException("Microphone recording stopped: "+n);break;}if(n==0)continue;filled+=n;if(filled<pcm.length)continue;filled=0;int peak=0;for(int i=0;i<pcm.length;i+=2)peak=Math.max(peak,Math.abs((int)(short)((pcm[i]&255)|(pcm[i+1]<<8))));amplitude=Math.max(amplitude,peak);byte[] packet=NativeCapture.aacEncode(codec,pcm);long next=total+packet.length+(adts==null?0:7);long containerReserve=muxer==null?0:16384+(packets+1)*4;if(next+containerReserve>maxFileSize){reason=MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED;break;}
   if(muxer!=null){MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();info.set(0,packet.length,packets*1024*1000000L/rate,0);muxer.writeSampleData(0,java.nio.ByteBuffer.wrap(packet),info);}else{int length=packet.length+7;adts.write(new byte[]{(byte)255,(byte)241,(byte)(64|(index<<2)|(channels>>2)),(byte)((channels&3)<<6|length>>11),(byte)(length>>3),(byte)((length&7)<<5|31),(byte)252});adts.write(packet);}total=next;packets++;
   if((maxDuration>0&&packets*1024*1000L/rate>=maxDuration)||packets*1024L>=rate*3600L){reason=MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;break;}
  }
 }catch(Exception e){failure=e instanceof RuntimeException?(RuntimeException)e:new IllegalStateException("Recording failed",e);}finally{recording=false;try{audio.release();}catch(RuntimeException e){failure=e;}try{if(codec!=0)NativeCapture.close(codec);}catch(RuntimeException e){failure=e;}try{if(muxerStarted)muxer.stop();}catch(RuntimeException e){failure=e;}finally{try{if(muxer!=null)muxer.release();}catch(RuntimeException e){failure=e;}try{if(adts!=null)adts.close();}catch(IOException e){failure=new IllegalStateException(e);}}}
  if(failure!=null){final OnErrorListener target;synchronized(this){target=errors;}if(target!=null&&handler!=null)handler.post(new Runnable(){public void run(){if(!released)target.onError(MediaRecorder.this,MEDIA_RECORDER_ERROR_UNKNOWN,0);}});}else if(reason!=0)notifyInfo(reason);
 }
 public void stop(){Thread thread;AudioRecord input;synchronized(this){if(!hasRun||worker==null)throw new IllegalStateException("Recorder not started");recording=false;thread=worker;input=audio;}input.stop();try{thread.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted stopping recorder",e);}if(thread.isAlive())throw new IllegalStateException("Recorder stop timed out");synchronized(this){worker=null;prepared=false;}if(failure!=null)throw failure;if(packets==0)throw new RuntimeException("No audio recorded");}
 public int getMaxAmplitude(){if(released)throw new IllegalStateException("Recorder released");int value=amplitude;amplitude=0;return value;}
 public void reset(){if(worker!=null){try{stop();}catch(RuntimeException ignored){if(worker!=null)throw ignored;}}synchronized(this){if(released)throw new IllegalStateException("Recorder released");prepared=false;hasRun=false;source=format=encoder=-1;path=null;rate=44100;channels=1;bitrate=64000;maxDuration=0;maxFileSize=256L*1024*1024;amplitude=0;failure=null;}}
 public void release(){if(released)return;if(worker!=null){try{stop();}catch(RuntimeException failure){if(worker!=null)throw failure;}}synchronized(this){released=true;prepared=false;}}
}
