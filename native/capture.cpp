// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <alsa/asoundlib.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <csetjmp>
#include <jpeglib.h>
extern "C" {
#include <vo-aacenc/voAAC.h>
#include <vo-aacenc/cmnMemory.h>
}
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <stdexcept>
#include <chrono>
#include <thread>
namespace {
struct Buffer {void* data;size_t size;};
struct Capture {
 std::mutex mutex;snd_pcm_t* pcm=nullptr;int rate=44100,channels=1,fd=-1,width=0,height=0,stride=0;unsigned format=0;bool streaming=false,closed=false;
 std::chrono::steady_clock::time_point nextRead=std::chrono::steady_clock::now();
 std::vector<Buffer> buffers;VO_AUDIO_CODECAPI codec{};VO_HANDLE encoder=nullptr;VO_MEM_OPERATOR memory{};
 void dispose(){if(closed)return;closed=true;if(pcm){snd_pcm_drop(pcm);snd_pcm_close(pcm);pcm=nullptr;}if(fd>=0){if(streaming){int type=V4L2_BUF_TYPE_VIDEO_CAPTURE;ioctl(fd,VIDIOC_STREAMOFF,&type);}for(auto& b:buffers)munmap(b.data,b.size);buffers.clear();::close(fd);fd=-1;}if(encoder){codec.Uninit(encoder);encoder=nullptr;}}
 ~Capture(){dispose();}
};
std::mutex registryMutex;std::unordered_map<jlong,std::shared_ptr<Capture>> registry;jlong nextId=1;
void error(JNIEnv* env,const char* type,const char* message){if(env->ExceptionCheck())return;jclass c=env->FindClass(type);if(c){env->ThrowNew(c,message);env->DeleteLocalRef(c);}}
bool allowed(JNIEnv* env,const char* permission){jclass c=env->FindClass("shiraz/runtime/CapturePermissions");if(!c)return false;jmethodID m=env->GetStaticMethodID(c,"authorize","(Ljava/lang/String;)V");if(!m){env->DeleteLocalRef(c);return false;}jstring p=env->NewStringUTF(permission);env->CallStaticVoidMethod(c,m,p);env->DeleteLocalRef(p);env->DeleteLocalRef(c);return !env->ExceptionCheck();}
jlong add(const std::shared_ptr<Capture>& c){std::lock_guard<std::mutex> lock(registryMutex);if(registry.size()>=16)throw std::runtime_error("Capture handle limit");jlong id=nextId++;registry.emplace(id,c);return id;}
std::shared_ptr<Capture> get(jlong id){std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(id);if(found==registry.end())throw std::runtime_error("Capture is closed");return found->second;}
template<class F> auto checked(JNIEnv* env,F f)->decltype(f()){try{return f();}catch(const std::exception& e){error(env,"java/lang/IllegalStateException",e.what());return {};}}
int call(int fd,unsigned long request,void* value){int r;do{r=ioctl(fd,request,value);}while(r<0&&errno==EINTR);return r;}
struct JpegError {jpeg_error_mgr base;jmp_buf jump;};
void jpegError(j_common_ptr c){longjmp(reinterpret_cast<JpegError*>(c->err)->jump,1);}
std::vector<jint> decodeJpeg(const unsigned char* bytes,size_t length){
 jpeg_decompress_struct decoder{};JpegError failure{};decoder.err=jpeg_std_error(&failure.base);failure.base.error_exit=jpegError;
 unsigned char* raw=nullptr;if(setjmp(failure.jump)){std::free(raw);jpeg_destroy_decompress(&decoder);throw std::runtime_error("Invalid camera JPEG frame");}
 jpeg_create_decompress(&decoder);jpeg_mem_src(&decoder,bytes,length);jpeg_read_header(&decoder,TRUE);
 if(!decoder.image_width||!decoder.image_height||decoder.image_width>1920||decoder.image_height>1080){jpeg_destroy_decompress(&decoder);throw std::runtime_error("Camera frame dimensions exceed limit");}
 decoder.out_color_space=JCS_RGB;jpeg_start_decompress(&decoder);int w=decoder.output_width,h=decoder.output_height;size_t stride=size_t(w)*3;
 raw=static_cast<unsigned char*>(std::malloc(stride*h));if(!raw){jpeg_destroy_decompress(&decoder);throw std::bad_alloc();}
 while(decoder.output_scanline<decoder.output_height){JSAMPROW row=raw+decoder.output_scanline*stride;jpeg_read_scanlines(&decoder,&row,1);}
 jpeg_finish_decompress(&decoder);jpeg_destroy_decompress(&decoder);std::vector<jint> out(size_t(w)*h+2);out[0]=w;out[1]=h;
 for(int i=0;i<w*h;i++){out[size_t(i)+2]=jint(0xff000000u|unsigned(raw[3*i])<<16|unsigned(raw[3*i+1])<<8|raw[3*i+2]);}
 std::free(raw);return out;
}
int clamp(int n){return n<0?0:n>255?255:n;}
jint yuv(int y,int u,int v){int c=y-16,d=u-128,e=v-128;return jint(0xff000000u|unsigned(clamp((298*c+409*e+128)>>8))<<16|unsigned(clamp((298*c-100*d-208*e+128)>>8))<<8|unsigned(clamp((298*c+516*d+128)>>8)));}
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_microphoneOpen(JNIEnv* env,jclass,jint rate,jint channels){
 if(!allowed(env,"android.permission.RECORD_AUDIO"))return 0;
 return checked(env,[&]()->jlong{if(rate<8000||rate>96000||channels<1||channels>2)throw std::invalid_argument("PCM configuration");auto c=std::make_shared<Capture>();c->channels=channels;c->rate=rate;const char* device=std::getenv("SHIRAZ_CAPTURE_AUDIO_DEVICE");if(!device||!*device)device="default";int r=snd_pcm_open(&c->pcm,device,SND_PCM_STREAM_CAPTURE,SND_PCM_NONBLOCK);if(r>=0)r=snd_pcm_set_params(c->pcm,SND_PCM_FORMAT_S16_LE,SND_PCM_ACCESS_RW_INTERLEAVED,channels,rate,1,100000);if(r<0)throw std::runtime_error("Microphone is unavailable");return add(c);});
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeCapture_microphoneRead(JNIEnv* env,jclass,jlong id,jbyteArray array,jint offset,jint length){
 if(!allowed(env,"android.permission.RECORD_AUDIO"))return -3;
 return checked(env,[&]()->jint{if(!array||offset<0||length<0||length>4*1024*1024||jlong(offset)+length>env->GetArrayLength(array))throw std::invalid_argument("PCM destination");auto c=get(id);std::lock_guard<std::mutex> lock(c->mutex);if(c->closed||!c->pcm)return -3;std::vector<jbyte> bytes(static_cast<size_t>(length),0);snd_pcm_sframes_t n=0;for(int attempt=0;attempt<10;attempt++){n=snd_pcm_readi(c->pcm,bytes.data(),std::min(length/(c->channels*2),c->rate/5));if(n==-EAGAIN){snd_pcm_wait(c->pcm,20);continue;}if(n<0){if(snd_pcm_recover(c->pcm,int(n),1)<0)return -6;continue;}break;}if(n<0)return 0;int count=int(n)*c->channels*2;c->nextRead+=std::chrono::microseconds(n*1000000/c->rate);auto now=std::chrono::steady_clock::now();if(c->nextRead>now)std::this_thread::sleep_until(c->nextRead);else c->nextRead=now;env->SetByteArrayRegion(array,offset,count,bytes.data());return count;});
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_cameraOpen(JNIEnv* env,jclass){
 if(!allowed(env,"android.permission.CAMERA"))return 0;
 return checked(env,[&]()->jlong{
  for(int index=0;index<64;index++){
   auto c=std::make_shared<Capture>();char path[32];std::snprintf(path,sizeof(path),"/dev/video%d",index);c->fd=open(path,O_RDWR|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);if(c->fd<0)continue;
   v4l2_capability cap{};if(call(c->fd,VIDIOC_QUERYCAP,&cap)<0)continue;unsigned caps=cap.capabilities&V4L2_CAP_DEVICE_CAPS?cap.device_caps:cap.capabilities;if(!(caps&V4L2_CAP_VIDEO_CAPTURE)||!(caps&V4L2_CAP_STREAMING))continue;
   v4l2_format f{};f.type=V4L2_BUF_TYPE_VIDEO_CAPTURE;f.fmt.pix.width=640;f.fmt.pix.height=480;f.fmt.pix.pixelformat=V4L2_PIX_FMT_MJPEG;f.fmt.pix.field=V4L2_FIELD_ANY;
   if(call(c->fd,VIDIOC_S_FMT,&f)<0||(f.fmt.pix.pixelformat!=V4L2_PIX_FMT_MJPEG&&f.fmt.pix.pixelformat!=V4L2_PIX_FMT_YUYV)){f.fmt.pix.pixelformat=V4L2_PIX_FMT_YUYV;if(call(c->fd,VIDIOC_S_FMT,&f)<0||f.fmt.pix.pixelformat!=V4L2_PIX_FMT_YUYV)continue;}
   c->width=f.fmt.pix.width;c->height=f.fmt.pix.height;c->format=f.fmt.pix.pixelformat;c->stride=int(f.fmt.pix.bytesperline);if(c->width<1||c->height<1||c->width>1920||c->height>1080||c->width%2)continue;
   v4l2_requestbuffers request{};request.count=4;request.type=V4L2_BUF_TYPE_VIDEO_CAPTURE;request.memory=V4L2_MEMORY_MMAP;if(call(c->fd,VIDIOC_REQBUFS,&request)<0||request.count<1||request.count>16)continue;
   bool ok=true;for(unsigned n=0;n<request.count;n++){v4l2_buffer b{};b.type=request.type;b.memory=request.memory;b.index=n;if(call(c->fd,VIDIOC_QUERYBUF,&b)<0||b.length>16*1024*1024){ok=false;break;}void* memory=mmap(nullptr,b.length,PROT_READ|PROT_WRITE,MAP_SHARED,c->fd,b.m.offset);if(memory==MAP_FAILED){ok=false;break;}c->buffers.push_back({memory,b.length});if(call(c->fd,VIDIOC_QBUF,&b)<0){ok=false;break;}}
   int type=V4L2_BUF_TYPE_VIDEO_CAPTURE;if(!ok||call(c->fd,VIDIOC_STREAMON,&type)<0)continue;c->streaming=true;return add(c);
  }throw std::runtime_error("No usable camera is connected");
 });
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeCapture_cameraFrame(JNIEnv* env,jclass,jlong id){
 if(!allowed(env,"android.permission.CAMERA"))return nullptr;
 return checked(env,[&]()->jintArray{auto c=get(id);std::lock_guard<std::mutex> lock(c->mutex);if(c->closed||c->fd<0)return nullptr;pollfd waiting{c->fd,POLLIN,0};if(poll(&waiting,1,200)<=0)return nullptr;v4l2_buffer b{};b.type=V4L2_BUF_TYPE_VIDEO_CAPTURE;b.memory=V4L2_MEMORY_MMAP;if(call(c->fd,VIDIOC_DQBUF,&b)<0)return nullptr;if(b.index>=c->buffers.size()||b.bytesused>c->buffers[b.index].size)throw std::runtime_error("Invalid camera buffer");
  std::vector<jint> pixels;try{const auto* bytes=static_cast<const unsigned char*>(c->buffers[b.index].data);if(c->format==V4L2_PIX_FMT_MJPEG)pixels=decodeJpeg(bytes,b.bytesused);else{if((c->stride<c->width*2||size_t(b.bytesused)<size_t(c->stride)*c->height))throw std::runtime_error("Truncated camera frame");pixels.resize(size_t(c->width)*c->height+2);pixels[0]=c->width;pixels[1]=c->height;for(int i=0;i<c->width*c->height;i+=2){int j=(i/c->width)*c->stride+(i%c->width)*2;pixels[size_t(i)+2]=yuv(bytes[j],bytes[j+1],bytes[j+3]);pixels[size_t(i)+3]=yuv(bytes[j+2],bytes[j+1],bytes[j+3]);}}}catch(...){call(c->fd,VIDIOC_QBUF,&b);throw;}call(c->fd,VIDIOC_QBUF,&b);jintArray result=env->NewIntArray(jsize(pixels.size()));if(result)env->SetIntArrayRegion(result,0,jsize(pixels.size()),pixels.data());return result;});
}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeCapture_aacOpen(JNIEnv* env,jclass,jint rate,jint channels,jint bitrate){return checked(env,[&]()->jlong{
 if(rate<8000||rate>96000||channels<1||channels>2||bitrate<8000||bitrate>320000)throw std::invalid_argument("AAC configuration");
 auto c=std::make_shared<Capture>();c->channels=channels;voGetAACEncAPI(&c->codec);c->memory.Alloc=cmnMemAlloc;c->memory.Copy=cmnMemCopy;c->memory.Free=cmnMemFree;c->memory.Set=cmnMemSet;c->memory.Check=cmnMemCheck;VO_CODEC_INIT_USERDATA user{};user.memflag=VO_IMF_USERMEMOPERATOR;user.memData=&c->memory;if(c->codec.Init(&c->encoder,VO_AUDIO_CodingAAC,&user)!=VO_ERR_NONE)throw std::runtime_error("AAC encoder initialization failed");AACENC_PARAM parameters{};parameters.sampleRate=rate;parameters.nChannels=channels;parameters.bitRate=bitrate;parameters.adtsUsed=0;if(c->codec.SetParam(c->encoder,VO_PID_AAC_ENCPARAM,&parameters)!=VO_ERR_NONE)throw std::runtime_error("Unsupported AAC parameters");return add(c);});}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeCapture_aacEncode(JNIEnv* env,jclass,jlong id,jbyteArray input){return checked(env,[&]()->jbyteArray{
 auto c=get(id);std::lock_guard<std::mutex> lock(c->mutex);if(c->closed||!c->encoder||!input||env->GetArrayLength(input)!=1024*c->channels*2)throw std::invalid_argument("AAC requires one PCM16 frame");std::vector<jbyte> pcm(size_t(1024*c->channels*2));env->GetByteArrayRegion(input,0,jsize(pcm.size()),pcm.data());VO_CODECBUFFER in{},out{};VO_AUDIO_OUTPUTINFO info{};in.Buffer=reinterpret_cast<VO_PBYTE>(pcm.data());in.Length=VO_U32(pcm.size());unsigned char encoded[8192];out.Buffer=encoded;out.Length=sizeof(encoded);if(c->codec.SetInputData(c->encoder,&in)!=VO_ERR_NONE||c->codec.GetOutputData(c->encoder,&out,&info)!=VO_ERR_NONE||out.Length>sizeof(encoded))throw std::runtime_error("AAC encoding failed");jbyteArray result=env->NewByteArray(jsize(out.Length));if(result)env->SetByteArrayRegion(result,0,jsize(out.Length),reinterpret_cast<jbyte*>(encoded));return result;});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeCapture_close(JNIEnv* env,jclass,jlong id){checked(env,[&]()->int{std::shared_ptr<Capture> c;{std::lock_guard<std::mutex> lock(registryMutex);auto found=registry.find(id);if(found==registry.end())return 0;c=found->second;registry.erase(found);}std::lock_guard<std::mutex> lock(c->mutex);c->dispose();return 0;});}

extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeCapture_cameraSize(JNIEnv* env,jclass,jlong id){
 if(!allowed(env,"android.permission.CAMERA"))return nullptr;
 return checked(env,[&]()->jintArray{auto c=get(id);std::lock_guard<std::mutex> lock(c->mutex);if(c->closed||c->fd<0)throw std::runtime_error("Camera closed");jint size[2]={c->width,c->height};jintArray out=env->NewIntArray(2);if(out)env->SetIntArrayRegion(out,0,2,size);return out;});
}
