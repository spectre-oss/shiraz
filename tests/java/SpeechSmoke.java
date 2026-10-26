// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeSpeech;
import java.io.*;
public final class SpeechSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void signal(short[] samples,int rate){check(samples.length>rate/10&&samples.length<rate*20,"speech duration");long energy=0;for(short v:samples)energy+=Math.abs((int)v);check(energy>samples.length*10L,"speech contains non-silent samples");}
 static void little(DataOutputStream out,int v,int bytes)throws IOException{for(int i=0;i<bytes;i++)out.write(v>>>(8*i));}
 public static void main(String[] args)throws Exception{
  try{NativeSpeech.synthesize("Turn right.","en",175,50);throw new AssertionError("uninitialized synthesis accepted");}catch(IllegalStateException expected){}
  try{NativeSpeech.initialize(args[0]+"/missing-speech-fixture");throw new AssertionError("missing speech data accepted");}catch(IllegalStateException expected){}
  final int rate=NativeSpeech.initialize(args[0]);check(rate>=8000&&rate<=96000&&NativeSpeech.initialize(args[0])==rate,"stable sample rate");
  String[] catalog=NativeSpeech.installedVoices();check(catalog.length>0&&catalog.length%2==0,"installed catalog pairs");String selected=null;java.util.HashSet<String> identifiers=new java.util.HashSet<String>();
  for(int i=0;i<catalog.length;i+=2){check(identifiers.add(catalog[i])&&!catalog[i].startsWith("mb/"),"unique local voice");check(!java.util.Locale.forLanguageTag(catalog[i+1]).getLanguage().isEmpty(),"voice locale");if(catalog[i+1].equals("de"))selected=catalog[i];}
  check(selected!=null,"German catalog entry");signal(NativeSpeech.synthesize("Weiter geradeaus.",selected,175,50),rate);
  try{NativeSpeech.synthesize("test","not-installed/en",175,50);throw new AssertionError("unlisted voice path accepted");}catch(IllegalArgumentException expected){}
  short[] english=NativeSpeech.synthesize("After two hundred meters, turn right.","en",175,50);signal(english,rate);
  short[] fast=NativeSpeech.synthesize("After two hundred meters, turn right.","en",300,50);signal(fast,rate);check(fast.length<english.length,"speech rate changes duration");
  signal(NativeSpeech.synthesize("Nächste Straße rechts.","de",175,50),rate);
  try{NativeSpeech.synthesize("test","zzzz-invalid",175,50);throw new AssertionError("unavailable voice accepted");}catch(IllegalArgumentException expected){}
  try{NativeSpeech.synthesize("test","../en",175,50);throw new AssertionError("voice path accepted");}catch(IllegalArgumentException expected){}
  try{NativeSpeech.synthesize("test","en",0,50);throw new AssertionError("invalid rate accepted");}catch(IllegalArgumentException expected){}
  try{NativeSpeech.synthesize("test","en",175,100);throw new AssertionError("invalid pitch accepted");}catch(IllegalArgumentException expected){}
  try{NativeSpeech.synthesize(new String(new char[4097]),"en",175,50);throw new AssertionError("text bound accepted");}catch(IllegalArgumentException expected){}
  final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  Thread a=new Thread(new Runnable(){public void run(){try{signal(NativeSpeech.synthesize("Continue straight.","en",175,40),rate);}catch(Throwable t){failure.set(t);}}});
  Thread b=new Thread(new Runnable(){public void run(){try{signal(NativeSpeech.synthesize("Weiter geradeaus.","de",175,65),rate);}catch(Throwable t){failure.set(t);}}});a.start();b.start();a.join();b.join();if(failure.get()!=null)throw new AssertionError(failure.get());
  try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]))){out.writeBytes("RIFF");little(out,36+english.length*2,4);out.writeBytes("WAVEfmt ");little(out,16,4);little(out,1,2);little(out,1,2);little(out,rate,4);little(out,rate*2,4);little(out,2,2);little(out,16,2);out.writeBytes("data");little(out,english.length*2,4);for(short v:english)little(out,v,2);}
  System.out.println("SPEECH_PCM_OK rate="+rate+" samples="+english.length);
 }
}
