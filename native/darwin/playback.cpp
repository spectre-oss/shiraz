// SPDX-License-Identifier: GPL-3.0-only
#include <AudioToolbox/AudioToolbox.h>
#include <jni.h>
#include "shiraz/darwin/audio_clock.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr OSStatus needMore=0x6d6f7265;
AudioStreamBasicDescription pcmFormat(int rate,int channels){AudioStreamBasicDescription f{};f.mSampleRate=rate;f.mFormatID=kAudioFormatLinearPCM;f.mFormatFlags=kAudioFormatFlagIsSignedInteger|kAudioFormatFlagIsPacked;f.mBytesPerFrame=f.mBytesPerPacket=channels*2;f.mFramesPerPacket=1;f.mChannelsPerFrame=channels;f.mBitsPerChannel=16;return f;}
void check(OSStatus status,const char *operation){if(status)throw std::runtime_error(std::string(operation)+" (CoreAudio "+std::to_string(status)+")");}
struct Playback {
  enum Kind {Track,Decoder} kind;
  std::mutex control,mutex;
  std::condition_variable changed;
  int rate=0,channels=0,bufferBytes=0;
  bool closed=false,failed=false,wantsPlay=false,running=false,resetting=false;
  uint64_t written=0,completed=0;
  AudioQueueRef queue=nullptr;
  std::deque<AudioQueueBufferRef> available;
  AudioConverterRef decoder=nullptr;
  std::vector<uint8_t> packet;
  bool packetSupplied=false;
  AudioStreamPacketDescription description{};
  explicit Playback(Kind value):kind(value){}
  void stop(){std::lock_guard<std::mutex> owner(control);{std::lock_guard<std::mutex> lock(mutex);if(closed)return;closed=true;resetting=true;changed.notify_all();}if(queue){AudioQueueDispose(queue,true);queue=nullptr;}if(decoder){AudioConverterDispose(decoder);decoder=nullptr;}}
  ~Playback(){stop();}
};
std::mutex registryMutex;std::map<jlong,std::shared_ptr<Playback>> registry;jlong nextHandle=1;
jlong add(std::shared_ptr<Playback> state){std::lock_guard<std::mutex> lock(registryMutex);if(registry.size()>=64)throw std::runtime_error("Playback handle limit");jlong handle=nextHandle++;registry.emplace(handle,std::move(state));return handle;}
std::shared_ptr<Playback> get(jlong handle,Playback::Kind kind){std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(handle);if(found==registry.end()||found->second->kind!=kind)throw std::runtime_error("Playback handle is closed or has the wrong type");return found->second;}
void error(JNIEnv *env,const char *type,const char *message){if(env->ExceptionCheck())return;jclass c=env->FindClass(type);if(c){env->ThrowNew(c,message);env->DeleteLocalRef(c);}}
template<class F> auto checked(JNIEnv *env,F function)->decltype(function()){try{return function();}catch(const std::bad_alloc &){error(env,"java/lang/OutOfMemoryError","Playback allocation failed");}catch(const std::invalid_argument &e){error(env,"java/lang/IllegalArgumentException",e.what());}catch(const std::exception &e){error(env,"java/lang/IllegalStateException",e.what());}return {};}
void outputReady(void *context,AudioQueueRef,AudioQueueBufferRef buffer){auto &state=*static_cast<Playback *>(context);try{std::lock_guard<std::mutex> lock(state.mutex);if(!state.resetting)state.completed+=buffer->mAudioDataByteSize/(state.channels*2);state.available.push_back(buffer);state.changed.notify_all();}catch(...){std::lock_guard<std::mutex> lock(state.mutex);state.failed=true;state.changed.notify_all();}}
void createQueue(Playback &state){auto format=pcmFormat(state.rate,state.channels);check(AudioQueueNewOutput(&format,outputReady,&state,nullptr,nullptr,0,&state.queue),"Create PCM output queue");for(int i=0;i<4;i++){AudioQueueBufferRef buffer=nullptr;check(AudioQueueAllocateBuffer(state.queue,state.bufferBytes,&buffer),"Allocate PCM output buffer");state.available.push_back(buffer);}state.running=false;state.wantsPlay=false;state.resetting=false;state.written=state.completed=0;}
OSStatus compressedInput(AudioConverterRef,UInt32 *count,AudioBufferList *data,AudioStreamPacketDescription **description,void *context){auto &state=*static_cast<Playback *>(context);if(state.packetSupplied){*count=0;return needMore;}state.packetSupplied=true;*count=1;data->mNumberBuffers=1;data->mBuffers[0]={UInt32(state.channels),UInt32(state.packet.size()),state.packet.data()};state.description={0,0,UInt32(state.packet.size())};*description=&state.description;return noErr;}
std::vector<uint8_t> aacCookie(const std::vector<uint8_t>& config){std::vector<uint8_t> decoder={0x40,0x15,0,0,0,0,0,0,0,0,0,0,0};decoder.push_back(5);decoder.push_back(uint8_t(config.size()));decoder.insert(decoder.end(),config.begin(),config.end());std::vector<uint8_t> es={0,0,0,4,uint8_t(decoder.size())};es.insert(es.end(),decoder.begin(),decoder.end());es.insert(es.end(),{6,1,2});std::vector<uint8_t> cookie={3,uint8_t(es.size())};cookie.insert(cookie.end(),es.begin(),es.end());return cookie;}
struct MemoryFile {const uint8_t *bytes;size_t size;AudioFileID file=nullptr;ExtAudioFileRef decoded=nullptr;~MemoryFile(){if(decoded)ExtAudioFileDispose(decoded);if(file)AudioFileClose(file);}};
OSStatus readFile(void *context,SInt64 position,UInt32 requested,void *output,UInt32 *actual){auto &file=*static_cast<MemoryFile *>(context);if(position<0||uint64_t(position)>file.size){*actual=0;return kAudioFilePositionError;}*actual=UInt32(std::min(size_t(requested),file.size-size_t(position)));if(*actual)std::memcpy(output,file.bytes+position,*actual);return noErr;}
SInt64 fileSize(void *context){return SInt64(static_cast<MemoryFile *>(context)->size);}
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_decoderOpen(JNIEnv *env,jclass,jbyteArray configuration){return checked(env,[&]()->jlong{
  if(!configuration||env->GetArrayLength(configuration)<2||env->GetArrayLength(configuration)>64)throw std::invalid_argument("AAC configuration length");
  std::vector<uint8_t> config(env->GetArrayLength(configuration));env->GetByteArrayRegion(configuration,0,jsize(config.size()),reinterpret_cast<jbyte *>(config.data()));if(env->ExceptionCheck())return 0;
  unsigned profile=config[0]>>3,index=(config[0]&7)<<1|config[1]>>7,channels=(config[1]>>3)&15;const int rates[]={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};
  if(profile!=2||index>=12||channels<1||channels>2)throw std::invalid_argument("Only bounded AAC-LC mono/stereo configuration is supported");
  auto state=std::make_shared<Playback>(Playback::Decoder);state->rate=rates[index];state->channels=channels;
  AudioStreamBasicDescription input{};input.mFormatID=kAudioFormatMPEG4AAC;input.mFormatFlags=kMPEG4Object_AAC_LC;input.mSampleRate=state->rate;input.mChannelsPerFrame=channels;input.mFramesPerPacket=1024;auto output=pcmFormat(state->rate,channels);
  check(AudioConverterNew(&input,&output,&state->decoder),"Create AAC decoder");auto cookie=aacCookie(config);check(AudioConverterSetProperty(state->decoder,kAudioConverterDecompressionMagicCookie,UInt32(cookie.size()),cookie.data()),"Configure AAC decoder");return add(state);
});}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativePlayback_decoderFormat(JNIEnv *env,jclass,jlong handle){return checked(env,[&]()->jintArray{auto state=get(handle,Playback::Decoder);jint values[]={state->rate,state->channels};auto out=env->NewIntArray(2);if(out)env->SetIntArrayRegion(out,0,2,values);return out;});}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativePlayback_decode(JNIEnv *env,jclass,jlong handle,jbyteArray packet){return checked(env,[&]()->jbyteArray{
  if(!packet||env->GetArrayLength(packet)<1||env->GetArrayLength(packet)>65536)throw std::invalid_argument("AAC packet size");auto state=get(handle,Playback::Decoder);std::lock_guard<std::mutex> lock(state->control);if(state->closed)throw std::runtime_error("AAC decoder closed");
  // The converter may retain a packet pointer until its next input callback.
  auto previous=std::move(state->packet);state->packet.resize(env->GetArrayLength(packet));env->GetByteArrayRegion(packet,0,jsize(state->packet.size()),reinterpret_cast<jbyte *>(state->packet.data()));if(env->ExceptionCheck())return nullptr;state->packetSupplied=false;
  std::vector<jbyte> pcm(size_t(4096*state->channels*2));AudioBufferList output{};output.mNumberBuffers=1;output.mBuffers[0]={UInt32(state->channels),UInt32(pcm.size()),pcm.data()};UInt32 frames=4096;
  OSStatus status=AudioConverterFillComplexBuffer(state->decoder,compressedInput,state.get(),&frames,&output,nullptr);if(status!=needMore)check(status,"Decode AAC packet");if(frames>4096||output.mBuffers[0].mDataByteSize>pcm.size())throw std::runtime_error("AAC output bounds");size_t count=size_t(frames)*state->channels*2;auto out=env->NewByteArray(jsize(count));if(out&&count)env->SetByteArrayRegion(out,0,jsize(count),pcm.data());return out;
});}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativePlayback_decodeFile(JNIEnv *env,jclass,jbyteArray input,jintArray format){return checked(env,[&]()->jbyteArray{
  if(!input||!format||env->GetArrayLength(format)<2||env->GetArrayLength(input)<1||env->GetArrayLength(input)>64*1024*1024)throw std::invalid_argument("Audio file input bounds");std::vector<uint8_t> bytes(env->GetArrayLength(input));env->GetByteArrayRegion(input,0,jsize(bytes.size()),reinterpret_cast<jbyte *>(bytes.data()));if(env->ExceptionCheck())return nullptr;
  MemoryFile file{bytes.data(),bytes.size()};check(AudioFileOpenWithCallbacks(&file,readFile,nullptr,fileSize,nullptr,0,&file.file),"Open bounded audio data");check(ExtAudioFileWrapAudioFileID(file.file,false,&file.decoded),"Open audio decoder");AudioStreamBasicDescription source{};UInt32 size=sizeof(source);check(ExtAudioFileGetProperty(file.decoded,kExtAudioFileProperty_FileDataFormat,&size,&source),"Read audio format");
  if(!std::isfinite(source.mSampleRate)||source.mSampleRate<8000||source.mSampleRate>192000||source.mChannelsPerFrame<1||source.mChannelsPerFrame>2)throw std::invalid_argument("Audio file output format");auto pcm=pcmFormat(int(source.mSampleRate),int(source.mChannelsPerFrame));check(ExtAudioFileSetProperty(file.decoded,kExtAudioFileProperty_ClientDataFormat,sizeof(pcm),&pcm),"Select PCM file output");
  std::vector<jbyte> decoded,block(size_t(4096*pcm.mBytesPerFrame));for(;;){AudioBufferList output{};output.mNumberBuffers=1;output.mBuffers[0]={pcm.mChannelsPerFrame,UInt32(block.size()),block.data()};UInt32 frames=4096;check(ExtAudioFileRead(file.decoded,&frames,&output),"Decode bounded audio file");if(!frames)break;size_t count=size_t(frames)*pcm.mBytesPerFrame;if(count>block.size()||decoded.size()+count>64*1024*1024)throw std::runtime_error("Decoded audio exceeds 64 MiB");decoded.insert(decoded.end(),block.begin(),block.begin()+count);}
  jint values[]={jint(pcm.mSampleRate),jint(pcm.mChannelsPerFrame)};env->SetIntArrayRegion(format,0,2,values);if(env->ExceptionCheck())return nullptr;auto out=env->NewByteArray(jsize(decoded.size()));if(out&&!decoded.empty())env->SetByteArrayRegion(out,0,jsize(decoded.size()),decoded.data());return out;
});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_trackOpen(JNIEnv *env,jclass,jint rate,jint channels,jint bytes){return checked(env,[&]()->jlong{if(rate<8000||rate>192000||channels<1||channels>2||bytes<2||bytes>4*1024*1024)throw std::invalid_argument("PCM track configuration");auto state=std::make_shared<Playback>(Playback::Track);state->rate=rate;state->channels=channels;state->bufferBytes=std::clamp(bytes/4,rate*channels*2/100,rate*channels*2/2);state->bufferBytes-=state->bufferBytes%(channels*2);createQueue(*state);return add(state);});}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativePlayback_write(JNIEnv *env,jclass,jlong handle,jbyteArray pcm,jint offset,jint length,jboolean blocking,jfloat gain){return checked(env,[&]()->jint{
  if(!pcm||offset<0||length<0||offset>env->GetArrayLength(pcm)-length||length>4*1024*1024||!std::isfinite(gain))throw std::invalid_argument("PCM write bounds");auto state=get(handle,Playback::Track);if(length%(state->channels*2))throw std::invalid_argument("PCM write alignment");gain=std::clamp(gain,0.f,1.f);int written=0;auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(2);
  while(written<length){std::unique_lock<std::mutex> owner(state->control);std::unique_lock<std::mutex> lock(state->mutex);if(state->closed||state->failed)return written?written:-6;
    if(state->available.empty()){owner.unlock();if(!blocking)return written;if(!state->changed.wait_until(lock,deadline,[&]{return state->closed||state->failed||!state->available.empty();}))return written;continue;}
    auto buffer=state->available.front();state->available.pop_front();int count=std::min(length-written,state->bufferBytes);count-=count%(state->channels*2);lock.unlock();env->GetByteArrayRegion(pcm,offset+written,count,static_cast<jbyte *>(buffer->mAudioData));if(env->ExceptionCheck())return written;
    auto samples=static_cast<int16_t *>(buffer->mAudioData);for(int i=0;i<count/2;i++)samples[i]=int16_t(samples[i]*gain);buffer->mAudioDataByteSize=count;OSStatus status=AudioQueueEnqueueBuffer(state->queue,buffer,0,nullptr);if(status){std::lock_guard<std::mutex> failed(state->mutex);state->failed=true;state->changed.notify_all();return written?written:-6;}
    state->written+=count/(state->channels*2);written+=count;if(state->wantsPlay&&!state->running){check(AudioQueueStart(state->queue,nullptr),"Start PCM output");state->running=true;}
  }return written;
});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePlayback_position(JNIEnv *env,jclass,jlong handle){return checked(env,[&]()->jlong{auto state=get(handle,Playback::Track);std::lock_guard<std::mutex> owner(state->control);if(state->closed)return 0;AudioTimeStamp time{};Boolean discontinuity=false;if(!AudioQueueGetCurrentTime(state->queue,nullptr,&time,&discontinuity)&&(time.mFlags&kAudioTimeStampSampleTimeValid)&&std::isfinite(time.mSampleTime))return jlong(std::min(state->written,uint64_t(std::max(0.,time.mSampleTime))));std::lock_guard<std::mutex> lock(state->mutex);return jlong(std::min(state->written,state->completed));});}
extern "C" JNIEXPORT jlongArray JNICALL Java_shiraz_runtime_NativePlayback_timestamp(JNIEnv *env,jclass,jlong handle){return checked(env,[&]()->jlongArray{
  auto state=get(handle,Playback::Track);std::lock_guard<std::mutex> owner(state->control);if(state->closed||!state->running)return nullptr;AudioTimeStamp time{};Boolean discontinuity=false;
  if(AudioQueueGetCurrentTime(state->queue,nullptr,&time,&discontinuity)||discontinuity||(time.mFlags&(kAudioTimeStampSampleTimeValid|kAudioTimeStampHostTimeValid))!=(kAudioTimeStampSampleTimeValid|kAudioTimeStampHostTimeValid)||!std::isfinite(time.mSampleTime)||time.mSampleTime<0)return nullptr;
  uint64_t nanos=shiraz::darwin::audio_monotonic_nanos(time.mHostTime);if(!nanos)return nullptr;
  jlong values[]={jlong(std::min(state->written,uint64_t(time.mSampleTime))),jlong(nanos)};auto out=env->NewLongArray(2);if(out)env->SetLongArrayRegion(out,0,2,values);return out;
});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePlayback_control(JNIEnv *env,jclass,jlong handle,jint action){checked(env,[&]()->int{auto state=get(handle,Playback::Track);std::lock_guard<std::mutex> owner(state->control);if(state->closed)throw std::runtime_error("PCM track closed");if(action==0){ {std::lock_guard<std::mutex> lock(state->mutex);state->resetting=true;}check(AudioQueueDispose(state->queue,true),"Reset PCM output");state->queue=nullptr;std::lock_guard<std::mutex> lock(state->mutex);state->available.clear();createQueue(*state);state->changed.notify_all();}else if(action==1){state->wantsPlay=false;if(state->running)check(AudioQueuePause(state->queue),"Pause PCM output");state->running=false;}else if(action==2||action==3){if(action==2)state->wantsPlay=true;if(state->wantsPlay&&state->written&&!state->running){check(AudioQueueStart(state->queue,nullptr),"Resume PCM output");state->running=true;}}else throw std::invalid_argument("PCM control action");return 0;});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePlayback_close(JNIEnv *env,jclass,jlong handle){checked(env,[&]()->int{std::shared_ptr<Playback> state;{std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(handle);if(found==registry.end())return 0;state=found->second;registry.erase(found);}state->stop();return 0;});}
