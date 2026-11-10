// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativePlayback;
import java.nio.file.*;
import java.io.*;
/** Generated audio and silent device output only; no microphone recording. */
public final class DarwinPlaybackSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static int word(InputStream input)throws Exception{int a=input.read(),b=input.read(),c=input.read(),d=input.read();if((a|b|c|d)<0)throw new EOFException();return a|b<<8|c<<16|d<<24;}
 public static void main(String[] args)throws Exception{
  Path root=Paths.get("build/darwin-playback-fixture");int[] format=new int[2];byte[] pcm=NativePlayback.decodeFile(Files.readAllBytes(root.resolve("tone.wav")),format);check(format[0]==44100&&format[1]==1&&pcm.length==44100,"exact WAV format and frame count");
  byte[] aac=NativePlayback.decodeFile(Files.readAllBytes(root.resolve("tone.m4a")),format);check(format[0]==44100&&format[1]==1&&aac.length>=40000&&aac.length<60000,"system AAC file decode");
  long decoder=NativePlayback.decoderOpen(new byte[]{0x12,0x08});int[] decodedFormat=NativePlayback.decoderFormat(decoder);check(decodedFormat[0]==44100&&decodedFormat[1]==1,"AAC ASC configuration");long samples=0,energy=0;
  try(InputStream input=Files.newInputStream(root.resolve("packets.bin"))){check(input.read()=='A'&&input.read()=='A'&&input.read()=='C'&&input.read()=='1',"packet fixture");int count=word(input);check(count>0&&count<=256,"packet count bound");for(int i=0;i<count;i++){int length=word(input);check(length>0&&length<=65536,"packet byte bound");byte[] packet=new byte[length];new DataInputStream(input).readFully(packet);byte[] decoded=NativePlayback.decode(decoder,packet);check(decoded.length%2==0,"PCM16 packet alignment");for(int j=0;j<decoded.length;j+=2){int value=(short)((decoded[j]&255)|(decoded[j+1]<<8));energy+=(long)value*value;samples++;}}}
  finally{NativePlayback.close(decoder);}check(samples>=20000&&samples<40000&&Math.sqrt((double)energy/samples)>500,"actual AAC packets decode to the generated signal");
  long track=NativePlayback.trackOpen(48000,2,19200);try{check(NativePlayback.position(track)==0,"initial output position");NativePlayback.control(track,2);int written=NativePlayback.write(track,new byte[16384],0,16384,true,0);check(written==16384,"silent PCM enqueue");long until=System.nanoTime()+2000000000L;while(NativePlayback.position(track)==0&&System.nanoTime()<until)Thread.sleep(10);long position=NativePlayback.position(track);check(position>0&&position<=4096,"real AudioQueue playback position");long[] timestamp=NativePlayback.timestamp(track);if(timestamp!=null){check(timestamp[0]>=0&&timestamp[0]<=4096&&Math.abs(System.nanoTime()-timestamp[1])<1000000000L,"hardware presentation timestamp clock domain");System.out.println("DARWIN_PLAYBACK_TIMESTAMP_OK");}else System.out.println("DARWIN_PLAYBACK_TIMESTAMP_UNSUPPORTED");NativePlayback.control(track,1);long paused=NativePlayback.position(track);Thread.sleep(40);check(NativePlayback.position(track)==paused,"paused output position");NativePlayback.control(track,0);check(NativePlayback.position(track)==0,"reset output position");}finally{NativePlayback.close(track);}NativePlayback.close(track);
  boolean malformed=false;try{NativePlayback.decodeFile(new byte[8],new int[2]);}catch(IllegalStateException expected){malformed=true;}check(malformed,"malformed compressed audio rejected");
  System.out.println("DARWIN_PLAYBACK_PCM_AAC_OK");
 }
}
