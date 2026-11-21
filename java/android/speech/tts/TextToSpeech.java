// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import shiraz.runtime.NativeSpeech;
/** Local offline speech service. Each instance owns a bounded, serial utterance queue. */
public class TextToSpeech {
 public static final String ACTION_TTS_QUEUE_PROCESSING_COMPLETED="android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED";
 public static final int SUCCESS=0,ERROR=-1,STOPPED=-2,QUEUE_FLUSH=0,QUEUE_ADD=1;
 public static final int LANG_AVAILABLE=0,LANG_COUNTRY_AVAILABLE=1,LANG_COUNTRY_VAR_AVAILABLE=2,LANG_MISSING_DATA=-1,LANG_NOT_SUPPORTED=-2;
 public static final int ERROR_SYNTHESIS=-3,ERROR_SERVICE=-4,ERROR_OUTPUT=-5,ERROR_NETWORK=-6,ERROR_NETWORK_TIMEOUT=-7,ERROR_INVALID_REQUEST=-8,ERROR_NOT_INSTALLED_YET=-9;
 public interface OnInitListener{void onInit(int status);}public interface OnUtteranceCompletedListener{void onUtteranceCompleted(String utteranceId);}
 public class Engine{
  public static final int DEFAULT_STREAM=3;
  public static final int CHECK_VOICE_DATA_PASS=1,CHECK_VOICE_DATA_FAIL=0,CHECK_VOICE_DATA_BAD_DATA=-1,CHECK_VOICE_DATA_MISSING_DATA=-2,CHECK_VOICE_DATA_MISSING_VOLUME=-3;
  public static final String INTENT_ACTION_TTS_SERVICE="android.intent.action.TTS_SERVICE",SERVICE_META_DATA="android.speech.tts";
  public static final String ACTION_INSTALL_TTS_DATA="android.speech.tts.engine.INSTALL_TTS_DATA",ACTION_TTS_DATA_INSTALLED="android.speech.tts.engine.TTS_DATA_INSTALLED",ACTION_CHECK_TTS_DATA="android.speech.tts.engine.CHECK_TTS_DATA",ACTION_GET_SAMPLE_TEXT="android.speech.tts.engine.GET_SAMPLE_TEXT";
  public static final String EXTRA_VOICE_DATA_ROOT_DIRECTORY="dataRoot",EXTRA_VOICE_DATA_FILES="dataFiles",EXTRA_VOICE_DATA_FILES_INFO="dataFilesInfo",EXTRA_AVAILABLE_VOICES="availableVoices",EXTRA_UNAVAILABLE_VOICES="unavailableVoices",EXTRA_CHECK_VOICE_DATA_FOR="checkVoiceDataFor",EXTRA_TTS_DATA_INSTALLED="dataInstalled",EXTRA_SAMPLE_TEXT="sampleText";
  public static final String KEY_FEATURE_EMBEDDED_SYNTHESIS="embeddedTts",KEY_FEATURE_NETWORK_SYNTHESIS="networkTts",KEY_FEATURE_NETWORK_TIMEOUT_MS="networkTimeoutMs",KEY_FEATURE_NETWORK_RETRIES_COUNT="networkRetriesCount",KEY_FEATURE_NOT_INSTALLED="notInstalled";
  public static final String KEY_PARAM_STREAM="streamType",KEY_PARAM_UTTERANCE_ID="utteranceId",KEY_PARAM_VOLUME="volume",KEY_PARAM_PAN="pan",KEY_PARAM_SESSION_ID="sessionId";
  public Engine(){}
 }
 public static class EngineInfo{
  public String name;
  public String label;
  public int icon;
  @Override public String toString(){return "EngineInfo{name="+name+"}";}
 }
 private static final String ENGINE="shiraz.espeak";
 // Opt-in numeric diagnostics: never log utterance text, app IDs, voice names or exceptions.
 private static final boolean TRACE="1".equals(System.getenv("SHIRAZ_TRACE_SPEECH"));
 private static final java.util.concurrent.atomic.AtomicInteger TRACE_COUNT=new java.util.concurrent.atomic.AtomicInteger();
 private static void trace(String event,int value){
  if(!TRACE)return;
  int count=TRACE_COUNT.get();
  while(count<512){if(TRACE_COUNT.compareAndSet(count,count+1)){System.err.println("SHIRAZ_SPEECH "+event+"="+value);return;}count=TRACE_COUNT.get();}
 }
 private static final Set<TextToSpeech> INSTANCES=new HashSet<TextToSpeech>();
 private final Object lock=new Object();private final ArrayDeque<Job> queue=new ArrayDeque<Job>();
 private final Map<String,Uri> utterances=new HashMap<String,Uri>();private final Map<String,Uri> earcons=new HashMap<String,Uri>();
 private final Set<Voice> voices=new LinkedHashSet<Voice>();private Voice voice,defaultVoice;private int rate=175,pitch=50,sampleRate;
 private boolean ready,closed;private Job active;private volatile UtteranceProgressListener progress;private volatile OnUtteranceCompletedListener completed;
 private static final class Job{String text,id,voice;int rate,pitch;long silence,handle;File outputFile;ParcelFileDescriptor outputPfd;volatile boolean canceled;boolean started;}
 public TextToSpeech(Context context,OnInitListener listener){this(context,listener,null);}
 public TextToSpeech(Context context,final OnInitListener listener,final String engine){
  Objects.requireNonNull(context);final Handler main=new Handler(Looper.getMainLooper());
  synchronized(INSTANCES){if(INSTANCES.size()>=16)throw new IllegalStateException("Speech instance limit");INSTANCES.add(this);}
  Thread worker=new Thread(new Runnable(){public void run(){work(listener,engine,main);}},"Shiraz speech");worker.setDaemon(true);worker.start();
 }
 private void work(final OnInitListener listener,String engine,Handler main){
  int status=ERROR;
  try{
   if(engine!=null&&!ENGINE.equals(engine))throw new IllegalArgumentException("Unavailable speech engine");
   int hz=NativeSpeech.initialize(System.getProperty("shiraz.speechData",System.getProperty("user.dir")+"/.local/share"));String[] catalog=NativeSpeech.installedVoices();
   synchronized(lock){sampleRate=hz;for(int i=0;i<catalog.length;i+=2){Locale locale=Locale.forLanguageTag(catalog[i+1]);if(!locale.getLanguage().isEmpty())voices.add(new Voice(catalog[i],locale,Voice.QUALITY_LOW,Voice.LATENCY_VERY_HIGH,false,Collections.<String>emptySet()));}defaultVoice=choose(Locale.getDefault());if(defaultVoice==null)defaultVoice=choose(Locale.ENGLISH);if(defaultVoice==null&&!voices.isEmpty())defaultVoice=voices.iterator().next();voice=defaultVoice;ready=!closed&&voice!=null;status=ready?SUCCESS:ERROR;}
  }catch(RuntimeException|LinkageError failure){synchronized(lock){ready=false;}}
  trace("init",status);
  final int result=status;main.post(new Runnable(){public void run(){synchronized(lock){if(closed)return;}if(listener!=null)listener.onInit(result);}});
  try{for(;;){Job job;synchronized(lock){while(queue.isEmpty()&&!closed)lock.wait();if(closed)return;job=queue.remove();active=job;job.started=true;}run(job);synchronized(lock){if(active==job)active=null;}}}
  catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
  finally{synchronized(INSTANCES){INSTANCES.remove(this);}}
 }
 private Voice choose(Locale locale){Voice language=null,country=null;for(Voice v:voices){Locale l=v.getLocale();if(l.equals(locale))return v;if(l.getLanguage().equals(locale.getLanguage())){if(language==null)language=v;if(!locale.getCountry().isEmpty()&&l.getCountry().equals(locale.getCountry()))country=v;}}return country!=null?country:language;}
 public int isLanguageAvailable(Locale locale){Objects.requireNonNull(locale);synchronized(lock){if(!ready)return LANG_MISSING_DATA;Voice found=choose(locale);if(found==null)return LANG_NOT_SUPPORTED;Locale actual=found.getLocale();if(!locale.getCountry().isEmpty()&&actual.getCountry().equals(locale.getCountry()))return !locale.getVariant().isEmpty()&&actual.getVariant().equals(locale.getVariant())?LANG_COUNTRY_VAR_AVAILABLE:LANG_COUNTRY_AVAILABLE;return LANG_AVAILABLE;}}
 public int setLanguage(Locale locale){synchronized(lock){int result=isLanguageAvailable(locale);if(result>=0)voice=choose(locale);return result;}}
 /** Engine default, independent of the voice selected for the next utterance. */
 @Deprecated public Locale getDefaultLanguage(){synchronized(lock){return !ready||defaultVoice==null?null:defaultVoice.getLocale();}}
 @Deprecated public Set<String> getFeatures(Locale locale){Objects.requireNonNull(locale);synchronized(lock){if(!ready||choose(locale)==null)return null;return new HashSet<String>(Collections.singleton(Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS));}}
 public Locale getLanguage(){synchronized(lock){return voice==null?null:voice.getLocale();}}
 public Voice getVoice(){synchronized(lock){return voice;}}public Voice getDefaultVoice(){synchronized(lock){return defaultVoice;}}
 public Set<Voice> getVoices(){synchronized(lock){return new LinkedHashSet<Voice>(voices);}}
 public Set<Locale> getAvailableLanguages(){Set<Locale> result=new HashSet<Locale>();for(Voice v:getVoices())result.add(v.getLocale());return result;}
 public int setVoice(Voice selected){synchronized(lock){if(!ready||!voices.contains(selected))return ERROR;voice=selected;return SUCCESS;}}
 public String getDefaultEngine(){return ENGINE;}public static int getMaxSpeechInputLength(){return 4096;}
 @Deprecated public int setEngineByPackageName(String enginePackageName){synchronized(lock){return closed||!ENGINE.equals(enginePackageName)?ERROR:SUCCESS;}}
 @Deprecated public boolean areDefaultsEnforced(){return false;}
 public List<EngineInfo> getEngines(){EngineInfo info=new EngineInfo();info.name=ENGINE;info.label="Shiraz eSpeak";info.icon=0;List<EngineInfo> list=new ArrayList<EngineInfo>(1);list.add(info);return list;}
 public int addSpeech(String text,String packagename,int resourceId){return addSpeech((CharSequence)text,packagename,resourceId);}
 public int addSpeech(CharSequence text,String packagename,int resourceId){if(text==null||packagename==null)return ERROR;return addSpeech(text,Uri.parse("android.resource://"+packagename+"/"+resourceId));}
 public int addSpeech(String text,String filename){if(text==null||filename==null)return ERROR;return addSpeech(text,Uri.parse(filename));}
 public int addSpeech(CharSequence text,File file){if(text==null||file==null)return ERROR;return addSpeech(text,Uri.fromFile(file));}
 public int addSpeech(CharSequence text,Uri uri){if(text==null||uri==null)return ERROR;synchronized(lock){if(closed)return ERROR;utterances.put(text.toString(),uri);return SUCCESS;}}
 public int addEarcon(String earcon,String packagename,int resourceId){if(earcon==null||packagename==null)return ERROR;return addEarcon(earcon,Uri.parse("android.resource://"+packagename+"/"+resourceId));}
 @Deprecated public int addEarcon(String earcon,String filename){if(earcon==null||filename==null)return ERROR;return addEarcon(earcon,Uri.parse(filename));}
 public int addEarcon(String earcon,File file){if(earcon==null||file==null)return ERROR;return addEarcon(earcon,Uri.fromFile(file));}
 public int addEarcon(String earcon,Uri uri){if(earcon==null||uri==null)return ERROR;synchronized(lock){if(closed)return ERROR;earcons.put(earcon,uri);return SUCCESS;}}
 public int playEarcon(String earcon,int queueMode,Bundle params,String utteranceId){if(earcon==null||(params!=null&&(params.getFloat(Engine.KEY_PARAM_VOLUME,1f)!=1f||params.getFloat(Engine.KEY_PARAM_PAN,0f)!=0f)))return ERROR;synchronized(lock){if(!earcons.containsKey(earcon))return ERROR;}return enqueue(earcon,0,queueMode,utteranceId,null,null);}
 @Deprecated public int playEarcon(String earcon,int queueMode,HashMap<String,String> params){if(earcon==null||!supportedParams(params))return ERROR;synchronized(lock){if(!earcons.containsKey(earcon))return ERROR;}return enqueue(earcon,0,queueMode,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID),null,null);}
 public int setSpeechRate(float value){if(!Float.isFinite(value)||value<=0||value*175<80||value*175>450)return ERROR;synchronized(lock){if(closed)return ERROR;rate=Math.round(value*175);return SUCCESS;}}
 public int setPitch(float value){if(!Float.isFinite(value)||value<=0||value*50>99)return ERROR;synchronized(lock){if(closed)return ERROR;pitch=Math.round(value*50);return SUCCESS;}}
 public int setAudioAttributes(AudioAttributes value){Objects.requireNonNull(value);synchronized(lock){return closed||value.getFlags()!=0?ERROR:SUCCESS;}}
 public int setOnUtteranceProgressListener(UtteranceProgressListener listener){progress=listener;return SUCCESS;}
 @Deprecated public int setOnUtteranceCompletedListener(OnUtteranceCompletedListener listener){completed=listener;return SUCCESS;}
 @Deprecated public int speak(String text,int mode,HashMap<String,String> params){if(text==null||!supportedParams(params))return ERROR;return enqueue(text,0,mode,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID),null,null);}
 public int speak(CharSequence text,int mode,Bundle params,String id){if(text==null||(params!=null&&(params.getFloat(Engine.KEY_PARAM_VOLUME,1f)!=1f||params.getFloat(Engine.KEY_PARAM_PAN,0f)!=0f)))return ERROR;return enqueue(text.toString(),0,mode,id,null,null);}
 public int playSilentUtterance(long duration,int mode,String id){if(duration<0||duration>120000)return ERROR;return enqueue(null,duration,mode,id,null,null);}
 @Deprecated public int playSilence(long duration,int mode,HashMap<String,String> params){return playSilentUtterance(duration,mode,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID));}
 public int synthesizeToFile(CharSequence text,Bundle params,ParcelFileDescriptor fileDescriptor,String utteranceId){if(text==null||fileDescriptor==null||(params!=null&&(params.getFloat(Engine.KEY_PARAM_VOLUME,1f)!=1f||params.getFloat(Engine.KEY_PARAM_PAN,0f)!=0f)))return ERROR;return enqueue(text.toString(),0,QUEUE_ADD,utteranceId,null,fileDescriptor);}
 public int synthesizeToFile(CharSequence text,Bundle params,File file,String utteranceId){if(text==null||file==null||(params!=null&&(params.getFloat(Engine.KEY_PARAM_VOLUME,1f)!=1f||params.getFloat(Engine.KEY_PARAM_PAN,0f)!=0f)))return ERROR;return enqueue(text.toString(),0,QUEUE_ADD,utteranceId,file,null);}
 @Deprecated public int synthesizeToFile(String text,HashMap<String,String> params,String filename){if(text==null||filename==null||!supportedParams(params))return ERROR;return enqueue(text,0,QUEUE_ADD,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID),new File(filename),null);}
 private static boolean supportedParams(HashMap<String,String> params){
  if(params==null)return true;try{return (!params.containsKey(Engine.KEY_PARAM_VOLUME)||Float.parseFloat(params.get(Engine.KEY_PARAM_VOLUME))==1f)&&(!params.containsKey(Engine.KEY_PARAM_PAN)||Float.parseFloat(params.get(Engine.KEY_PARAM_PAN))==0f);}catch(RuntimeException invalid){return false;}
 }
 private int enqueue(String text,long silence,int mode,String id,File outputFile,ParcelFileDescriptor outputPfd){
  if((mode!=QUEUE_ADD&&mode!=QUEUE_FLUSH)||(text!=null&&(text.length()>4096||text.indexOf('\0')>=0))||(id!=null&&id.length()>1024))return ERROR;
  List<Job> removed=Collections.emptyList();synchronized(lock){if(!ready||closed)return ERROR;if(mode==QUEUE_FLUSH)removed=cancelLocked();if(queue.size()>=64)return ERROR;Job job=new Job();job.text=text;job.silence=silence;job.id=id;job.voice=voice.getName();job.rate=rate;job.pitch=pitch;job.outputFile=outputFile;job.outputPfd=outputPfd;queue.add(job);lock.notifyAll();}for(Job job:removed)notifyResult(job,1);return SUCCESS;
 }
 private List<Job> cancelLocked(){List<Job> removed=new ArrayList<Job>(queue);queue.clear();for(Job job:removed)job.canceled=true;if(active!=null){active.canceled=true;if(active.handle!=0)NativeSpeech.cancelPlayback(active.handle);}lock.notifyAll();return removed;}
 public int stop(){List<Job> removed;synchronized(lock){if(closed)return ERROR;removed=cancelLocked();}for(Job job:removed)notifyResult(job,1);return SUCCESS;}
 public void shutdown(){List<Job> removed;synchronized(lock){if(closed)return;closed=true;ready=false;removed=cancelLocked();}for(Job job:removed)notifyResult(job,1);}
 public boolean isSpeaking(){synchronized(lock){return active!=null&&!active.canceled;}}
 public static void shirazShutdown(){List<TextToSpeech> copy;synchronized(INSTANCES){copy=new ArrayList<TextToSpeech>(INSTANCES);}for(TextToSpeech t:copy)t.shutdown();}
 private void run(Job job){int result=ERROR_SYNTHESIS;try{
  if(job.canceled){notifyResult(job,1);return;}UtteranceProgressListener listener=progress;if(listener!=null&&job.id!=null)listener.onStart(job.id);
  if(job.text==null){long end=System.nanoTime()+job.silence*1000000L;synchronized(lock){while(!job.canceled){long remaining=end-System.nanoTime();if(remaining<=0)break;lock.wait(Math.max(1,remaining/1000000));}}result=job.canceled?1:0;}
  else if(job.outputFile!=null||job.outputPfd!=null){short[] pcm=NativeSpeech.synthesize(job.text,job.voice,job.rate,job.pitch);trace("samples",pcm.length);if(job.canceled)result=1;else result=writeWav(job,pcm,listener);}
  else{short[] pcm=NativeSpeech.synthesize(job.text,job.voice,job.rate,job.pitch);trace("samples",pcm.length);synchronized(lock){if(!job.canceled)job.handle=NativeSpeech.createPlayback();}if(job.canceled)result=1;else{result=NativeSpeech.play(job.handle,pcm,sampleRate);if(result<0)result=ERROR_OUTPUT;}}
 }catch(InterruptedException interrupted){Thread.currentThread().interrupt();result=1;}catch(RuntimeException|LinkageError failure){result=ERROR_SYNTHESIS;}
 finally{long handle;synchronized(lock){handle=job.handle;job.handle=0;}if(handle!=0)NativeSpeech.releasePlayback(handle);}
 notifyResult(job,job.canceled?1:result);
 }
 private int writeWav(Job job,short[] pcm,UtteranceProgressListener listener){
  int dataLen=pcm.length*2;byte[] bytes=new byte[dataLen];
  for(int i=0;i<pcm.length;i++){short s=pcm[i];bytes[i*2]=(byte)(s&0xff);bytes[i*2+1]=(byte)((s>>>8)&0xff);}
  if(listener!=null&&job.id!=null){try{listener.onBeginSynthesis(job.id,sampleRate,AudioFormat.ENCODING_PCM_16BIT,1);listener.onAudioAvailable(job.id,bytes);}catch(RuntimeException ignored){}}
  byte[] header=new byte[44];int totalDataLen=36+dataLen,byteRate=sampleRate*2;
  header[0]='R';header[1]='I';header[2]='F';header[3]='F';putIntLE(header,4,totalDataLen);
  header[8]='W';header[9]='A';header[10]='V';header[11]='E';header[12]='f';header[13]='m';header[14]='t';header[15]=' ';
  putIntLE(header,16,16);header[20]=1;header[21]=0;header[22]=1;header[23]=0;putIntLE(header,24,sampleRate);putIntLE(header,28,byteRate);
  header[32]=2;header[33]=0;header[34]=16;header[35]=0;header[36]='d';header[37]='a';header[38]='t';header[39]='a';putIntLE(header,40,dataLen);
  OutputStream out=null;
  try{
   out=job.outputFile!=null?new FileOutputStream(job.outputFile):new FileOutputStream(job.outputPfd.getFileDescriptor());
   out.write(header);out.write(bytes);out.flush();return 0;
  }catch(IOException io){return ERROR_OUTPUT;}
  finally{if(out!=null&&job.outputFile!=null){try{out.close();}catch(IOException ignored){}}}
 }
 private static void putIntLE(byte[] b,int off,int v){b[off]=(byte)(v&0xff);b[off+1]=(byte)((v>>>8)&0xff);b[off+2]=(byte)((v>>>16)&0xff);b[off+3]=(byte)((v>>>24)&0xff);}
 private void notifyResult(Job job,int result){trace(job.text==null?"silence_result":"speech_result",result);if(job.id==null)return;UtteranceProgressListener listener=progress;try{if(listener!=null){if(result==0)listener.onDone(job.id);else if(result==1)listener.onStop(job.id,job.started);else listener.onError(job.id,result);}OnUtteranceCompletedListener legacy=completed;if(result==0&&legacy!=null)legacy.onUtteranceCompleted(job.id);}catch(RuntimeException ignored){/* An app callback must not strand subsequent queued speech. */}}
}
