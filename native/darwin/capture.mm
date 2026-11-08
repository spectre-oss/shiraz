// SPDX-License-Identifier: GPL-3.0-only
#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>
#import <CoreMedia/CoreMedia.h>
#import <CoreVideo/CoreVideo.h>
#include <jni.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <vector>

namespace {
struct Media {
  enum Kind { Microphone, Camera, Encoder } kind;
  std::mutex mutex;
  std::condition_variable changed;
  bool closed=false,failed=false,tapInstalled=false;
  int rate=0,channels=0,width=0,height=0;
  uint64_t frame=0,readFrame=0;
  std::deque<uint8_t> pcm;
  std::vector<jint> pixels;
  AVAudioEngine *engine=nil;
  AVAudioConverter *converter=nil;
  AVCaptureSession *session=nil;
  id delegate=nil;
  dispatch_queue_t queue=nullptr;
  AudioConverterRef encoder=nullptr;
  explicit Media(Kind value):kind(value){}
  void stop(){
    {std::lock_guard<std::mutex> lock(mutex);if(closed)return;closed=true;changed.notify_all();}
    // Do not hold the sample mutex while joining framework callbacks.
    if(engine){if(tapInstalled)[engine.inputNode removeTapOnBus:0];[engine stop];engine=nil;converter=nil;}
    if(session){[session stopRunning];session=nil;delegate=nil;queue=nullptr;}
    if(encoder){AudioConverterDispose(encoder);encoder=nullptr;}
    std::lock_guard<std::mutex> lock(mutex);pcm.clear();pixels.clear();
  }
  ~Media(){stop();}
};
std::mutex registryMutex;
std::map<jlong,std::shared_ptr<Media>> registry;
jlong nextHandle=1;
void error(JNIEnv *env,const char *type,const char *message){if(env->ExceptionCheck())return;jclass c=env->FindClass(type);if(c){env->ThrowNew(c,message);env->DeleteLocalRef(c);}}
bool allowed(JNIEnv *env,const char *permission){
  jclass c=env->FindClass("shiraz/runtime/CapturePermissions");if(!c)return false;
  jmethodID method=env->GetStaticMethodID(c,"authorize","(Ljava/lang/String;)V");
  jstring value=method?env->NewStringUTF(permission):nullptr;
  if(value){env->CallStaticVoidMethod(c,method,value);env->DeleteLocalRef(value);}env->DeleteLocalRef(c);return !env->ExceptionCheck();
}
template<class F> auto checked(JNIEnv *env,F function)->decltype(function()){
  @autoreleasepool {try{return function();}catch(const std::bad_alloc &){error(env,"java/lang/OutOfMemoryError","Capture allocation failed");}catch(const std::invalid_argument &e){error(env,"java/lang/IllegalArgumentException",e.what());}catch(const std::exception &e){error(env,"java/lang/IllegalStateException",e.what());}return {};}
}
bool hostPermission(JNIEnv *env,AVMediaType type,bool request){
  auto status=[AVCaptureDevice authorizationStatusForMediaType:type];
  if(status==AVAuthorizationStatusNotDetermined&&request){
    NSString *key=[type isEqualToString:AVMediaTypeVideo]?@"NSCameraUsageDescription":@"NSMicrophoneUsageDescription";
    id description=[[NSBundle mainBundle] objectForInfoDictionaryKey:key];
    if(![description isKindOfClass:[NSString class]]||![description length]){error(env,"java/lang/IllegalStateException","Host capture requires a Shiraz app bundle with camera/microphone usage descriptions");return false;}
    struct Decision {std::mutex mutex;std::condition_variable changed;bool complete=false;};auto decision=std::make_shared<Decision>();
    [AVCaptureDevice requestAccessForMediaType:type completionHandler:^(BOOL){std::lock_guard<std::mutex> lock(decision->mutex);decision->complete=true;decision->changed.notify_all();}];
    std::unique_lock<std::mutex> lock(decision->mutex);
    if(!decision->changed.wait_for(lock,std::chrono::seconds(60),[&]{return decision->complete;})){error(env,"java/lang/SecurityException","macOS capture permission request timed out");return false;}
    status=[AVCaptureDevice authorizationStatusForMediaType:type];
  }
  if(status!=AVAuthorizationStatusAuthorized){error(env,"java/lang/SecurityException","macOS camera or microphone permission is not granted");return false;}return true;
}
jlong add(std::shared_ptr<Media> state){std::lock_guard<std::mutex> lock(registryMutex);if(registry.size()>=16)throw std::runtime_error("Capture handle limit");jlong handle=nextHandle++;registry.emplace(handle,std::move(state));return handle;}
std::shared_ptr<Media> get(jlong handle,Media::Kind kind){std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(handle);if(found==registry.end()||found->second->kind!=kind)throw std::runtime_error("Capture handle is closed or has the wrong type");return found->second;}
void videoFrame(const std::shared_ptr<Media>& state,CMSampleBufferRef sample){
  CVPixelBufferRef image=CMSampleBufferGetImageBuffer(sample);if(!image)return;
  size_t w=CVPixelBufferGetWidth(image),h=CVPixelBufferGetHeight(image);
  if(!w||!h||w>1920||h>1080||CVPixelBufferGetPixelFormatType(image)!=kCVPixelFormatType_32BGRA)return;
  if(CVPixelBufferLockBaseAddress(image,kCVPixelBufferLock_ReadOnly))return;
  try{
    std::vector<jint> pixels(w*h+2);pixels[0]=jint(w);pixels[1]=jint(h);
    auto *bytes=static_cast<const uint8_t *>(CVPixelBufferGetBaseAddress(image));size_t stride=CVPixelBufferGetBytesPerRow(image);
    if(!bytes||stride<w*4){CVPixelBufferUnlockBaseAddress(image,kCVPixelBufferLock_ReadOnly);return;}
    for(size_t y=0;y<h;y++)for(size_t x=0;x<w;x++){auto p=bytes+y*stride+x*4;pixels[2+y*w+x]=jint(0xff000000u|uint32_t(p[2])<<16|uint32_t(p[1])<<8|p[0]);}
    std::lock_guard<std::mutex> lock(state->mutex);if(!state->closed){state->width=int(w);state->height=int(h);state->pixels=std::move(pixels);state->frame++;state->changed.notify_all();}
  }catch(...){std::lock_guard<std::mutex> lock(state->mutex);state->failed=true;state->changed.notify_all();}
  CVPixelBufferUnlockBaseAddress(image,kCVPixelBufferLock_ReadOnly);
}
}

