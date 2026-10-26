// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeSpeech;
import java.io.*;
public final class SpeechPlaybackSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  final int rate=NativeSpeech.initialize(args[0]);short[] samples=NativeSpeech.synthesize("Turn right.","en",175,50);
  if(args[1].equals("complete")){
   long id=NativeSpeech.createPlayback();long start=System.nanoTime();try{check(NativeSpeech.play(id,samples,rate)==0,"complete output");}finally{NativeSpeech.releasePlayback(id);}check(System.nanoTime()-start>=samples.length*1000000000L/rate,"playback completion is paced to duration");
   try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[2]))){for(short sample:samples){out.write(sample);out.write(sample>>>8);}}
  }else if(args[1].equals("unavailable")){
   long id=NativeSpeech.createPlayback();try{check(NativeSpeech.play(id,samples,rate)==-1,"unavailable output must fail");}finally{NativeSpeech.releasePlayback(id);}
  }else{
   final short[] longPcm=new short[rate*10];for(int i=0;i<longPcm.length;i++)longPcm[i]=samples[i%samples.length];
   long canceled=NativeSpeech.createPlayback();NativeSpeech.cancelPlayback(canceled);check(NativeSpeech.play(canceled,samples,rate)==1,"pre-canceled output");NativeSpeech.releasePlayback(canceled);NativeSpeech.releasePlayback(canceled);
   for(int pass=0;pass<2;pass++){
    final long id=NativeSpeech.createPlayback();final int[] result={-99};final Throwable[] failure={null};Thread worker=new Thread(new Runnable(){public void run(){try{result[0]=NativeSpeech.play(id,longPcm,rate);}catch(Throwable t){failure[0]=t;}}});worker.start();long deadline=System.nanoTime()+2000000000L;while(!NativeSpeech.isPlaybackRunning(id)&&worker.isAlive()&&System.nanoTime()<deadline)Thread.sleep(1);check(NativeSpeech.isPlaybackRunning(id),"playback worker started");Thread.sleep(100);long start=System.nanoTime();if(pass==0)NativeSpeech.cancelPlayback(id);else NativeSpeech.releasePlayback(id);worker.join(2000);check(!worker.isAlive()&&failure[0]==null&&result[0]==1,"cancel/release active playback");check(System.nanoTime()-start<1500000000L,"bounded cancellation latency");NativeSpeech.releasePlayback(id);
   }
   long id=NativeSpeech.createPlayback();try{try{NativeSpeech.play(id,samples,0);throw new AssertionError("invalid sample rate accepted");}catch(IllegalArgumentException expected){}}finally{NativeSpeech.releasePlayback(id);}
   long[] ids=new long[64];for(int i=0;i<ids.length;i++)ids[i]=NativeSpeech.createPlayback();try{NativeSpeech.createPlayback();throw new AssertionError("handle budget bypassed");}catch(IllegalArgumentException expected){}finally{for(long value:ids)NativeSpeech.releasePlayback(value);}
  }
  System.out.println("SPEECH_PLAYBACK_OK "+args[1]);
 }
}
