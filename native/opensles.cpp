// SPDX-License-Identifier: GPL-3.0-only
#include <SLES/OpenSLES_Android.h>
static const SLInterfaceID_ iid_ENGINE = { 0x8d97c260, 0xddd4, 0x11db, 0x958f, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_ENGINE = &iid_ENGINE;
static const SLInterfaceID_ iid_PLAY = { 0xef0bd9c0, 0xddd7, 0x11db, 0xbf49, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_PLAY = &iid_PLAY;
static const SLInterfaceID_ iid_VOLUME = { 0x09e8ede0, 0xddde, 0x11db, 0xb4f6, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_VOLUME = &iid_VOLUME;
static const SLInterfaceID_ iid_BUFFERQUEUE = { 0x2bc99cc0, 0xddd4, 0x11db, 0x8d99, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_BUFFERQUEUE = &iid_BUFFERQUEUE;
static const SLInterfaceID_ iid_ANDROIDSIMPLEBUFFERQUEUE = {0x198e4940,0xc5d7,0x11dd,0xad8b,{0x00,0x02,0xa5,0xd5,0xc5,0x1b}};
extern "C" const SLInterfaceID SL_IID_ANDROIDSIMPLEBUFFERQUEUE = &iid_ANDROIDSIMPLEBUFFERQUEUE;
static const SLInterfaceID_ iid_ANDROIDCONFIGURATION = { 0x89f6a7e0, 0xbeac, 0x11df, 0x8b5c, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_ANDROIDCONFIGURATION = &iid_ANDROIDCONFIGURATION;
static const SLInterfaceID_ iid_OUTPUTMIX = { 0x97750f60, 0xddd7, 0x11db, 0x92b1, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_OUTPUTMIX = &iid_OUTPUTMIX;
static const SLInterfaceID_ iid_RECORD = { 0xc5657aa0, 0xdddb, 0x11db, 0x82f7, { 0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b } };
extern "C" const SLInterfaceID SL_IID_RECORD = &iid_RECORD;
#include <alsa/asoundlib.h>
#include <condition_variable>
#include <chrono>
#include <mutex>
#include <thread>
#include <deque>
#include <vector>
#include <atomic>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <type_traits>
#include <new>
namespace {
template<class R,class... A> auto unsupported(R(*)(A...))->R(*)(A...){return [](A...)->R{if constexpr(!std::is_void<R>::value)return SL_RESULT_FEATURE_UNSUPPORTED;};}
static SLObjectItf_ defaults_SLObjectItf_(){SLObjectItf_ t{};t.Realize=unsupported(t.Realize);t.Resume=unsupported(t.Resume);t.GetState=unsupported(t.GetState);t.GetInterface=unsupported(t.GetInterface);t.RegisterCallback=unsupported(t.RegisterCallback);t.AbortAsyncOperation=unsupported(t.AbortAsyncOperation);t.Destroy=unsupported(t.Destroy);t.SetPriority=unsupported(t.SetPriority);t.GetPriority=unsupported(t.GetPriority);t.SetLossOfControlInterfaces=unsupported(t.SetLossOfControlInterfaces);return t;}
static SLEngineItf_ defaults_SLEngineItf_(){SLEngineItf_ t{};t.CreateLEDDevice=unsupported(t.CreateLEDDevice);t.CreateVibraDevice=unsupported(t.CreateVibraDevice);t.CreateAudioPlayer=unsupported(t.CreateAudioPlayer);t.CreateAudioRecorder=unsupported(t.CreateAudioRecorder);t.CreateMidiPlayer=unsupported(t.CreateMidiPlayer);t.CreateListener=unsupported(t.CreateListener);t.Create3DGroup=unsupported(t.Create3DGroup);t.CreateOutputMix=unsupported(t.CreateOutputMix);t.CreateMetadataExtractor=unsupported(t.CreateMetadataExtractor);t.CreateExtensionObject=unsupported(t.CreateExtensionObject);t.QueryNumSupportedInterfaces=unsupported(t.QueryNumSupportedInterfaces);t.QuerySupportedInterfaces=unsupported(t.QuerySupportedInterfaces);t.QueryNumSupportedExtensions=unsupported(t.QueryNumSupportedExtensions);t.QuerySupportedExtension=unsupported(t.QuerySupportedExtension);t.IsExtensionSupported=unsupported(t.IsExtensionSupported);return t;}
static SLPlayItf_ defaults_SLPlayItf_(){SLPlayItf_ t{};t.SetPlayState=unsupported(t.SetPlayState);t.GetPlayState=unsupported(t.GetPlayState);t.GetDuration=unsupported(t.GetDuration);t.GetPosition=unsupported(t.GetPosition);t.RegisterCallback=unsupported(t.RegisterCallback);t.SetCallbackEventsMask=unsupported(t.SetCallbackEventsMask);t.GetCallbackEventsMask=unsupported(t.GetCallbackEventsMask);t.SetMarkerPosition=unsupported(t.SetMarkerPosition);t.ClearMarkerPosition=unsupported(t.ClearMarkerPosition);t.GetMarkerPosition=unsupported(t.GetMarkerPosition);t.SetPositionUpdatePeriod=unsupported(t.SetPositionUpdatePeriod);t.GetPositionUpdatePeriod=unsupported(t.GetPositionUpdatePeriod);return t;}
static SLVolumeItf_ defaults_SLVolumeItf_(){SLVolumeItf_ t{};t.SetVolumeLevel=unsupported(t.SetVolumeLevel);t.GetVolumeLevel=unsupported(t.GetVolumeLevel);t.GetMaxVolumeLevel=unsupported(t.GetMaxVolumeLevel);t.SetMute=unsupported(t.SetMute);t.GetMute=unsupported(t.GetMute);t.EnableStereoPosition=unsupported(t.EnableStereoPosition);t.IsEnabledStereoPosition=unsupported(t.IsEnabledStereoPosition);t.SetStereoPosition=unsupported(t.SetStereoPosition);t.GetStereoPosition=unsupported(t.GetStereoPosition);return t;}
static SLAndroidSimpleBufferQueueItf_ defaults_SLAndroidSimpleBufferQueueItf_(){SLAndroidSimpleBufferQueueItf_ t{};t.Enqueue=unsupported(t.Enqueue);t.Clear=unsupported(t.Clear);t.GetState=unsupported(t.GetState);t.RegisterCallback=unsupported(t.RegisterCallback);return t;}
static SLAndroidConfigurationItf_ defaults_SLAndroidConfigurationItf_(){SLAndroidConfigurationItf_ t{};t.SetConfiguration=unsupported(t.SetConfiguration);t.GetConfiguration=unsupported(t.GetConfiguration);t.AcquireJavaProxy=unsupported(t.AcquireJavaProxy);t.ReleaseJavaProxy=unsupported(t.ReleaseJavaProxy);return t;}
struct Object;
template<class Table> struct Interface {const Table* table;Object* owner;};
struct Object {
 Interface<SLOutputMixItf_> mix;Interface<SLObjectItf_> object;Interface<SLEngineItf_> engine;Interface<SLPlayItf_> play;Interface<SLVolumeItf_> volume;Interface<SLAndroidSimpleBufferQueueItf_> queue;Interface<SLAndroidConfigurationItf_> config;
 int kind;SLuint32 state=SL_OBJECT_STATE_UNREALIZED,playState=SL_PLAYSTATE_STOPPED;unsigned rate=48000,channels=2,bits=16,capacity=4,generation=0,completed=0;uint64_t frames=0;bool floating=false,done=false,muted=false,selfDelete=false;SLmillibel gain=0;
 std::mutex mutex;std::condition_variable changed;std::thread worker;std::deque<std::vector<unsigned char>> buffers;snd_pcm_t* pcm=nullptr;slAndroidSimpleBufferQueueCallback callback=nullptr;void* callbackContext=nullptr;slPlayCallback playCallback=nullptr;void* playContext=nullptr;SLuint32 eventMask=0;SLmillisecond marker=SL_TIME_UNKNOWN;bool markerFired=false;
 explicit Object(int type);~Object(){if(pcm)snd_pcm_close(pcm);}
};
template<class Table> Object* owner(const Table* const* self){return reinterpret_cast<const Interface<Table>*>(self)->owner;}
bool same(SLInterfaceID a,SLInterfaceID b){return a&&b&&!std::memcmp(a,b,sizeof(SLInterfaceID_));}
SLresult getInterface(SLObjectItf self,SLInterfaceID id,void* out){if(!out)return SL_RESULT_PARAMETER_INVALID;Object* o=owner(self);void* value=nullptr;
 if(same(id,SL_IID_OUTPUTMIX)&&o->kind==1)value=&o->mix;
 if(same(id,SL_IID_ENGINE)&&o->kind==0)value=&o->engine;
 if(o->kind==2){if(same(id,SL_IID_PLAY))value=&o->play;else if(same(id,SL_IID_VOLUME))value=&o->volume;else if(same(id,SL_IID_BUFFERQUEUE)||same(id,SL_IID_ANDROIDSIMPLEBUFFERQUEUE))value=&o->queue;else if(same(id,SL_IID_ANDROIDCONFIGURATION))value=&o->config;}
 *static_cast<void**>(out)=value;return value?SL_RESULT_SUCCESS:SL_RESULT_FEATURE_UNSUPPORTED;
}
void run(Object* o){
 for(;;){std::vector<unsigned char> bytes;unsigned generation;float gain;
  {std::unique_lock<std::mutex> lock(o->mutex);o->changed.wait(lock,[&]{return o->done||(o->playState==SL_PLAYSTATE_PLAYING&&!o->buffers.empty());});if(o->done)return;bytes=std::move(o->buffers.front());generation=o->generation;gain=o->muted?0.f:std::pow(10.f,float(o->gain)/2000.f);}
  const auto started=std::chrono::steady_clock::now();
  if(gain!=1.f){if(o->floating){auto* p=reinterpret_cast<float*>(bytes.data());for(size_t i=0;i<bytes.size()/4;i++)p[i]*=gain;}else{auto* p=reinterpret_cast<int16_t*>(bytes.data());for(size_t i=0;i<bytes.size()/2;i++)p[i]=int16_t(p[i]*gain);}}
  size_t count=bytes.size()/(o->channels*(o->bits/8)),written=0;
  while(written<count){auto n=snd_pcm_writei(o->pcm,bytes.data()+written*o->channels*(o->bits/8),count-written);if(n<0){if(snd_pcm_recover(o->pcm,int(n),1)<0)break;}else if(n==0)break;else written+=size_t(n);}
  slAndroidSimpleBufferQueueCallback callback=nullptr;void* context=nullptr;
  {std::unique_lock<std::mutex> lock(o->mutex);
   // A null/file PCM accepts writes immediately. Do not turn its completion
   // callbacks into a busy loop; time already spent in a hardware write counts.
   const auto deadline=started+std::chrono::nanoseconds(uint64_t(written)*1000000000/o->rate);
   o->changed.wait_until(lock,deadline,[&]{return o->done||o->generation!=generation;});
   if(o->done)return;
   if(o->generation==generation){if(!o->buffers.empty())o->buffers.pop_front();o->frames+=written;++o->completed;callback=o->callback;context=o->callbackContext;}
  }
  if(callback)callback(reinterpret_cast<SLAndroidSimpleBufferQueueItf>(&o->queue),context);
  slPlayCallback end=nullptr;void* endContext=nullptr;SLuint32 events=0;{std::lock_guard<std::mutex> lock(o->mutex);if(!o->done){if(o->buffers.empty())events|=o->eventMask&SL_PLAYEVENT_HEADATEND;if(!o->markerFired&&o->marker!=SL_TIME_UNKNOWN&&o->frames*1000/o->rate>=o->marker){events|=o->eventMask&SL_PLAYEVENT_HEADATMARKER;o->markerFired=true;}if(events){end=o->playCallback;endContext=o->playContext;}}}
  if(end)end(reinterpret_cast<SLPlayItf>(&o->play),endContext,events);
 }
}
SLresult realize(SLObjectItf self,SLboolean async){if(async)return SL_RESULT_FEATURE_UNSUPPORTED;Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);if(o->state==SL_OBJECT_STATE_REALIZED)return SL_RESULT_PRECONDITIONS_VIOLATED;
 if(o->kind==2){const char* device=std::getenv("SHIRAZ_AUDIO_DEVICE");if(!device||!*device)device="default";if(snd_pcm_open(&o->pcm,device,SND_PCM_STREAM_PLAYBACK,0)<0)return SL_RESULT_RESOURCE_ERROR;
 if(snd_pcm_set_params(o->pcm,o->floating?SND_PCM_FORMAT_FLOAT_LE:SND_PCM_FORMAT_S16_LE,SND_PCM_ACCESS_RW_INTERLEAVED,o->channels,o->rate,1,80000)<0){snd_pcm_close(o->pcm);o->pcm=nullptr;return SL_RESULT_CONTENT_UNSUPPORTED;}
 try{o->worker=std::thread([o]{run(o);if(o->selfDelete)delete o;});}catch(const std::exception&){snd_pcm_close(o->pcm);o->pcm=nullptr;return SL_RESULT_RESOURCE_ERROR;}std::fprintf(stderr,"SHIRAZ_OPENSL_OUTPUT rate=%u channels=%u bits=%u\n",o->rate,o->channels,o->bits);}
 o->state=SL_OBJECT_STATE_REALIZED;return SL_RESULT_SUCCESS;
}
void destroy(SLObjectItf self){Object* o=owner(self);{std::lock_guard<std::mutex> lock(o->mutex);o->done=true;o->changed.notify_all();}if(o->worker.joinable()){if(o->worker.get_id()==std::this_thread::get_id()){o->selfDelete=true;o->worker.detach();return;}o->worker.join();}delete o;}
SLresult setPlay(SLPlayItf self,SLuint32 state){if(state<SL_PLAYSTATE_STOPPED||state>SL_PLAYSTATE_PLAYING)return SL_RESULT_PARAMETER_INVALID;Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);if(o->state!=SL_OBJECT_STATE_REALIZED)return SL_RESULT_PRECONDITIONS_VIOLATED;o->playState=state;o->changed.notify_all();return SL_RESULT_SUCCESS;}
SLresult enqueue(SLAndroidSimpleBufferQueueItf self,const void* data,SLuint32 size){Object* o=owner(self);if(!data||!size||size>4*1024*1024||size%(o->channels*(o->bits/8)))return SL_RESULT_PARAMETER_INVALID;std::lock_guard<std::mutex> lock(o->mutex);if(o->buffers.size()>=o->capacity)return SL_RESULT_BUFFER_INSUFFICIENT;try{o->buffers.emplace_back(static_cast<const unsigned char*>(data),static_cast<const unsigned char*>(data)+size);}catch(const std::bad_alloc&){return SL_RESULT_MEMORY_FAILURE;}o->changed.notify_all();return SL_RESULT_SUCCESS;}
SLresult clear(SLAndroidSimpleBufferQueueItf self){Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->buffers.clear();++o->generation;o->completed=0;o->changed.notify_all();return SL_RESULT_SUCCESS;}
SLresult createPlayer(SLEngineItf,SLObjectItf* out,SLDataSource* source,SLDataSink* sink,SLuint32 count,const SLInterfaceID* ids,const SLboolean* required){
 if(!out||!source||!sink||!source->pLocator||!source->pFormat||!sink->pLocator||count>32||(count&&(!ids||!required)))return SL_RESULT_PARAMETER_INVALID;
 *out=nullptr;
 auto* locator=static_cast<SLDataLocator_AndroidSimpleBufferQueue*>(source->pLocator);auto* format=static_cast<SLDataFormat_PCM*>(source->pFormat);
 if((locator->locatorType!=SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE&&locator->locatorType!=SL_DATALOCATOR_BUFFERQUEUE)||*static_cast<SLuint32*>(sink->pLocator)!=SL_DATALOCATOR_OUTPUTMIX)return SL_RESULT_CONTENT_UNSUPPORTED;
 bool floating=format->formatType==SL_ANDROID_DATAFORMAT_PCM_EX&&static_cast<SLAndroidDataFormat_PCM_EX*>(source->pFormat)->representation==SL_ANDROID_PCM_REPRESENTATION_FLOAT;
 if((format->formatType!=SL_DATAFORMAT_PCM&&!floating)||format->numChannels<1||format->numChannels>2||format->samplesPerSec<8000000||format->samplesPerSec>192000000||format->bitsPerSample!=(floating?32u:16u)||format->containerSize!=format->bitsPerSample||format->endianness!=SL_BYTEORDER_LITTLEENDIAN||locator->numBuffers<1||locator->numBuffers>64)return SL_RESULT_CONTENT_UNSUPPORTED;
 Object* o=new(std::nothrow) Object(2);if(!o)return SL_RESULT_MEMORY_FAILURE;o->rate=format->samplesPerSec/1000;o->channels=format->numChannels;o->bits=format->bitsPerSample;o->floating=floating;o->capacity=locator->numBuffers;
 for(unsigned i=0;i<count;i++){void* result;if(required[i]&&getInterface(reinterpret_cast<SLObjectItf>(&o->object),ids[i],&result)!=SL_RESULT_SUCCESS){delete o;return SL_RESULT_FEATURE_UNSUPPORTED;}}
 *out=reinterpret_cast<SLObjectItf>(&o->object);return SL_RESULT_SUCCESS;
}
SLObjectItf_ objectTable(){auto t=defaults_SLObjectItf_();t.Realize=realize;t.GetInterface=getInterface;t.Destroy=destroy;t.GetState=[](SLObjectItf self,SLuint32* state){if(!state)return SL_RESULT_PARAMETER_INVALID;Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*state=o->state;return SL_RESULT_SUCCESS;};return t;}
SLEngineItf_ engineTable(){auto t=defaults_SLEngineItf_();t.CreateAudioPlayer=createPlayer;t.CreateOutputMix=[](SLEngineItf,SLObjectItf* out,SLuint32 count,const SLInterfaceID* ids,const SLboolean* required){if(!out||count>32||(count&&!required))return SL_RESULT_PARAMETER_INVALID;for(unsigned i=0;i<count;i++)if(required[i]&&(!ids||!same(ids[i],SL_IID_OUTPUTMIX)))return SL_RESULT_FEATURE_UNSUPPORTED;Object* o=new(std::nothrow) Object(1);if(!o)return SL_RESULT_MEMORY_FAILURE;*out=reinterpret_cast<SLObjectItf>(&o->object);return SL_RESULT_SUCCESS;};return t;}
SLPlayItf_ playTable(){auto t=defaults_SLPlayItf_();
 t.SetMarkerPosition=[](SLPlayItf self,SLmillisecond position){auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->marker=position;o->markerFired=false;return SL_RESULT_SUCCESS;};
 t.ClearMarkerPosition=[](SLPlayItf self){auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->marker=SL_TIME_UNKNOWN;o->markerFired=false;return SL_RESULT_SUCCESS;};
 t.GetMarkerPosition=[](SLPlayItf self,SLmillisecond* position){if(!position)return SL_RESULT_PARAMETER_INVALID;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*position=o->marker;return SL_RESULT_SUCCESS;};
 t.GetCallbackEventsMask=[](SLPlayItf self,SLuint32* flags){if(!flags)return SL_RESULT_PARAMETER_INVALID;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*flags=o->eventMask;return SL_RESULT_SUCCESS;};t.SetPlayState=setPlay;t.GetPlayState=[](SLPlayItf self,SLuint32* state){if(!state)return SL_RESULT_PARAMETER_INVALID;Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*state=o->playState;return SL_RESULT_SUCCESS;};t.GetPosition=[](SLPlayItf self,SLmillisecond* position){if(!position)return SL_RESULT_PARAMETER_INVALID;Object* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*position=SLmillisecond(o->frames*1000/o->rate);return SL_RESULT_SUCCESS;};t.GetDuration=[](SLPlayItf,SLmillisecond* value){if(!value)return SL_RESULT_PARAMETER_INVALID;*value=SL_TIME_UNKNOWN;return SL_RESULT_SUCCESS;};t.RegisterCallback=[](SLPlayItf self,slPlayCallback cb,void* context){auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->playCallback=cb;o->playContext=context;return SL_RESULT_SUCCESS;};t.SetCallbackEventsMask=[](SLPlayItf self,SLuint32 flags){if(flags&~(SL_PLAYEVENT_HEADATEND|SL_PLAYEVENT_HEADATMARKER))return SL_RESULT_FEATURE_UNSUPPORTED;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->eventMask=flags;return SL_RESULT_SUCCESS;};return t;}
SLAndroidSimpleBufferQueueItf_ queueTable(){auto t=defaults_SLAndroidSimpleBufferQueueItf_();t.Enqueue=enqueue;t.Clear=clear;t.GetState=[](SLAndroidSimpleBufferQueueItf self,SLAndroidSimpleBufferQueueState* state){if(!state)return SL_RESULT_PARAMETER_INVALID;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);state->count=o->buffers.size();state->index=o->completed;return SL_RESULT_SUCCESS;};t.RegisterCallback=[](SLAndroidSimpleBufferQueueItf self,slAndroidSimpleBufferQueueCallback cb,void* context){auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->callback=cb;o->callbackContext=context;return SL_RESULT_SUCCESS;};return t;}
SLVolumeItf_ volumeTable(){auto t=defaults_SLVolumeItf_();t.SetVolumeLevel=[](SLVolumeItf self,SLmillibel gain){if(gain>0||gain<SL_MILLIBEL_MIN)return SL_RESULT_PARAMETER_INVALID;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->gain=gain;return SL_RESULT_SUCCESS;};t.GetVolumeLevel=[](SLVolumeItf self,SLmillibel* gain){if(!gain)return SL_RESULT_PARAMETER_INVALID;auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);*gain=o->gain;return SL_RESULT_SUCCESS;};t.GetMaxVolumeLevel=[](SLVolumeItf,SLmillibel* gain){if(!gain)return SL_RESULT_PARAMETER_INVALID;*gain=0;return SL_RESULT_SUCCESS;};t.SetMute=[](SLVolumeItf self,SLboolean mute){auto* o=owner(self);std::lock_guard<std::mutex> lock(o->mutex);o->muted=mute;return SL_RESULT_SUCCESS;};return t;}
SLAndroidConfigurationItf_ configTable(){auto t=defaults_SLAndroidConfigurationItf_();t.SetConfiguration=[](SLAndroidConfigurationItf,const SLchar* key,const void* value,SLuint32 size){if(!key||!value||size!=sizeof(SLuint32))return SL_RESULT_PARAMETER_INVALID;if(!strcmp(reinterpret_cast<const char*>(key),reinterpret_cast<const char*>(SL_ANDROID_KEY_STREAM_TYPE))&&*static_cast<const SLuint32*>(value)==SL_ANDROID_STREAM_MEDIA)return SL_RESULT_SUCCESS;return SL_RESULT_FEATURE_UNSUPPORTED;};return t;}
Object::Object(int type):kind(type){static const SLOutputMixItf_ mt={unsupported(SLOutputMixItf_{}.GetDestinationOutputDeviceIDs),unsupported(SLOutputMixItf_{}.RegisterDeviceChangeCallback),unsupported(SLOutputMixItf_{}.ReRoute)};mix={&mt,this};static const auto ot=objectTable();static const auto et=engineTable();static const auto pt=playTable();static const auto vt=volumeTable();static const auto qt=queueTable();static const auto ct=configTable();object={&ot,this};engine={&et,this};play={&pt,this};volume={&vt,this};queue={&qt,this};config={&ct,this};}
}
extern "C" SLresult slCreateEngine(SLObjectItf* out,SLuint32 options,const SLEngineOption* values,SLuint32 count,const SLInterfaceID*,const SLboolean* required){if(!out||options>16||count>32||(options&&!values)||(count&&!required))return SL_RESULT_PARAMETER_INVALID;for(unsigned i=0;i<options;i++)if(values[i].feature!=SL_ENGINEOPTION_THREADSAFE)return SL_RESULT_FEATURE_UNSUPPORTED;for(unsigned i=0;i<count;i++)if(required[i])return SL_RESULT_FEATURE_UNSUPPORTED;auto* o=new(std::nothrow) Object(0);if(!o)return SL_RESULT_MEMORY_FAILURE;*out=reinterpret_cast<SLObjectItf>(&o->object);return SL_RESULT_SUCCESS;}