@interface ShirazCameraSamples : NSObject<AVCaptureVideoDataOutputSampleBufferDelegate> {
@public std::weak_ptr<Media> owner;
}
@end
@implementation ShirazCameraSamples
- (void)captureOutput:(AVCaptureOutput *)output didOutputSampleBuffer:(CMSampleBufferRef)sample fromConnection:(AVCaptureConnection *)connection {
  (void)output;(void)connection;if(auto state=owner.lock())videoFrame(state,sample);
}
@end

extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeCapture_cameraCount(JNIEnv *env,jclass){return checked(env,[]()->jint{return [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo]?1:0;});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_microphoneOpen(JNIEnv *env,jclass,jint rate,jint channels){return checked(env,[&]()->jlong{
  if(rate<8000||rate>96000||channels<1||channels>2)throw std::invalid_argument("Microphone PCM configuration");
  if(!allowed(env,"android.permission.RECORD_AUDIO")||!hostPermission(env,AVMediaTypeAudio,true))return 0;
  auto state=std::make_shared<Media>(Media::Microphone);state->rate=rate;state->channels=channels;state->engine=[AVAudioEngine new];
  AVAudioInputNode *input=state->engine.inputNode;AVAudioFormat *hardware=[input outputFormatForBus:0];
  if(!hardware.channelCount||hardware.sampleRate<=0)throw std::runtime_error("No host microphone input format");
  AVAudioFormat *target=[[AVAudioFormat alloc] initWithCommonFormat:AVAudioPCMFormatInt16 sampleRate:rate channels:channels interleaved:YES];
  state->converter=[[AVAudioConverter alloc] initFromFormat:hardware toFormat:target];if(!state->converter)throw std::runtime_error("Microphone PCM conversion unavailable");
  AVAudioConverter *converter=state->converter;
  std::weak_ptr<Media> weak=state;
  AVAudioNodeTapBlock tap=^(AVAudioPCMBuffer *buffer,AVAudioTime *){
    auto current=weak.lock();if(!current)return;
    @autoreleasepool {try{
      AVAudioFrameCount capacity=AVAudioFrameCount(std::ceil(buffer.frameLength*double(rate)/hardware.sampleRate)+32);
      AVAudioPCMBuffer *converted=[[AVAudioPCMBuffer alloc] initWithPCMFormat:target frameCapacity:capacity];
      __block bool supplied=false;NSError *failure=nil;
      auto status=[converter convertToBuffer:converted error:&failure withInputFromBlock:^AVAudioBuffer *(AVAudioPacketCount,AVAudioConverterInputStatus *inputStatus){if(supplied){*inputStatus=AVAudioConverterInputStatus_NoDataNow;return nil;}supplied=true;*inputStatus=AVAudioConverterInputStatus_HaveData;return buffer;}];
      std::lock_guard<std::mutex> lock(current->mutex);if(current->closed)return;
      if(status==AVAudioConverterOutputStatus_Error||failure){current->failed=true;current->changed.notify_all();return;}
      size_t count=size_t(converted.frameLength)*channels*2;auto *bytes=static_cast<const uint8_t *>(converted.audioBufferList->mBuffers[0].mData);
      size_t maximum=size_t(rate)*channels*2*2;while(current->pcm.size()+count>maximum&&!current->pcm.empty())current->pcm.pop_front();
      if(bytes&&count<=maximum)current->pcm.insert(current->pcm.end(),bytes,bytes+count);current->changed.notify_all();
    }catch(...){std::lock_guard<std::mutex> lock(current->mutex);current->failed=true;current->changed.notify_all();}}
  };
  AVAudioFrameCount frames=AVAudioFrameCount(std::ceil(hardware.sampleRate/10));
  if(@available(macOS 27.0,*)){NSError *failure=nil;if(![input installTapOnBus:0 bufferSize:frames format:hardware error:&failure block:tap])throw std::runtime_error("Cannot install host microphone tap");}
  else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    [input installTapOnBus:0 bufferSize:frames format:hardware block:tap];
#pragma clang diagnostic pop
  }
  state->tapInstalled=true;
  NSError *failure=nil;if(![state->engine startAndReturnError:&failure])throw std::runtime_error("Cannot start host microphone");return add(state);
});}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeCapture_microphoneRead(JNIEnv *env,jclass,jlong handle,jbyteArray output,jint offset,jint length){return checked(env,[&]()->jint{
  if(!output||offset<0||length<0||offset>env->GetArrayLength(output)-length||length>4*1024*1024)throw std::invalid_argument("Microphone read range");
  if(!allowed(env,"android.permission.RECORD_AUDIO")||!hostPermission(env,AVMediaTypeAudio,false))return -1;
  auto state=get(handle,Media::Microphone);std::unique_lock<std::mutex> lock(state->mutex);if(length%(state->channels*2))throw std::invalid_argument("PCM read alignment");if(!length)return 0;
  state->changed.wait_for(lock,std::chrono::milliseconds(500),[&]{return state->closed||state->failed||!state->pcm.empty();});
  if(state->closed||state->failed)throw std::runtime_error("Microphone capture stopped");
  size_t count=std::min(size_t(length),state->pcm.size());count-=count%(state->channels*2);std::vector<jbyte> bytes(count);
  for(size_t i=0;i<count;i++){bytes[i]=jbyte(state->pcm.front());state->pcm.pop_front();}lock.unlock();if(count)env->SetByteArrayRegion(output,offset,jsize(count),bytes.data());return jint(count);
});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_cameraOpen(JNIEnv *env,jclass){return checked(env,[&]()->jlong{
  if(!allowed(env,"android.permission.CAMERA")||!hostPermission(env,AVMediaTypeVideo,true))return 0;
  AVCaptureDevice *device=[AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];if(!device)throw std::runtime_error("No host camera is available");
  NSError *failure=nil;AVCaptureDeviceInput *input=[AVCaptureDeviceInput deviceInputWithDevice:device error:&failure];if(!input)throw std::runtime_error("Cannot open host camera");
  auto state=std::make_shared<Media>(Media::Camera);state->session=[AVCaptureSession new];[state->session beginConfiguration];
  if(![state->session canSetSessionPreset:AVCaptureSessionPreset640x480])throw std::runtime_error("Host camera lacks bounded 640x480 capture");state->session.sessionPreset=AVCaptureSessionPreset640x480;
  AVCaptureVideoDataOutput *output=[AVCaptureVideoDataOutput new];output.videoSettings=@{(id)kCVPixelBufferPixelFormatTypeKey:@(kCVPixelFormatType_32BGRA)};output.alwaysDiscardsLateVideoFrames=YES;
  if(![state->session canAddInput:input]||![state->session canAddOutput:output])throw std::runtime_error("Cannot configure host camera session");
  [state->session addInput:input];[state->session addOutput:output];
  ShirazCameraSamples *delegate=[ShirazCameraSamples new];delegate->owner=state;state->delegate=delegate;state->queue=dispatch_queue_create("org.shiraz.camera.samples",DISPATCH_QUEUE_SERIAL);[output setSampleBufferDelegate:delegate queue:state->queue];
  [state->session commitConfiguration];[state->session startRunning];
  std::unique_lock<std::mutex> lock(state->mutex);state->changed.wait_for(lock,std::chrono::seconds(5),[&]{return state->frame||state->failed;});if(!state->frame||state->failed)throw std::runtime_error("Host camera produced no frame");lock.unlock();return add(state);
});}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeCapture_cameraSize(JNIEnv *env,jclass,jlong handle){return checked(env,[&]()->jintArray{auto state=get(handle,Media::Camera);std::lock_guard<std::mutex> lock(state->mutex);if(state->closed)throw std::runtime_error("Camera stopped");jint size[]={state->width,state->height};auto out=env->NewIntArray(2);if(out)env->SetIntArrayRegion(out,0,2,size);return out;});}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeCapture_cameraFrame(JNIEnv *env,jclass,jlong handle){return checked(env,[&]()->jintArray{
  if(!allowed(env,"android.permission.CAMERA")||!hostPermission(env,AVMediaTypeVideo,false))return nullptr;
  auto state=get(handle,Media::Camera);std::unique_lock<std::mutex> lock(state->mutex);state->changed.wait_for(lock,std::chrono::milliseconds(500),[&]{return state->closed||state->failed||state->frame!=state->readFrame;});
  if(state->closed||state->failed)throw std::runtime_error("Camera capture stopped");if(state->frame==state->readFrame)return nullptr;
  auto pixels=state->pixels;state->readFrame=state->frame;lock.unlock();auto out=env->NewIntArray(jsize(pixels.size()));if(out)env->SetIntArrayRegion(out,0,jsize(pixels.size()),pixels.data());return out;
});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeCapture_close(JNIEnv *env,jclass,jlong handle){checked(env,[&]()->int{std::shared_ptr<Media> state;{std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(handle);if(found==registry.end())return 0;state=found->second;registry.erase(found);}state->stop();return 0;});}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_aacOpen(JNIEnv *env,jclass,jint,jint,jint){error(env,"java/lang/UnsupportedOperationException","Darwin AAC recording bridge is not implemented");return 0;}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeCapture_aacEncode(JNIEnv *env,jclass,jlong,jbyteArray){error(env,"java/lang/UnsupportedOperationException","Darwin AAC recording bridge is not implemented");return nullptr;}
