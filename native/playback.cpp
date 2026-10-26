// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <alsa/asoundlib.h>
#include <neaacdec.h>
#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <unordered_map>
#include <vector>
namespace {
struct Playback {
 std::mutex mutex;NeAACDecHandle decoder=nullptr;snd_pcm_t* pcm=nullptr;unsigned long rate=0;unsigned char channels=0;uint64_t written=0;bool closed=false;
 ~Playback(){if(decoder)NeAACDecClose(decoder);if(pcm)snd_pcm_close(pcm);}
};
std::mutex registryMutex;std::unordered_map<jlong,std::shared_ptr<Playback>> registry;jlong nextId=1;
std::shared_ptr<Playback> get(jlong id){std::lock_guard<std::mutex> lock(registryMutex);auto it=registry.find(id);if(it==registry.end())throw std::runtime_error("Playback handle is closed");return it->second;}
jlong add(std::shared_ptr<Playback> p){std::lock_guard<std::mutex> lock(registryMutex);if(registry.size()>=64)throw std::runtime_error("Playback handle limit");jlong id=nextId++;registry.emplace(id,p);return id;}
template<class F> auto checked(JNIEnv* env,F f)->decltype(f()){try{return f();}catch(const std::exception& e){jclass c=env->FindClass("java/lang/IllegalStateException");if(c)env->ThrowNew(c,e.what());return {};}}
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_decoderOpen(JNIEnv* env,jclass,jbyteArray config){return checked(env,[&]()->jlong{
 if(!config||env->GetArrayLength(config)<2||env->GetArrayLength(config)>64)throw std::invalid_argument("AAC config length");
 
 std::vector<unsigned char> bytes(env->GetArrayLength(config));env->GetByteArrayRegion(config,0,bytes.size(),reinterpret_cast<jbyte*>(bytes.data()));if(env->ExceptionCheck())return 0;
 auto p=std::make_shared<Playback>();p->decoder=NeAACDecOpen();if(!p->decoder)throw std::runtime_error("AAC decoder allocation");
 auto c=NeAACDecGetCurrentConfiguration(p->decoder);c->outputFormat=FAAD_FMT_16BIT;NeAACDecSetConfiguration(p->decoder,c);
 if(NeAACDecInit2(p->decoder,bytes.data(),bytes.size(),&p->rate,&p->channels)!=0||p->channels<1||p->channels>2||p->rate<8000||p->rate>48000)throw std::invalid_argument("Unsupported AAC format");
 return add(p);
});}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativePlayback_decoderFormat(JNIEnv* env,jclass,jlong id){return checked(env,[&]()->jintArray{
 auto p=get(id);std::lock_guard<std::mutex> lock(p->mutex);if(p->closed||!p->decoder)throw std::runtime_error("Decoder closed");
 jint values[]={static_cast<jint>(p->rate),p->channels};jintArray out=env->NewIntArray(2);if(out)env->SetIntArrayRegion(out,0,2,values);return out;
});}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativePlayback_decode(JNIEnv* env,jclass,jlong id,jbyteArray packet){return checked(env,[&]()->jbyteArray{
 if(!packet||env->GetArrayLength(packet)<1||env->GetArrayLength(packet)>65536)throw std::invalid_argument("AAC packet length");
 auto p=get(id);std::lock_guard<std::mutex> lock(p->mutex);if(p->closed||!p->decoder)throw std::runtime_error("Decoder closed");
 std::vector<unsigned char> bytes(env->GetArrayLength(packet));env->GetByteArrayRegion(packet,0,bytes.size(),reinterpret_cast<jbyte*>(bytes.data()));if(env->ExceptionCheck())return nullptr;
 NeAACDecFrameInfo info{};void* pcm=NeAACDecDecode(p->decoder,&info,bytes.data(),bytes.size());if(info.error)throw std::runtime_error(NeAACDecGetErrorMessage(info.error));
 if(info.samples>8192||(info.samples&&(!pcm||info.channels!=p->channels||info.samplerate!=p->rate)))throw std::runtime_error("AAC output format changed");
 jbyteArray out=env->NewByteArray(info.samples*2);if(out&&info.samples)env->SetByteArrayRegion(out,0,info.samples*2,static_cast<jbyte*>(pcm));return out;
});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_trackOpen(JNIEnv* env,jclass,jint rate,jint channels,jint bytes){return checked(env,[&]()->jlong{
 if(rate<8000||rate>192000||channels<1||channels>2||bytes<2||bytes>4*1024*1024)throw std::invalid_argument("PCM output configuration");
 auto p=std::make_shared<Playback>();p->rate=rate;p->channels=channels;const char* device=std::getenv("SHIRAZ_AUDIO_DEVICE");if(!device||!*device)device="default";
 int result=snd_pcm_open(&p->pcm,device,SND_PCM_STREAM_PLAYBACK,SND_PCM_NONBLOCK);if(result>=0)result=snd_pcm_set_params(p->pcm,SND_PCM_FORMAT_S16_LE,SND_PCM_ACCESS_RW_INTERLEAVED,channels,rate,1,100000);if(result<0)throw std::runtime_error(snd_strerror(result));return add(p);
});}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativePlayback_write(JNIEnv* env,jclass,jlong id,jbyteArray pcm,jint offset,jint length,jboolean blocking,jfloat gain){return checked(env,[&]()->jint{
 if(!pcm||offset<0||length<0||length>4*1024*1024||jlong(offset)+length>env->GetArrayLength(pcm))throw std::invalid_argument("PCM write bounds");
 auto p=get(id);std::lock_guard<std::mutex> lock(p->mutex);if(p->closed||!p->pcm)throw std::runtime_error("Track closed");
 length-=length%(p->channels*2);std::vector<int16_t> samples(length/2);env->GetByteArrayRegion(pcm,offset,length,reinterpret_cast<jbyte*>(samples.data()));if(env->ExceptionCheck())return -3;
 gain=std::max(0.0f,std::min(1.0f,gain));for(auto& sample:samples)sample=static_cast<int16_t>(sample*gain);
 snd_pcm_sframes_t n=0;for(int attempt=0;attempt<(blocking?50:1);++attempt){n=snd_pcm_writei(p->pcm,samples.data(),samples.size()/p->channels);if(n==-EAGAIN){if(blocking)snd_pcm_wait(p->pcm,20);continue;}if(n<0){if(snd_pcm_recover(p->pcm,n,1)<0)return -6;continue;}break;}
 if(n<0)return 0;
 p->written+=n;return n*p->channels*2;
});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_position(JNIEnv* env,jclass,jlong id){return checked(env,[&]()->jlong{auto p=get(id);std::lock_guard<std::mutex> lock(p->mutex);snd_pcm_sframes_t delay=0;if(p->closed||!p->pcm)return 0;if(snd_pcm_delay(p->pcm,&delay)<0)delay=0;return p->written-std::min<uint64_t>(p->written,std::max<snd_pcm_sframes_t>(0,delay));});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePlayback_control(JNIEnv* env,jclass,jlong id,jint action){checked(env,[&](){auto p=get(id);std::lock_guard<std::mutex> lock(p->mutex);if(p->closed||!p->pcm)throw std::runtime_error("Track closed");if(action==0){snd_pcm_drop(p->pcm);snd_pcm_prepare(p->pcm);p->written=0;}else if(action==1){snd_pcm_pause(p->pcm,1);}else if(action==2){snd_pcm_pause(p->pcm,0);}else if(action==3&&p->written>0&&snd_pcm_state(p->pcm)==SND_PCM_STATE_PREPARED){int result=snd_pcm_start(p->pcm);if(result<0)throw std::runtime_error(snd_strerror(result));}return 0;});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePlayback_close(JNIEnv* env,jclass,jlong id){checked(env,[&](){std::shared_ptr<Playback> p;{std::lock_guard<std::mutex> lock(registryMutex);auto it=registry.find(id);if(it==registry.end())return 0;p=it->second;registry.erase(it);}std::lock_guard<std::mutex> lock(p->mutex);p->closed=true;if(p->pcm)snd_pcm_drop(p->pcm);return 0;});}

// libsndfile decodes bounded local audio assets through an in-memory virtual file.
#include <sndfile.h>
#include <cstring>
namespace {
struct AudioFile {std::vector<unsigned char> bytes;sf_count_t at=0;};
sf_count_t audioLength(void* p){return static_cast<AudioFile*>(p)->bytes.size();}
sf_count_t audioSeek(sf_count_t offset,int whence,void* p){auto& f=*static_cast<AudioFile*>(p);sf_count_t base=whence==SEEK_SET?0:whence==SEEK_CUR?f.at:whence==SEEK_END?sf_count_t(f.bytes.size()):-1;if(base<0||offset< -base||offset>sf_count_t(f.bytes.size())-base)return -1;return f.at=base+offset;}
sf_count_t audioRead(void* out,sf_count_t count,void* p){auto& f=*static_cast<AudioFile*>(p);if(count<0)return 0;count=std::min(count,sf_count_t(f.bytes.size())-f.at);std::memcpy(out,f.bytes.data()+f.at,count);f.at+=count;return count;}
sf_count_t audioWrite(const void*,sf_count_t,void*){return 0;}
sf_count_t audioTell(void* p){return static_cast<AudioFile*>(p)->at;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativePlayback_decodeFile(JNIEnv* env,jclass,jbyteArray data,jintArray format){return checked(env,[&]()->jbyteArray{
 if(!data||!format||env->GetArrayLength(format)!=2||env->GetArrayLength(data)>16*1024*1024)throw std::invalid_argument("Audio asset bounds");
 AudioFile file;file.bytes.resize(env->GetArrayLength(data));if(file.bytes.empty())throw std::invalid_argument("Empty audio asset");env->GetByteArrayRegion(data,0,file.bytes.size(),reinterpret_cast<jbyte*>(file.bytes.data()));if(env->ExceptionCheck())return nullptr;
 SF_VIRTUAL_IO io={audioLength,audioSeek,audioRead,audioWrite,audioTell};SF_INFO info={};
 std::unique_ptr<SNDFILE,decltype(&sf_close)> decoder(sf_open_virtual(&io,SFM_READ,&info,&file),sf_close);
 if(!decoder||info.channels<1||info.channels>2||info.samplerate<8000||info.samplerate>192000||info.frames<1||info.frames>32*1024*1024/info.channels)throw std::invalid_argument("Unsupported audio format or decoded size");
 std::vector<short> samples(info.frames*info.channels);sf_count_t frames=sf_readf_short(decoder.get(),samples.data(),info.frames);if(frames!=info.frames||sf_error(decoder.get()))throw std::runtime_error("Truncated audio asset");
 std::vector<jbyte> pcm(samples.size()*2);for(size_t i=0;i<samples.size();++i){pcm[2*i]=samples[i]&255;pcm[2*i+1]=(uint16_t(samples[i])>>8)&255;}
 jint values[]={info.samplerate,info.channels};env->SetIntArrayRegion(format,0,2,values);jbyteArray out=env->NewByteArray(pcm.size());if(out)env->SetByteArrayRegion(out,0,pcm.size(),pcm.data());return out;
});}
