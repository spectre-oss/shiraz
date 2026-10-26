// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <alsa/asoundlib.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <algorithm>
namespace {
using Clock=std::chrono::steady_clock;
struct Playback {std::atomic<bool> canceled{false},running{false};std::mutex mutex;std::condition_variable changed;};
std::mutex registryMutex;
std::unordered_map<jlong,std::shared_ptr<Playback>> registry;
jlong nextId=1;
void error(JNIEnv* env,const char* type,const char* message){if(env->ExceptionCheck())return;auto cls=env->FindClass(type);if(cls){env->ThrowNew(cls,message);env->DeleteLocalRef(cls);}}
std::shared_ptr<Playback> find(jlong id){std::lock_guard<std::mutex> lock(registryMutex);auto i=registry.find(id);if(i==registry.end())throw std::invalid_argument("Unknown speech playback handle");return i->second;}
void cancel(const std::shared_ptr<Playback>& p){p->canceled=true;p->changed.notify_all();}
bool wait(const std::shared_ptr<Playback>& p,Clock::time_point until){std::unique_lock<std::mutex> lock(p->mutex);p->changed.wait_until(lock,until,[&]{return p->canceled.load();});return p->canceled;}
struct Active{std::shared_ptr<Playback> p;explicit Active(std::shared_ptr<Playback> value):p(std::move(value)){if(p->running.exchange(true))throw std::invalid_argument("Speech handle is already playing");}~Active(){p->running=false;}};
struct PCM {snd_pcm_t* value=nullptr;~PCM(){if(value){snd_pcm_drop(value);snd_pcm_close(value);}}};
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSpeech_createPlayback(JNIEnv* env,jclass){
 try{std::lock_guard<std::mutex> lock(registryMutex);if(registry.size()>=64||nextId==INT64_MAX)throw std::invalid_argument("Speech playback handle limit");jlong id=nextId++;registry.emplace(id,std::make_shared<Playback>());return id;}
 catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Speech playback allocation");}catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}return 0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSpeech_isPlaybackRunning(JNIEnv* env,jclass,jlong id){try{return find(id)->running.load()?JNI_TRUE:JNI_FALSE;}catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());return JNI_FALSE;}}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSpeech_cancelPlayback(JNIEnv* env,jclass,jlong id){try{cancel(find(id));}catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSpeech_releasePlayback(JNIEnv*,jclass,jlong id){std::shared_ptr<Playback> p;{std::lock_guard<std::mutex> lock(registryMutex);auto i=registry.find(id);if(i==registry.end())return;p=i->second;registry.erase(i);}cancel(p);}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSpeech_play(JNIEnv* env,jclass,jlong id,jshortArray input,jint rate){
 try{
  auto p=find(id);Active active(p);
  if(!input||rate<8000||rate>96000||env->GetArrayLength(input)>int64_t(rate)*120)throw std::invalid_argument("Speech playback PCM bounds");
  if(p->canceled)return 1;
  std::vector<short> samples(static_cast<size_t>(env->GetArrayLength(input)));if(samples.empty())return 0;
  env->GetShortArrayRegion(input,0,static_cast<jsize>(samples.size()),samples.data());if(env->ExceptionCheck())return -1;
  PCM pcm;const char* device=std::getenv("SHIRAZ_AUDIO_DEVICE");if(!device||!*device)device="default";
  if(snd_pcm_open(&pcm.value,device,SND_PCM_STREAM_PLAYBACK,SND_PCM_NONBLOCK)<0)return -1;
  if(snd_pcm_set_params(pcm.value,SND_PCM_FORMAT_S16_LE,SND_PCM_ACCESS_RW_INTERLEAVED,1,rate,1,40000)<0)return -1;
  size_t done=0;const auto start=Clock::now();auto deadline=start+std::chrono::seconds(2);
  while(done<samples.size()){
   if(p->canceled)return 1;
   size_t chunk=std::min(samples.size()-done,static_cast<size_t>(std::max(1,rate/100)));
   auto count=snd_pcm_writei(pcm.value,samples.data()+done,chunk);
   if(count>0){done+=static_cast<size_t>(count);deadline=Clock::now()+std::chrono::seconds(2);if(wait(p,start+std::chrono::nanoseconds(int64_t(done)*1000000000/rate)))return 1;}
   else{
    if(count==-EPIPE){if(snd_pcm_prepare(pcm.value)<0)return -1;}
    else if(count!=-EAGAIN&&count!=-EINTR&&count!=0)return -1;
    if(Clock::now()>=deadline)return -1;
    if(wait(p,Clock::now()+std::chrono::milliseconds(5)))return 1;
   }
  }
  deadline=Clock::now()+std::chrono::seconds(2);
  for(;;){if(p->canceled)return 1;int status=snd_pcm_drain(pcm.value);if(status==0)return 0;if(status!=-EAGAIN&&status!=-EINTR)return -1;if(Clock::now()>=deadline)return -1;if(wait(p,Clock::now()+std::chrono::milliseconds(5)))return 1;}
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Speech playback PCM allocation");}
 catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}return -1;
}
