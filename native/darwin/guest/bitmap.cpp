// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/bitmap.hpp"
#include "shiraz/android_bitmap.h"
#include "shiraz/native_window.h"
#include <dlfcn.h>
namespace shiraz::guest {
Provider android_provider(const std::filesystem::path &directory, UnwrapEnvironment unwrap) {
  if (!directory.is_absolute() || !unwrap) throw ElfError("Android provider requires absolute native directory and JNI unwrapping");
  struct Libraries {
    void *android = nullptr, *resources = nullptr;
    ~Libraries() { if(resources) dlclose(resources); if(android) dlclose(android); }
  };
  auto libraries = std::make_shared<Libraries>();
  libraries->android = dlopen((directory / "libandroid.so").c_str(), RTLD_NOW | RTLD_LOCAL);
  libraries->resources = dlopen((directory / "libshiraz-resources.so").c_str(), RTLD_NOW | RTLD_LOCAL);
  if (!libraries->android || !libraries->resources) throw ElfError("Android provider native libraries unavailable");
  Provider provider{"libandroid.so", {}, {}, false};
  auto add = [&](const char *name, auto type, bool resource = false) {
    using Function = decltype(type);
    auto function = reinterpret_cast<Function>(dlsym(resource ? libraries->resources : libraries->android,name));
    if (!function) throw ElfError(std::string("Android provider export unavailable: ")+name);
    auto entry = fixed_export(name,"",function);
    // Keep the explicitly opened native libraries resident with each export.
    auto retained = closure([libraries,address=entry.address](Capture &capture) {
      std::vector<Argument> arguments;
      for(unsigned i=0;i<6;++i) arguments.push_back(Argument::integer(capture.frame.integers[i],ArgumentType::Int64));
      auto result=invoke(reinterpret_cast<void *>(address),arguments);
      capture.frame.result_integers[0]=result.result_integers[0];
    });
    entry.address=reinterpret_cast<uintptr_t>(retained);
    provider.exports.push_back(std::move(entry));
  };
  add("ANativeWindow_acquire",static_cast<void (*)(ANativeWindow *)>(nullptr));
  add("ANativeWindow_release",static_cast<void (*)(ANativeWindow *)>(nullptr));
  add("ANativeWindow_getWidth",static_cast<int32_t (*)(ANativeWindow *)>(nullptr));
  add("ANativeWindow_getHeight",static_cast<int32_t (*)(ANativeWindow *)>(nullptr));
  add("ANativeWindow_getFormat",static_cast<int32_t (*)(ANativeWindow *)>(nullptr));
  add("ANativeWindow_setBuffersGeometry",static_cast<int32_t (*)(ANativeWindow *,int32_t,int32_t,int32_t)>(nullptr));
  add("ANativeWindow_lock",static_cast<int32_t (*)(ANativeWindow *,ANativeWindow_Buffer *,ARect *)>(nullptr));
  add("ANativeWindow_unlockAndPost",static_cast<int32_t (*)(ANativeWindow *)>(nullptr));
  add("AAssetManager_open",static_cast<void *(*)(void *,const char *,int)>(nullptr),true);
  add("AAsset_read",static_cast<int (*)(void *,void *,size_t)>(nullptr),true);
  add("AAsset_close",static_cast<void (*)(void *)>(nullptr),true);
  add("ATrace_beginSection",static_cast<void (*)(const char *)>(nullptr));
  add("ATrace_endSection",static_cast<void (*)()>(nullptr));
  for(bool asset:{false,true}) {
    const char *name=asset?"AAssetManager_fromJava":"ANativeWindow_fromSurface";
    auto function=reinterpret_cast<void *(*)(JNIEnv *,jobject)>(dlsym(asset?libraries->resources:libraries->android,name));
    if(!function)throw ElfError(std::string("Android JNI export unavailable: ")+name);
    auto entry=closure([libraries,unwrap,function](Capture &capture) {
      CallCursor cursor(capture,Convention::Aapcs64);
      auto *env=static_cast<JNIEnv *>(unwrap(cursor.read<void *>()));
      jobject object=cursor.read<jobject>();
      if(!env)throw ElfError("Android provider received null JNI environment");
      HostErrno preserve; return_value(capture,function(env,object));
    });
    provider.exports.push_back({name,"",reinterpret_cast<uintptr_t>(entry),SymbolKind::Function,"android/checked-shadow-jni",false});
  }
  return provider;
}
Provider bitmap_provider(UnwrapEnvironment unwrap) {
  if (!unwrap) throw ElfError("bitmap provider requires checked JNI environment unwrapping");
  static_assert(sizeof(AndroidBitmapInfo) == 20 && alignof(AndroidBitmapInfo) == 4);
  Provider provider{"libjnigraphics.so", {}, {}, false};
  for (unsigned operation = 0; operation < 3; ++operation) {
    void *entry = closure([unwrap, operation](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      void *guest = cursor.read<void *>();
      jobject bitmap = cursor.read<jobject>();
      HostErrno preserve;
      auto *host = static_cast<JNIEnv *>(unwrap(guest));
      if (!host) throw ElfError("bitmap provider received null host JNI environment");
      int result;
      if (operation == 0) result = AndroidBitmap_getInfo(host, bitmap, cursor.read<AndroidBitmapInfo *>());
      else if (operation == 1) result = AndroidBitmap_lockPixels(host, bitmap, cursor.read<void **>());
      else result = AndroidBitmap_unlockPixels(host, bitmap);
      return_value(capture, result);
    });
    provider.exports.push_back({operation == 0 ? "AndroidBitmap_getInfo" : operation == 1 ?
        "AndroidBitmap_lockPixels" : "AndroidBitmap_unlockPixels", "", reinterpret_cast<uintptr_t>(entry),
        SymbolKind::Function, "bitmap/checked-shadow-jni", false});
  }
  return provider;
}
}
