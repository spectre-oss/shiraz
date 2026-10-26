// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <espeak-ng/speak_lib.h>
#include <mutex>
#include <vector>
#include <string>
#include <stdexcept>
#include <algorithm>
#include <filesystem>
#include <cstring>
namespace {
std::mutex mutex;
int sampleRate=0;
std::string directory;
std::vector<short>* output=nullptr;
bool overflow=false;
void error(JNIEnv* env,const char* type,const char* message){if(env->ExceptionCheck())return;auto cls=env->FindClass(type);if(cls){env->ThrowNew(cls,message);env->DeleteLocalRef(cls);}}
std::string string(JNIEnv* env,jstring value){if(!value)throw std::invalid_argument("Null speech argument");const char* p=env->GetStringUTFChars(value,nullptr);if(!p)throw std::bad_alloc();std::string result;try{result=p;}catch(...){env->ReleaseStringUTFChars(value,p);throw;}env->ReleaseStringUTFChars(value,p);return result;}
int receive(short* samples,int count,espeak_EVENT*){
 if(!samples||count==0)return 0;
 if(!output||count<0||output->size()+static_cast<size_t>(count)>static_cast<size_t>(sampleRate)*120){overflow=true;return 1;}
 try{output->insert(output->end(),samples,samples+count);}catch(...){overflow=true;return 1;}return 0;
}
bool safeVoice(const std::string& id){return !id.empty()&&id.size()<=64&&id[0]!='/'&&id.find("//")==std::string::npos&&id.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_+-/")==std::string::npos;}
std::vector<std::string> catalog(){
 std::vector<std::string> result;const auto voices=espeak_ListVoices(nullptr);if(!voices)return result;
 for(size_t i=0;voices[i];++i){
  if(i>=1024)throw std::runtime_error("Speech voice catalog exceeds limit");
  const auto v=voices[i];if(!v->identifier||!v->languages||!v->languages[0])continue;
  std::string id=v->identifier,tag=v->languages+1;
  if(!safeVoice(id)||id.compare(0,3,"mb/")==0||tag.empty()||tag.size()>64||tag.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-")!=std::string::npos)continue;
  result.push_back(id);result.push_back(tag);
 }return result;
}
struct Collect {explicit Collect(std::vector<short>& samples){output=&samples;overflow=false;}~Collect(){output=nullptr;}};
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSpeech_initialize(JNIEnv* env,jclass,jstring path){
 try{
  std::lock_guard<std::mutex> lock(mutex);std::string requested=string(env,path);
  if(requested.empty()||requested.size()>4096||requested[0]!='/')throw std::invalid_argument("Speech data requires an absolute directory");
  if(sampleRate){if(directory!=requested)throw std::invalid_argument("Speech engine already initialized with another directory");return sampleRate;}
  for(const char* file:{"phondata","phonindex","phontab","intonations"})if(!std::filesystem::is_regular_file(std::filesystem::path(requested)/"espeak-ng-data"/file)){error(env,"java/lang/IllegalStateException","Speech data files are unavailable");return 0;}
  int rate=espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,20,requested.c_str(),espeakINITIALIZE_DONT_EXIT);
  if(rate<8000||rate>96000){error(env,"java/lang/IllegalStateException","Cannot initialize speech voice data");return 0;}
  sampleRate=rate;directory=requested;espeak_SetSynthCallback(receive);return sampleRate;
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Speech initialization allocation");}
 catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}return 0;
}
extern "C" JNIEXPORT jshortArray JNICALL Java_shiraz_runtime_NativeSpeech_synthesizeUtf8(JNIEnv* env,jclass,jbyteArray text,jstring voice,jint rate,jint pitch){
 try{
  std::lock_guard<std::mutex> lock(mutex);
  if(!sampleRate){error(env,"java/lang/IllegalStateException","Speech engine is not initialized");return nullptr;}
  if(!text||env->GetArrayLength(text)>16384||rate<80||rate>450||pitch<0||pitch>99)throw std::invalid_argument("Speech synthesis bounds");
  std::string selected=string(env,voice);if(!safeVoice(selected))throw std::invalid_argument("Speech voice identifier");
  if(selected.find('/')!=std::string::npos){auto voices=catalog();bool found=false;for(size_t i=0;i<voices.size();i+=2)if(voices[i]==selected)found=true;if(!found)throw std::invalid_argument("Speech voice is not installed");}
  auto size=env->GetArrayLength(text);std::vector<char> utf8(static_cast<size_t>(size)+1,0);env->GetByteArrayRegion(text,0,size,reinterpret_cast<jbyte*>(utf8.data()));if(env->ExceptionCheck())return nullptr;
  if(std::find(utf8.begin(),utf8.end()-1,'\0')!=utf8.end()-1)throw std::invalid_argument("Speech text contains NUL");
  if(espeak_SetVoiceByName(selected.c_str())!=EE_OK)throw std::invalid_argument("Speech voice is unavailable");
  if(espeak_SetParameter(espeakRATE,rate,0)!=EE_OK||espeak_SetParameter(espeakPITCH,pitch,0)!=EE_OK||espeak_SetParameter(espeakVOLUME,100,0)!=EE_OK)throw std::runtime_error("Speech parameter failure");
  std::vector<short> samples;Collect collect(samples);unsigned id=0;
  auto status=espeak_Synth(utf8.data(),utf8.size(),0,POS_CHARACTER,0,espeakCHARS_UTF8|espeakENDPAUSE,&id,nullptr);
  if(status!=EE_OK||overflow){error(env,"java/lang/IllegalStateException","Speech synthesis failed or exceeded 120 seconds");return nullptr;}
  auto result=env->NewShortArray(static_cast<jsize>(samples.size()));if(result&&!samples.empty())env->SetShortArrayRegion(result,0,static_cast<jsize>(samples.size()),samples.data());return result;
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Speech synthesis allocation");}
 catch(const std::exception& e){error(env,"java/lang/IllegalArgumentException",e.what());}return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_shiraz_runtime_NativeSpeech_installedVoices(JNIEnv* env,jclass){
 try{
  std::lock_guard<std::mutex> lock(mutex);
  if(!sampleRate){error(env,"java/lang/IllegalStateException","Speech engine is not initialized");return nullptr;}
  auto voices=catalog();auto cls=env->FindClass("java/lang/String");if(!cls)return nullptr;
  auto result=env->NewObjectArray(static_cast<jsize>(voices.size()),cls,nullptr);env->DeleteLocalRef(cls);if(!result)return nullptr;
  for(size_t i=0;i<voices.size();++i){auto value=env->NewStringUTF(voices[i].c_str());if(!value)return nullptr;env->SetObjectArrayElement(result,static_cast<jsize>(i),value);env->DeleteLocalRef(value);if(env->ExceptionCheck())return nullptr;}
  return result;
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Speech catalog allocation");}
 catch(const std::exception& e){error(env,"java/lang/IllegalStateException",e.what());}return nullptr;
}
