// SPDX-License-Identifier: GPL-3.0-only
import android.speech.tts.*;
import android.os.*;
import java.util.*;
public final class TextToSpeechSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final List<String> events=Collections.synchronizedList(new ArrayList<String>());
 static void await(String marker)throws Exception{long end=System.nanoTime()+10000000000L;while(!events.contains(marker)&&System.nanoTime()<end){Looper.myLooper().shirazDispatchDue(256);Thread.sleep(2);}check(events.contains(marker),"missing "+marker+" in "+events);}
 public static void main(String[] args)throws Exception{
  System.setProperty("shiraz.speechData",args[0]);Looper.prepareMainLooper();
  final TextToSpeech tts=new TextToSpeech(new android.content.ContextWrapper(null),new TextToSpeech.OnInitListener(){public void onInit(int status){events.add("init:"+status);}});
  try{
   await("init:0");check(tts.getDefaultVoice()!=null&&!tts.getVoices().isEmpty(),"installed voices");check(tts.isLanguageAvailable(Locale.GERMAN)>=0,"German installed");check(tts.isLanguageAvailable(new Locale("zz"))==TextToSpeech.LANG_NOT_SUPPORTED,"unknown language");
   Locale engineDefault=tts.getDefaultLanguage();check(engineDefault.equals(tts.getDefaultVoice().getLocale()),"default language matches engine voice");
   check(tts.setLanguage(Locale.GERMAN)>=0&&engineDefault.equals(tts.getDefaultLanguage()),"selection does not change engine default");
   Set<String> features=tts.getFeatures(Locale.GERMAN);check(features.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)&&!features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS),"installed voice is offline");features.clear();check(!tts.getFeatures(Locale.GERMAN).isEmpty(),"feature snapshot isolated");check(tts.getFeatures(new Locale("zz"))==null,"unsupported locale has no features");
   check(tts.setLanguage(Locale.ENGLISH)>=0,"English selected");check(!tts.getVoice().isNetworkConnectionRequired(),"offline voice");check(tts.setVoice(new Voice("invalid",Locale.ENGLISH,200,300,false,Collections.<String>emptySet()))==TextToSpeech.ERROR,"invalid voice rejected");
   check(tts.setSpeechRate(0)==-1&&tts.setSpeechRate(Float.NaN)==-1&&tts.setPitch(3)==-1,"unsupported parameters");check(tts.speak((String)null,1,null)==-1,"null speech rejected");
   Bundle volume=new Bundle();volume.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,0.5f);check(tts.speak("quiet",1,volume,"quiet")==-1,"unsupported volume rejected");
   tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){events.add("start:"+id);}public void onDone(String id){events.add("done:"+id);}public void onError(String id){events.add("error:"+id);}public void onError(String id,int code){events.add("error:"+id+":"+code);}public void onStop(String id,boolean active){events.add("stop:"+id+":"+active);}});
   tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener(){public void onUtteranceCompleted(String id){events.add("legacy:"+id);}});
   check(tts.speak("Turn right.",1,new Bundle(),"one")==0,"speech accepted");
   if(args[1].equals("unavailable")){await("error:one:-5");check(!events.contains("done:one")&&!events.contains("legacy:one"),"output failure must not complete");}
   else{
    check(tts.playSilentUtterance(40,1,"pause")==0,"silence queued");check(tts.speak("Continue.",1,new Bundle(),"two")==0,"second speech queued");await("legacy:two");check(events.indexOf("done:one")<events.indexOf("start:pause")&&events.indexOf("done:pause")<events.indexOf("start:two"),"serial queue completion");
    tts.playSilentUtterance(10000,1,"long");await("start:long");tts.playSilentUtterance(10000,1,"queued");check(tts.isSpeaking(),"active silence");tts.playSilentUtterance(0,0,"flush");await("done:flush");await("stop:long:true");await("stop:queued:false");check(!events.contains("done:long")&&!events.contains("legacy:queued"),"flush does not complete canceled work");
    tts.speak("Turn right. Turn right. Turn right.",1,new Bundle(),"cancel");await("start:cancel");tts.stop();await("stop:cancel:true");check(!events.contains("done:cancel"),"speech stop");
   }
   tts.shutdown();check(tts.speak("closed",1,new Bundle(),"closed")==-1,"shutdown rejects work");tts.shutdown();
   final TextToSpeech missing=new TextToSpeech(new android.content.ContextWrapper(null),new TextToSpeech.OnInitListener(){public void onInit(int status){events.add("missing:"+status);}},"not-installed");await("missing:-1");check(missing.getDefaultLanguage()==null&&missing.getFeatures(Locale.ENGLISH)==null,"failed engine has no default/features");check(missing.speak("invalid",1,new Bundle(),"invalid")==-1,"failed init rejects speech");missing.shutdown();
  }finally{tts.shutdown();TextToSpeech.shirazShutdown();Looper.shirazShutdownMain();}
  System.out.println("TTS_OK "+args[1]);
 }
}
