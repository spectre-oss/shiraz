// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/opensles.hpp"
#include <SLES/OpenSLES_Android.h>
#include <iostream>
#include <stdexcept>
using namespace shiraz::guest;
static void check(bool value,const char *message){if(!value)throw std::runtime_error(message);}
static Argument arg(uint64_t value){return Argument::integer(value,ArgumentType::Int64);}
template<class T> static Argument ptr(T value){return arg(reinterpret_cast<uintptr_t>(value));}
static uint64_t call(void *fn,std::vector<Argument> arguments){return invoke(fn,arguments).result_integers[0];}
int main(){try{
 std::function<void()> shutdown;
 auto provider=opensles_provider([](const void *){return false;},shutdown);
 void *create=nullptr;
 for(const auto &entry:provider.exports)if(entry.name=="slCreateEngine")create=reinterpret_cast<void *>(entry.address);
 SLObjectItf engine=nullptr;
 check(call(create,{ptr(&engine),arg(0),arg(0),arg(0),arg(0),arg(0)})==SL_RESULT_SUCCESS&&engine,"create real OpenSL engine");
 check(call(reinterpret_cast<void *>((*engine)->Realize),{ptr(engine),arg(0)})==SL_RESULT_SUCCESS,"typed engine realize");
 SLEngineItf api=nullptr;
 check(call(reinterpret_cast<void *>((*engine)->GetInterface),{ptr(engine),ptr(SL_IID_ENGINE),ptr(&api)})==SL_RESULT_SUCCESS&&api,"typed engine interface");
 SLObjectItf mix=nullptr;
 check(call(reinterpret_cast<void *>((*api)->CreateOutputMix),{ptr(api),ptr(&mix),arg(0),arg(0),arg(0)})==SL_RESULT_SUCCESS&&mix,"typed output mix");
 SLDataLocator_AndroidSimpleBufferQueue queue{SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,2};
 SLDataFormat_PCM pcm{SL_DATAFORMAT_PCM,2,SL_SAMPLINGRATE_48,SL_PCMSAMPLEFORMAT_FIXED_16,SL_PCMSAMPLEFORMAT_FIXED_16,SL_SPEAKER_FRONT_LEFT|SL_SPEAKER_FRONT_RIGHT,SL_BYTEORDER_LITTLEENDIAN};
 SLDataSource source{&queue,&pcm};SLDataLocator_OutputMix destination{SL_DATALOCATOR_OUTPUTMIX,mix};SLDataSink sink{&destination,nullptr};
 SLObjectItf player=nullptr;
 check(call(reinterpret_cast<void *>((*api)->CreateAudioPlayer),{ptr(api),ptr(&player),ptr(&source),ptr(&sink),arg(0),arg(0),arg(0)})==SL_RESULT_SUCCESS&&player,"typed PCM player creation");
 SLAndroidSimpleBufferQueueItf buffers=nullptr;
 check(call(reinterpret_cast<void *>((*player)->GetInterface),{ptr(player),ptr(SL_IID_ANDROIDSIMPLEBUFFERQUEUE),ptr(&buffers)})==SL_RESULT_SUCCESS&&buffers,"typed buffer interface");
 int16_t silence[96]{};
 check(call(reinterpret_cast<void *>((*buffers)->Enqueue),{ptr(buffers),ptr(silence),arg(sizeof(silence))})==SL_RESULT_SUCCESS,"owned PCM enqueue");
 SLAndroidSimpleBufferQueueState state{};
 check(call(reinterpret_cast<void *>((*buffers)->GetState),{ptr(buffers),ptr(&state)})==SL_RESULT_SUCCESS&&state.count==1,"actual queue count");
 check(call(reinterpret_cast<void *>((*buffers)->RegisterCallback),{ptr(buffers),arg(0x1234),arg(0)})==SL_RESULT_PARAMETER_INVALID,"unproven guest callback rejected");
 shutdown();
 std::cout<<"M5_OPENSL_PASS typed_vtables=1 engine=1 mix=1 pcm_queue=1 callback_validation=1 teardown=1 audio_device_execution=unverified\n";
}catch(const std::exception &error){std::cerr<<"FAIL "<<error.what()<<'\n';return 1;}}
