// SPDX-License-Identifier: GPL-3.0-only
#include <SLES/OpenSLES_Android.h>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>
#include <cmath>
#include <cstdio>
#include <stdexcept>
#include <string>
void check(SLresult result){if(result!=SL_RESULT_SUCCESS)throw std::runtime_error("OpenSL result "+std::to_string(result));}
std::atomic<unsigned> completed{0},markers{0};
void bufferDone(SLAndroidSimpleBufferQueueItf,void*){++completed;}
void playEvent(SLPlayItf,void*,SLuint32 event){if(event&SL_PLAYEVENT_HEADATMARKER)++markers;}
int main(){
 SLObjectItf engine=nullptr,mix=nullptr,player=nullptr;SLEngineItf api=nullptr;
 check(slCreateEngine(&engine,0,nullptr,0,nullptr,nullptr));check((*engine)->Realize(engine,SL_BOOLEAN_FALSE));check((*engine)->GetInterface(engine,SL_IID_ENGINE,&api));
 const SLboolean required=SL_BOOLEAN_TRUE;check((*api)->CreateOutputMix(api,&mix,1,&SL_IID_OUTPUTMIX,&required));check((*mix)->Realize(mix,SL_BOOLEAN_FALSE));
 SLDataLocator_AndroidSimpleBufferQueue locator{SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,4};
 SLDataFormat_PCM format{SL_DATAFORMAT_PCM,1,SL_SAMPLINGRATE_48,SL_PCMSAMPLEFORMAT_FIXED_16,SL_PCMSAMPLEFORMAT_FIXED_16,SL_SPEAKER_FRONT_CENTER,SL_BYTEORDER_LITTLEENDIAN};
 SLDataSource source{&locator,&format};SLDataLocator_OutputMix output{SL_DATALOCATOR_OUTPUTMIX,mix};SLDataSink sink{&output,nullptr};
 check((*api)->CreateAudioPlayer(api,&player,&source,&sink,1,&SL_IID_ANDROIDSIMPLEBUFFERQUEUE,&required));check((*player)->Realize(player,SL_BOOLEAN_FALSE));
 SLPlayItf play=nullptr;SLAndroidSimpleBufferQueueItf queue=nullptr;check((*player)->GetInterface(player,SL_IID_PLAY,&play));check((*player)->GetInterface(player,SL_IID_ANDROIDSIMPLEBUFFERQUEUE,&queue));
 check((*queue)->RegisterCallback(queue,bufferDone,nullptr));check((*play)->RegisterCallback(play,playEvent,nullptr));check((*play)->SetMarkerPosition(play,10));check((*play)->SetCallbackEventsMask(play,SL_PLAYEVENT_HEADATMARKER));
 std::vector<short> samples(960);for(unsigned i=0;i<samples.size();i++)samples[i]=short(12000*std::sin(i*6.283185307*440/48000));
 for(int i=0;i<4;i++)check((*queue)->Enqueue(queue,samples.data(),samples.size()*2));
 if((*queue)->Enqueue(queue,samples.data(),samples.size()*2)!=SL_RESULT_BUFFER_INSUFFICIENT)throw std::runtime_error("Unbounded queue");
 std::this_thread::sleep_for(std::chrono::milliseconds(20));if(completed)throw std::runtime_error("Stopped player consumed data");
 check((*play)->SetPlayState(play,SL_PLAYSTATE_PLAYING));
 for(int i=0;i<200&&completed<4;i++)std::this_thread::sleep_for(std::chrono::milliseconds(5));
 if(completed!=4||markers!=1)throw std::runtime_error("Buffer/marker callback missing");
 check((*play)->SetPlayState(play,SL_PLAYSTATE_PAUSED));SLmillisecond position=0;check((*play)->GetPosition(play,&position));if(position!=80)throw std::runtime_error("Playback position");
 check((*queue)->Clear(queue));SLAndroidSimpleBufferQueueState state{};check((*queue)->GetState(queue,&state));if(state.count||state.index)throw std::runtime_error("Clear state");
 (*player)->Destroy(player);(*mix)->Destroy(mix);(*engine)->Destroy(engine);std::puts("OPENSL_BUFFER_QUEUE_MARKER_PCM_OK");
}
