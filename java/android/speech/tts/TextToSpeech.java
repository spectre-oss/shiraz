// SPDX-License-Identifier: GPL-3.0-only
package android.speech.tts;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.*;
import java.util.*;
import shiraz.runtime.NativeSpeech;
/** Local offline speech service. Each instance owns a bounded, serial utterance queue. */
public class TextToSpeech {
 public static final int SUCCESS=0,ERROR=-1,QUEUE_FLUSH=0,QUEUE_ADD=1;
 public static final int LANG_AVAILABLE=0,LANG_COUNTRY_AVAILABLE=1,LANG_COUNTRY_VAR_AVAILABLE=2,LANG_MISSING_DATA=-1,LANG_NOT_SUPPORTED=-2;
 public static final int ERROR_SYNTHESIS=-3,ERROR_SERVICE=-4,ERROR_OUTPUT=-5,ERROR_NETWORK=-6,ERROR_NETWORK_TIMEOUT=-7,ERROR_INVALID_REQUEST=-8,ERROR_NOT_INSTALLED_YET=-9;
 public interface OnInitListener{void onInit(int status);}public interface OnUtteranceCompletedListener{void onUtteranceCompleted(String utteranceId);}
 public static class Engine{public static final String KEY_FEATURE_EMBEDDED_SYNTHESIS="embeddedTts",KEY_FEATURE_NETWORK_SYNTHESIS="networkTts";public static final String KEY_PARAM_STREAM="streamType",KEY_PARAM_UTTERANCE_ID="utteranceId",KEY_PARAM_VOLUME="volume",KEY_PARAM_PAN="pan";}
 private static final String ENGINE="shiraz.espeak";
 private static final Set<TextToSpeech> INSTANCES=new HashSet<TextToSpeech>();
 private final Object lock=new Object();private final ArrayDeque<Job> queue=new ArrayDeque<Job>();
 private final Set<Voice> voices=new LinkedHashSet<Voice>();private Voice voice,defaultVoice;private int rate=175,pitch=50,sampleRate;
 private boolean ready,closed;private Job active;private volatile UtteranceProgressListener progress;private volatile OnUtteranceCompletedListener completed;
 private static final class Job{String text,id,voice;int rate,pitch;long silence,handle;volatile boolean canceled;boolean started;}
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
 public int setSpeechRate(float value){if(!Float.isFinite(value)||value<=0||value*175<80||value*175>450)return ERROR;synchronized(lock){if(closed)return ERROR;rate=Math.round(value*175);return SUCCESS;}}
 public int setPitch(float value){if(!Float.isFinite(value)||value<=0||value*50>99)return ERROR;synchronized(lock){if(closed)return ERROR;pitch=Math.round(value*50);return SUCCESS;}}
 public int setAudioAttributes(AudioAttributes value){Objects.requireNonNull(value);synchronized(lock){return closed||value.getFlags()!=0?ERROR:SUCCESS;}}
 public int setOnUtteranceProgressListener(UtteranceProgressListener listener){progress=listener;return SUCCESS;}
 public int setOnUtteranceCompletedListener(OnUtteranceCompletedListener listener){completed=listener;return SUCCESS;}
 public int speak(String text,int mode,HashMap<String,String> params){if(text==null||!supportedParams(params))return ERROR;return enqueue(text,0,mode,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID));}
 public int speak(CharSequence text,int mode,Bundle params,String id){if(text==null||(params!=null&&(params.getFloat(Engine.KEY_PARAM_VOLUME,1f)!=1f||params.getFloat(Engine.KEY_PARAM_PAN,0f)!=0f)))return ERROR;return enqueue(text.toString(),0,mode,id);}
 public int playSilentUtterance(long duration,int mode,String id){if(duration<0||duration>120000)return ERROR;return enqueue(null,duration,mode,id);}
 public int playSilence(long duration,int mode,HashMap<String,String> params){return playSilentUtterance(duration,mode,params==null?null:params.get(Engine.KEY_PARAM_UTTERANCE_ID));}
 private static boolean supportedParams(HashMap<String,String> params){
  if(params==null)return true;try{return (!params.containsKey(Engine.KEY_PARAM_VOLUME)||Float.parseFloat(params.get(Engine.KEY_PARAM_VOLUME))==1f)&&(!params.containsKey(Engine.KEY_PARAM_PAN)||Float.parseFloat(params.get(Engine.KEY_PARAM_PAN))==0f);}catch(RuntimeException invalid){return false;}
 }
 private int enqueue(String text,long silence,int mode,String id){
  if((mode!=QUEUE_ADD&&mode!=QUEUE_FLUSH)||(text!=null&&(text.length()>4096||text.indexOf('\0')>=0))||(id!=null&&id.length()>1024))return ERROR;
  List<Job> removed=Collections.emptyList();synchronized(lock){if(!ready||closed)return ERROR;if(mode==QUEUE_FLUSH)removed=cancelLocked();if(queue.size()>=64)return ERROR;Job job=new Job();job.text=text;job.silence=silence;job.id=id;job.voice=voice.getName();job.rate=rate;job.pitch=pitch;queue.add(job);lock.notifyAll();}for(Job job:removed)notifyResult(job,1);return SUCCESS;
 }
 private List<Job> cancelLocked(){List<Job> removed=new ArrayList<Job>(queue);queue.clear();for(Job job:removed)job.canceled=true;if(active!=null){active.canceled=true;if(active.handle!=0)NativeSpeech.cancelPlayback(active.handle);}lock.notifyAll();return removed;}
 public int stop(){List<Job> removed;synchronized(lock){if(closed)return ERROR;removed=cancelLocked();}for(Job job:removed)notifyResult(job,1);return SUCCESS;}
 public void shutdown(){List<Job> removed;synchronized(lock){if(closed)return;closed=true;ready=false;removed=cancelLocked();}for(Job job:removed)notifyResult(job,1);}
 public boolean isSpeaking(){synchronized(lock){return active!=null&&!active.canceled;}}
 public static void shirazShutdown(){List<TextToSpeech> copy;synchronized(INSTANCES){copy=new ArrayList<TextToSpeech>(INSTANCES);}for(TextToSpeech t:copy)t.shutdown();}
 private void run(Job job){int result=ERROR_SYNTHESIS;try{
  if(job.canceled){notifyResult(job,1);return;}UtteranceProgressListener listener=progress;if(listener!=null&&job.id!=null)listener.onStart(job.id);
  if(job.text==null){long end=System.nanoTime()+job.silence*1000000L;synchronized(lock){while(!job.canceled){long remaining=end-System.nanoTime();if(remaining<=0)break;lock.wait(Math.max(1,remaining/1000000));}}result=job.canceled?1:0;}
  else{short[] pcm=NativeSpeech.synthesize(job.text,job.voice,job.rate,job.pitch);synchronized(lock){if(!job.canceled)job.handle=NativeSpeech.createPlayback();}if(job.canceled)result=1;else{result=NativeSpeech.play(job.handle,pcm,sampleRate);if(result<0)result=ERROR_OUTPUT;}}
 }catch(InterruptedException interrupted){Thread.currentThread().interrupt();result=1;}catch(RuntimeException|LinkageError failure){result=ERROR_SYNTHESIS;}
 finally{long handle;synchronized(lock){handle=job.handle;job.handle=0;}if(handle!=0)NativeSpeech.releasePlayback(handle);}
 notifyResult(job,job.canceled?1:result);
 }
 private void notifyResult(Job job,int result){if(job.id==null)return;UtteranceProgressListener listener=progress;try{if(listener!=null){if(result==0)listener.onDone(job.id);else if(result==1)listener.onStop(job.id,job.started);else listener.onError(job.id,result);}OnUtteranceCompletedListener legacy=completed;if(result==0&&legacy!=null)legacy.onUtteranceCompleted(job.id);}catch(RuntimeException ignored){/* An app callback must not strand subsequent queued speech. */}}
}
