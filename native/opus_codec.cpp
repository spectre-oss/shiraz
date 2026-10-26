// SPDX-License-Identifier: GPL-3.0-only
// Optional system libopus adapter. Codec implementation stays OS-managed.
#include <jni.h>
#include <opus/opus.h>
#include <dlfcn.h>
#include <vector>
#include <memory>
#include <stdexcept>
#include <cstring>
namespace {
struct Api {
 void* library=dlopen("libopus.so.0",RTLD_NOW|RTLD_LOCAL);
 decltype(&opus_decoder_create) create=library?reinterpret_cast<decltype(create)>(dlsym(library,"opus_decoder_create")):nullptr;
 decltype(&opus_decode) decode=library?reinterpret_cast<decltype(decode)>(dlsym(library,"opus_decode")):nullptr;
 decltype(&opus_decoder_destroy) destroy=library?reinterpret_cast<decltype(destroy)>(dlsym(library,"opus_decoder_destroy")):nullptr;
 bool ready() const{return create&&decode&&destroy;}
};
Api& api(){static Api a;return a;}
struct Decoder {OpusDecoder* handle=nullptr;int channels=0,skip=0;~Decoder(){if(handle)api().destroy(handle);}};
template<class F> auto checked(JNIEnv* env,F f)->decltype(f()){try{return f();}catch(const std::exception& e){jclass c=env->FindClass("java/lang/IllegalStateException");if(c){env->ThrowNew(c,e.what());env->DeleteLocalRef(c);}return {};}}
}
extern "C" bool shiraz_opus_available(){return api().ready();}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeVideo_opusOpen(JNIEnv* env,jclass,jbyteArray config){return checked(env,[&]()->jlong{
 if(!api().ready()||!config||env->GetArrayLength(config)!=19)throw std::invalid_argument("OS Opus decoder or mapping-family-zero header unavailable");
 unsigned char bytes[19];env->GetByteArrayRegion(config,0,19,reinterpret_cast<jbyte*>(bytes));if(env->ExceptionCheck())return 0;
 if(memcmp(bytes,"OpusHead",8)||bytes[8]>15||bytes[9]<1||bytes[9]>2||bytes[18]!=0||bytes[16]||bytes[17])throw std::invalid_argument("Unsupported Opus header");
 auto d=std::make_unique<Decoder>();d->channels=bytes[9];d->skip=bytes[10]|int(bytes[11])<<8;int error=0;d->handle=api().create(48000,d->channels,&error);if(!d->handle||error)throw std::runtime_error("OS Opus initialization failed");return reinterpret_cast<jlong>(d.release());
});}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeVideo_opusDecode(JNIEnv* env,jclass,jlong handle,jbyteArray packet){return checked(env,[&]()->jbyteArray{
 auto* d=reinterpret_cast<Decoder*>(handle);if(!d||!packet)throw std::invalid_argument("Opus input");jsize size=env->GetArrayLength(packet);if(size<1||size>65536)throw std::invalid_argument("Opus packet bounds");
 std::vector<unsigned char> bytes(size);env->GetByteArrayRegion(packet,0,size,reinterpret_cast<jbyte*>(bytes.data()));if(env->ExceptionCheck())return nullptr;
 std::vector<opus_int16> pcm(5760*d->channels);int frames=api().decode(d->handle,bytes.data(),size,pcm.data(),5760,0);if(frames<0)throw std::runtime_error("OS Opus rejected packet");int skip=frames<d->skip?frames:d->skip;d->skip-=skip;int length=(frames-skip)*d->channels*2;jbyteArray result=env->NewByteArray(length);if(result&&length)env->SetByteArrayRegion(result,0,length,reinterpret_cast<jbyte*>(pcm.data()+skip*d->channels));return result;
});}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_opusClose(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<Decoder*>(handle);}
