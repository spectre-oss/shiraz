// SPDX-License-Identifier: GPL-3.0-only
// Original typed OpenSL bridge; uses the shared implementation and its native
// AudioQueue output. Guest callbacks always re-enter through the AAPCS invoker.
#include "shiraz/darwin/guest/opensles.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <SLES/OpenSLES_Android.h>
#include <mutex>
#include <set>
#include <tuple>
#include <type_traits>
#include <condition_variable>
namespace {
// The loader's pre-unmap hook may run after this translation unit's static
// destructors. Keep bounded bookkeeping alive like the resident ABI closures;
// shutdown still drains callbacks and destroys every native audio object.
struct AudioRuntime {
  std::mutex objects_mutex;
  std::set<void *> objects;
  shiraz::guest::CodeValidator callback_validator;
  bool closing = false;
  size_t active_callbacks = 0;
  std::condition_variable callbacks_drained;
};
AudioRuntime &audio_runtime() {
  static AudioRuntime *runtime = new AudioRuntime;
  return *runtime;
}
void shiraz_opensl_track(void *object,bool add) {
  std::lock_guard<std::mutex> lock(audio_runtime().objects_mutex);
  if(add){if(audio_runtime().closing || audio_runtime().objects.size()>=256)throw shiraz::guest::ElfError("OpenSL object limit/closed runtime");audio_runtime().objects.insert(object);}
  else audio_runtime().objects.erase(object);
}
void shiraz_opensl_callback(void *callback,void *self,void *context,uint32_t events,bool play) {
  {std::lock_guard<std::mutex> lock(audio_runtime().objects_mutex);if(audio_runtime().closing)return;++audio_runtime().active_callbacks;}
  struct Guard { ~Guard(){std::lock_guard<std::mutex> lock(audio_runtime().objects_mutex);--audio_runtime().active_callbacks;audio_runtime().callbacks_drained.notify_all();} } guard;
  if(!audio_runtime().callback_validator || !audio_runtime().callback_validator(callback))std::abort();
  shiraz::guest::prepare_guest_tls();
  using namespace shiraz::guest;
  std::vector<Argument> arguments{Argument::integer(uintptr_t(self),ArgumentType::Int64),Argument::integer(uintptr_t(context),ArgumentType::Int64)};
  if(play)arguments.push_back(Argument::integer(events));
  invoke(callback,arguments);
}
template<class T> bool callback_valid(T value) {
  if constexpr(std::is_pointer_v<T> && std::is_function_v<std::remove_pointer_t<T>>)
    return !value || (audio_runtime().callback_validator && audio_runtime().callback_validator(reinterpret_cast<const void *>(value)));
  else return true;
}
template<class R,class... A> auto guest_method(R(*function)(A...))->R(*)(A...) {
  using namespace shiraz::guest;
  auto entry=closure([function](Capture &capture){
    CallCursor cursor(capture,Convention::Aapcs64);
    std::tuple<A...> args{cursor.read<A>()...};
    bool valid=std::apply([](auto... value){return (callback_valid(value)&&...);},args);
    HostErrno preserve;
    if constexpr(std::is_void_v<R>){if(valid)std::apply(function,args);}
    else return_value(capture,valid?std::apply(function,args):static_cast<R>(SL_RESULT_PARAMETER_INVALID));
  });
  return reinterpret_cast<R(*)(A...)>(entry);
}
SLObjectItf_ guest_table(SLObjectItf_ table){table.Realize=guest_method(table.Realize);table.Resume=guest_method(table.Resume);table.GetState=guest_method(table.GetState);table.GetInterface=guest_method(table.GetInterface);table.RegisterCallback=guest_method(table.RegisterCallback);table.AbortAsyncOperation=guest_method(table.AbortAsyncOperation);table.Destroy=guest_method(table.Destroy);table.SetPriority=guest_method(table.SetPriority);table.GetPriority=guest_method(table.GetPriority);table.SetLossOfControlInterfaces=guest_method(table.SetLossOfControlInterfaces);return table;}
SLEngineItf_ guest_table(SLEngineItf_ table){table.CreateLEDDevice=guest_method(table.CreateLEDDevice);table.CreateVibraDevice=guest_method(table.CreateVibraDevice);table.CreateAudioPlayer=guest_method(table.CreateAudioPlayer);table.CreateAudioRecorder=guest_method(table.CreateAudioRecorder);table.CreateMidiPlayer=guest_method(table.CreateMidiPlayer);table.CreateListener=guest_method(table.CreateListener);table.Create3DGroup=guest_method(table.Create3DGroup);table.CreateOutputMix=guest_method(table.CreateOutputMix);table.CreateMetadataExtractor=guest_method(table.CreateMetadataExtractor);table.CreateExtensionObject=guest_method(table.CreateExtensionObject);table.QueryNumSupportedInterfaces=guest_method(table.QueryNumSupportedInterfaces);table.QuerySupportedInterfaces=guest_method(table.QuerySupportedInterfaces);table.QueryNumSupportedExtensions=guest_method(table.QueryNumSupportedExtensions);table.QuerySupportedExtension=guest_method(table.QuerySupportedExtension);table.IsExtensionSupported=guest_method(table.IsExtensionSupported);return table;}
SLPlayItf_ guest_table(SLPlayItf_ table){table.SetPlayState=guest_method(table.SetPlayState);table.GetPlayState=guest_method(table.GetPlayState);table.GetDuration=guest_method(table.GetDuration);table.GetPosition=guest_method(table.GetPosition);table.RegisterCallback=guest_method(table.RegisterCallback);table.SetCallbackEventsMask=guest_method(table.SetCallbackEventsMask);table.GetCallbackEventsMask=guest_method(table.GetCallbackEventsMask);table.SetMarkerPosition=guest_method(table.SetMarkerPosition);table.ClearMarkerPosition=guest_method(table.ClearMarkerPosition);table.GetMarkerPosition=guest_method(table.GetMarkerPosition);table.SetPositionUpdatePeriod=guest_method(table.SetPositionUpdatePeriod);table.GetPositionUpdatePeriod=guest_method(table.GetPositionUpdatePeriod);return table;}
SLVolumeItf_ guest_table(SLVolumeItf_ table){table.SetVolumeLevel=guest_method(table.SetVolumeLevel);table.GetVolumeLevel=guest_method(table.GetVolumeLevel);table.GetMaxVolumeLevel=guest_method(table.GetMaxVolumeLevel);table.SetMute=guest_method(table.SetMute);table.GetMute=guest_method(table.GetMute);table.EnableStereoPosition=guest_method(table.EnableStereoPosition);table.IsEnabledStereoPosition=guest_method(table.IsEnabledStereoPosition);table.SetStereoPosition=guest_method(table.SetStereoPosition);table.GetStereoPosition=guest_method(table.GetStereoPosition);return table;}
SLAndroidSimpleBufferQueueItf_ guest_table(SLAndroidSimpleBufferQueueItf_ table){table.Enqueue=guest_method(table.Enqueue);table.Clear=guest_method(table.Clear);table.GetState=guest_method(table.GetState);table.RegisterCallback=guest_method(table.RegisterCallback);return table;}
SLAndroidConfigurationItf_ guest_table(SLAndroidConfigurationItf_ table){table.SetConfiguration=guest_method(table.SetConfiguration);table.GetConfiguration=guest_method(table.GetConfiguration);table.AcquireJavaProxy=guest_method(table.AcquireJavaProxy);table.ReleaseJavaProxy=guest_method(table.ReleaseJavaProxy);return table;}
}
#define SHIRAZ_OPENSL_GUEST 1
#include "../../opensles.cpp"
namespace shiraz::guest {
Provider opensles_provider(CodeValidator validate,std::function<void()> &shutdown) {
  {std::lock_guard<std::mutex> lock(audio_runtime().objects_mutex);if(!audio_runtime().objects.empty())throw ElfError("OpenSL runtime already active");audio_runtime().callback_validator=std::move(validate);audio_runtime().closing=false;}
  shutdown=[] {
    std::vector<void *> active;
    {std::unique_lock<std::mutex> lock(audio_runtime().objects_mutex);audio_runtime().closing=true;audio_runtime().callbacks_drained.wait(lock,[]{return !audio_runtime().active_callbacks;});active.assign(audio_runtime().objects.begin(),audio_runtime().objects.end());}
    for(void *pointer:active){auto *object=static_cast<Object *>(pointer);destroy(reinterpret_cast<SLObjectItf>(&object->object));}
  };
  Provider provider{"libOpenSLES.so",{},{},false};
  provider.exports.push_back(fixed_export("slCreateEngine","",&slCreateEngine));
  provider.exports.push_back({"SL_IID_ENGINE","",uintptr_t(&SL_IID_ENGINE),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_PLAY","",uintptr_t(&SL_IID_PLAY),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_VOLUME","",uintptr_t(&SL_IID_VOLUME),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_BUFFERQUEUE","",uintptr_t(&SL_IID_BUFFERQUEUE),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_ANDROIDSIMPLEBUFFERQUEUE","",uintptr_t(&SL_IID_ANDROIDSIMPLEBUFFERQUEUE),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_ANDROIDCONFIGURATION","",uintptr_t(&SL_IID_ANDROIDCONFIGURATION),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_OUTPUTMIX","",uintptr_t(&SL_IID_OUTPUTMIX),SymbolKind::Object,"opensl/standard-interface-id",false});
  provider.exports.push_back({"SL_IID_RECORD","",uintptr_t(&SL_IID_RECORD),SymbolKind::Object,"opensl/standard-interface-id",false});
  return provider;
}
}
