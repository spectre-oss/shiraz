// SPDX-License-Identifier: GPL-3.0-only
import android.media.*;
import java.nio.*;
public final class AudioPlaybackSmoke {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static void lowRate()throws Exception{
  MediaCodec e=MediaCodec.createEncoderByType("audio/mp4a-latm"),d=MediaCodec.createDecoderByType("audio/mp4a-latm");
  MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",22050,1);e.configure(format,null,null,1);e.start();d.configure(e.getOutputFormat(),null,null,0);d.start();
  MediaCodec.BufferInfo encoded=new MediaCodec.BufferInfo(),decoded=new MediaCodec.BufferInfo();long frames=0;int outputs=0;
  for(int block=0;block<24;block++){
   int in=e.dequeueInputBuffer(0);ByteBuffer b=e.getInputBuffer(in).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<1024;i++)b.putShort((short)(10000*Math.sin((block*1024+i)*2*Math.PI*440/22050)));
   e.queueInputBuffer(in,0,2048,block*1024L*1000000/22050,0);
   int ei;while((ei=e.dequeueOutputBuffer(encoded,0))!=-1){if(ei<0)continue;int di=d.dequeueInputBuffer(0);d.getInputBuffer(di).put(e.getOutputBuffer(ei));d.queueInputBuffer(di,0,encoded.size,encoded.presentationTimeUs,0);e.releaseOutputBuffer(ei,false);
    int out;while((out=d.dequeueOutputBuffer(decoded,0))!=-1){if(out<0)continue;MediaFormat actual=d.getOutputFormat();long duration=decoded.size*1000000L/(2*actual.getInteger("channel-count")*actual.getInteger("sample-rate"));check(Math.abs(duration-1024L*1000000/22050)<2,"Low-rate decoder PCM duration agrees with timestamps");frames+=decoded.size;outputs++;d.releaseOutputBuffer(out,false);}
   }
  }
  check(outputs>20&&frames>0,"Low-rate decoder produces PCM");System.out.println("LOW_RATE_FORMAT_OK rate="+d.getOutputFormat().getInteger("sample-rate"));d.release();e.release();
 }
 static void callbackConcurrency()throws Exception{
  final MediaCodec codec=MediaCodec.createEncoderByType("audio/mp4a-latm");
  codec.configure(MediaFormat.createAudioFormat("audio/mp4a-latm",44100,1),null,null,1);
  android.os.HandlerThread handler=new android.os.HandlerThread("codec-callback-test");handler.start();
  final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  codec.setCallback(new MediaCodec.Callback(){
   public void onOutputFormatChanged(MediaCodec c,MediaFormat f){
    Thread client=new Thread(new Runnable(){public void run(){codec.flush();}},"codec-client-flush");client.setDaemon(true);client.start();
    try{client.join(1000);if(client.isAlive())failure.set(new AssertionError("Concurrent flush blocked by client callback"));}
    catch(InterruptedException e){failure.set(e);}finally{done.countDown();}
   }
   public void onInputBufferAvailable(MediaCodec c,int i){failure.compareAndSet(null,new AssertionError("Stale input callback after flush"));}
   public void onOutputBufferAvailable(MediaCodec c,int i,MediaCodec.BufferInfo info){}
   public void onError(MediaCodec c,MediaCodec.CodecException e){failure.set(e);done.countDown();}
  },new android.os.Handler(handler.getLooper()));
  try{codec.start();check(done.await(3,java.util.concurrent.TimeUnit.SECONDS),"Callback timeout");handler.quitSafely();handler.join(2000);check(!handler.isAlive(),"Callback handler exits");if(failure.get()!=null)throw new AssertionError("Codec callback concurrency",failure.get());}
  finally{handler.quit();codec.release();}
  System.out.println("CODEC_CALLBACK_CONCURRENCY_OK");
 }
 public static void main(String[] args)throws Exception{
  callbackConcurrency();
  MediaFormat video=MediaFormat.createVideoFormat("video/avc",1920,1080);check("video/avc".equals(video.getString(MediaFormat.KEY_MIME))&&video.getInteger(MediaFormat.KEY_WIDTH)==1920&&video.getInteger(MediaFormat.KEY_HEIGHT)==1080,"video format JNI factory contract");
  MediaCodec encoder=MediaCodec.createEncoderByType("audio/mp4a-latm");
  MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,1);f.setInteger("bitrate",64000);encoder.configure(f,null,null,1);encoder.start();
  MediaCodec decoder=MediaCodec.createByCodecName(new MediaCodecList(0).getCodecInfos()[0].getName());decoder.configure(encoder.getOutputFormat(),null,null,0);decoder.start();
  MediaFormat outputFormat=decoder.getOutputFormat();
  AudioTrack track=new AudioTrack(new AudioAttributes.Builder().build(),new AudioFormat.Builder().setSampleRate(outputFormat.getInteger("sample-rate")).setChannelMask(outputFormat.getInteger("channel-count")==1?4:12).build(),8192,AudioTrack.MODE_STREAM,0);track.play();
  long energy=0;int decoded=0,packets=0;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo(),out=new MediaCodec.BufferInfo();
  for(int block=0;block<32;block++){
   int input=encoder.dequeueInputBuffer(0);ByteBuffer pcm=encoder.getInputBuffer(input);pcm.order(ByteOrder.LITTLE_ENDIAN);
   for(int i=0;i<1024;i++)pcm.putShort((short)(12000*Math.sin((block*1024+i)*2*Math.PI*440/44100)));
   encoder.queueInputBuffer(input,0,2048,block*1024L*1000000/44100,0);
   int index;while((index=encoder.dequeueOutputBuffer(info,0))!=-1){if(index<0)continue;
    ByteBuffer encoded=encoder.getOutputBuffer(index);int target=decoder.dequeueInputBuffer(0);check(target>=0,"decoder input ownership");decoder.getInputBuffer(target).put(encoded);decoder.queueInputBuffer(target,0,info.size,info.presentationTimeUs,0);packets++;encoder.releaseOutputBuffer(index,false);
    int output;while((output=decoder.dequeueOutputBuffer(out,0))!=-1){if(output<0)continue;long duration=out.size*1000000L/(2*outputFormat.getInteger("channel-count")*outputFormat.getInteger("sample-rate"));check(Math.abs(duration-1024L*1000000/44100)<2,"Decoded duration matches AAC timestamp spacing");ByteBuffer samples=decoder.getOutputBuffer(output);check(samples.order()==ByteOrder.nativeOrder(),"codec output byte order");ByteBuffer copy=samples.duplicate().order(ByteOrder.LITTLE_ENDIAN);while(copy.remaining()>=2){long sample=copy.getShort();energy+=sample*sample;decoded++;}
     int n=track.write(samples,out.size,AudioTrack.WRITE_BLOCKING);check(n==out.size,"PCM output write");decoder.releaseOutputBuffer(output,false);
    }
   }
  }
  check(packets==32&&decoded>28000&&energy/decoded>1000000,"AAC decode must preserve non-silent test signal");
  check(track.getState()==1&&track.getPlayState()==3&&track.getPlaybackHeadPosition()>0,"streaming playback position");
  track.pause();track.flush();check(track.getPlaybackHeadPosition()==0,"flush resets playback position");track.release();check(track.write(new byte[4],0,4)==-3,"released track rejects writes");
  decoder.flush();check(decoder.dequeueInputBuffer(0)==0,"decoder reusable after flush");decoder.release();encoder.release();
  check(Rating.newStarRating(5,6)==null&&Rating.newPercentageRating(50).getPercentRating()==50,"rating validation");
  lowRate();
  android.os.Looper.prepareMainLooper();android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "test";}};
  android.media.session.MediaSession session=new android.media.session.MediaSession(context,"test");session.setCallback(new android.media.session.MediaSession.Callback(){});java.lang.reflect.Field callbackField=android.media.session.MediaSession.class.getDeclaredField("mCallback");callbackField.setAccessible(true);check(callbackField.get(session) instanceof android.os.Handler,"AndroidX callback handler reflection");session.setCallback(null);check(callbackField.get(session)==null,"Callback handler cleared");session.setRatingType(5);check(session.getController().getRatingType()==5,"controller rating type");
  MediaMetadata.Builder builder=new MediaMetadata.Builder().putText(MediaMetadata.METADATA_KEY_TITLE,"Recording").putLong(MediaMetadata.METADATA_KEY_DURATION,1234);MediaMetadata metadata=builder.build();builder.putText(MediaMetadata.METADATA_KEY_TITLE,"changed");session.setMetadata(metadata);check("Recording".equals(session.getController().getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE)),"metadata snapshot");session.release();
  android.os.Bundle bound=new android.os.Bundle();android.os.Binder binder=new android.os.Binder();bound.putBinder("session",binder);check(bound.shirazBroadcastCopy().getBinder("session")==binder,"local binder extras retain identity");
  System.out.println("AUDIO_PLAYBACK_OK samples="+decoded+" energy="+energy/decoded);
 }
}
