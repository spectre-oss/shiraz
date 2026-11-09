// SPDX-License-Identifier: GPL-3.0-only
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import shiraz.runtime.NativeCapture;
import shiraz.runtime.NativePlayback;
/** Synthetic audio only: does not request microphone access or save recordings. */
public final class DarwinAacEncodeSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static byte[] tone(int rate,int channels,int start){byte[] pcm=new byte[1024*channels*2];for(int i=0;i<1024;i++){int value=(int)(12000*Math.sin(2*Math.PI*440*(start+i)/rate));for(int c=0;c<channels;c++){int at=(i*channels+c)*2;pcm[at]=(byte)value;pcm[at+1]=(byte)(value>>8);}}return pcm;}
 private static void roundtrip(int rate,int channels,int index){long encoder=NativeCapture.aacOpen(rate,channels,channels*64000),decoder=0;try{decoder=NativePlayback.decoderOpen(new byte[]{(byte)(16|(index>>1)),(byte)((index&1)<<7|channels<<3)});long energy=0,samples=0;for(int frame=0;frame<12;frame++){byte[] packet=NativeCapture.aacEncode(encoder,tone(rate,channels,frame*1024));check(packet.length>0&&packet.length<8192,"bounded AAC packet");byte[] pcm=NativePlayback.decode(decoder,packet);for(int i=0;i+1<pcm.length;i+=2){int value=(short)((pcm[i]&255)|(pcm[i+1]<<8));energy+=(long)value*value;samples++;}}check(samples>=10*1024*channels&&samples<=14*1024*channels,"decoded AAC frame count");check(energy/samples>1000000,"decoded AAC signal");boolean malformed=false;try{NativeCapture.aacEncode(encoder,new byte[3]);}catch(IllegalArgumentException expected){malformed=true;}check(malformed,"partial native frame rejected");}finally{NativeCapture.close(encoder);if(decoder!=0)NativePlayback.close(decoder);}boolean closed=false;try{NativeCapture.aacEncode(encoder,new byte[2048]);}catch(IllegalStateException expected){closed=true;}check(closed,"closed encoder rejected");}
 // CoreAudio file decoding trims codec priming; raw packet tests above retain it.
 private static void container()throws Exception{
  File file=File.createTempFile("shiraz-synthetic-aac-",".m4a");long encoder=0;MediaMuxer muxer=null;try{encoder=NativeCapture.aacOpen(44100,1,64000);muxer=new MediaMuxer(file.getPath(),0);MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,1);format.setByteBuffer("csd-0",ByteBuffer.wrap(new byte[]{0x12,0x08}));muxer.addTrack(format);muxer.start();for(int i=0;i<12;i++){byte[] packet=NativeCapture.aacEncode(encoder,tone(44100,1,i*1024));MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();info.set(0,packet.length,i*1024*1000000L/44100,0);muxer.writeSampleData(0,ByteBuffer.wrap(packet),info);}muxer.stop();muxer.release();muxer=null;int[] actual=new int[2];byte[] pcm=NativePlayback.decodeFile(Files.readAllBytes(file.toPath()),actual);check(actual[0]==44100&&actual[1]==1&&pcm.length>=9*2048&&pcm.length<=12*2048,"AAC MPEG-4 container roundtrip: "+actual[0]+"/"+actual[1]+" bytes="+pcm.length);}finally{if(muxer!=null)muxer.release();if(encoder!=0)NativeCapture.close(encoder);check(file.delete(),"synthetic recording cleanup");}
 }
 public static void main(String[] args)throws Exception{
  container();roundtrip(44100,1,4);roundtrip(48000,2,3);roundtrip(16000,1,8);
  MediaCodec codec=MediaCodec.createEncoderByType("audio/mp4a-latm");try{MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,1);format.setInteger("bitrate",64000);codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();int input=codec.dequeueInputBuffer(0);ByteBuffer buffer=codec.getInputBuffer(input);buffer.put(tone(44100,1,0),0,1500);codec.queueInputBuffer(input,0,1500,123456,MediaCodec.BUFFER_FLAG_END_OF_STREAM);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();check(codec.dequeueOutputBuffer(info,0)==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,"AAC output format");check(codec.getOutputFormat().getByteBuffer("csd-0").remaining()==2,"AAC configuration");int output=codec.dequeueOutputBuffer(info,0);check(output>=0&&info.size>0&&info.presentationTimeUs==123456,"padded AAC packet timestamp");codec.releaseOutputBuffer(output,false);output=codec.dequeueOutputBuffer(info,0);check(output>=0&&(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0&&info.presentationTimeUs==123456+1024*1000000L/44100,"AAC end of stream");codec.releaseOutputBuffer(output,false);}finally{codec.release();}
  System.out.println("DARWIN_AAC_ENCODER_ROUNDTRIP_OK");
 }
}
