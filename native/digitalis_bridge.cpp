// SPDX-License-Identifier: GPL-3.0-only
#include <cstdlib>
// NativeBridge v3 adapter for Shiraz's standalone ART and Digitalis.
#include <cstdio>
#include <cstring>
#include <elf.h>
#include <mutex>
#include <string>
#include <vector>
#include <nativebridge/native_bridge.h>
#include "berberis/native_bridge/native_bridge.h"
#include "berberis/guest_loader/guest_loader.h"
#include "berberis/jni/jni_trampolines.h"
#include "berberis/runtime/berberis.h"
#include "berberis/runtime_primitives/known_guest_function_wrapper.h"

namespace {
const android::NativeBridgeRuntimeCallbacks* callbacks = nullptr;
berberis::GuestLoader* loader = nullptr;
std::once_flag initialization;
std::string initialization_error;
bool initialize(const android::NativeBridgeRuntimeCallbacks* runtime, const char*, const char*) {
  std::call_once(initialization, [runtime] {
    callbacks = runtime;
    berberis::InitBerberis();
    loader = berberis::GuestLoader::StartAppProcessInNewThread(&initialization_error);
    if (loader) berberis::InitializeJNI();
    else std::fprintf(stderr, "Shiraz Digitalis initialization failed: %s\n", initialization_error.c_str());
  });
  return loader != nullptr;
}
void* load(const char* path, int flags) {
  return loader ? loader->DlOpen(path, flags) : nullptr;
}
void* trampoline(void* handle, const char* name, const char* shorty, uint32_t) {
  if (!loader || !name) return nullptr;
  const auto address = loader->DlSym(handle, name);
  if (!address) return nullptr;
  return const_cast<void*>(shorty ? berberis::WrapGuestJNIFunction(address, shorty, name, true)
                                 : berberis::WrapKnownGuestFunction(address, name));
}
bool supports(const char* path) {
  if (!path) return false;
  FILE* file = std::fopen(path, "rb");
  if (!file) return false;
  Elf64_Ehdr header{};
  const bool read = std::fread(&header, 1, sizeof(header), file) == sizeof(header);
  std::fclose(file);
  return read && std::memcmp(header.e_ident, ELFMAG, SELFMAG) == 0 &&
      header.e_ident[EI_CLASS] == ELFCLASS64 && header.e_ident[EI_DATA] == ELFDATA2LSB &&
      header.e_machine == EM_AARCH64 && header.e_type == ET_DYN;
}
const android::NativeBridgeRuntimeValues* environment(const char*) {
  static const android::NativeBridgeRuntimeValues values = {"aarch64", "arm64-v8a", nullptr, nullptr, 0};
  return &values;
}
bool compatible(uint32_t version) { return version >= 1 && version <= 3; }
const char* error() { return loader ? loader->DlError() : initialization_error.c_str(); }
int unload(void*) { return -1; } // GuestLoader currently keeps guest DSOs resident.
using Namespace = android::native_bridge_namespace_t;
Namespace* create_namespace(const char* name, const char* search, const char* defaults,
                            uint64_t type, const char* permitted, Namespace* parent) {
  return loader ? reinterpret_cast<Namespace*>(loader->CreateNamespace(name, search, defaults,
      type, permitted, reinterpret_cast<android_namespace_t*>(parent))) : nullptr;
}
bool link_namespaces(Namespace* from, Namespace* to, const char* libraries) {
  return loader && loader->LinkNamespaces(reinterpret_cast<android_namespace_t*>(from),
      reinterpret_cast<android_namespace_t*>(to), libraries);
}
void* load_extended(const char* path, int flags, Namespace* ns) {
  if (!loader) return nullptr;
  android_dlextinfo info{};
  info.flags = ANDROID_DLEXT_USE_NAMESPACE;
  info.library_namespace = reinterpret_cast<android_namespace_t*>(ns);
  return loader->DlOpenExt(path, flags, ns ? &info : nullptr);
}
android::NativeBridgeSignalHandlerFn signal_handler(int) { return nullptr; }
}
namespace berberis {
// Standalone ART does not wrap RegisterNatives function pointers itself.
static bool SignatureType(const char*& cursor, char& kind, bool allow_void) {
  bool array = false;
  while (*cursor == '[') { array = true; ++cursor; }
  kind = *cursor++;
  if (kind == 'L') {
    const char* end = std::strchr(cursor, ';');
    if (!end || end == cursor) return false;
    cursor = end + 1;
  } else if (kind == 'V') {
    if (!allow_void || array) return false;
  } else if (!std::strchr("ZBCSIJFD", kind) || kind == 0) {
    return false;
  }
  if (array) kind = 'L';
  return true;
}
jint ShirazRegisterGuestNatives(JNIEnv* env, jclass klass, const JNINativeMethod* methods, jint count) {
  if (count < 0 || count > 65536 || (count && !methods)) return JNI_ERR;
  std::vector<JNINativeMethod> wrapped;
  wrapped.reserve(count);
  for (jint i = 0; i < count; ++i) {
    const auto& method = methods[i];
    if (!method.name || !method.signature || !method.fnPtr) return JNI_ERR;
    const char* cursor = method.signature;
    if (*cursor == '!') ++cursor;
    if (*cursor++ != '(') return JNI_ERR;
    std::string shorty(1, 'V');
    while (*cursor && *cursor != ')') {
      char kind;
      if (!SignatureType(cursor, kind, false)) return JNI_ERR;
      shorty += kind;
    }
    if (*cursor++ != ')' || !SignatureType(cursor, shorty[0], true) || *cursor) return JNI_ERR;
    JNINativeMethod entry = method;
    entry.fnPtr = const_cast<void*>(WrapGuestJNIFunction(ToGuestAddr(method.fnPtr),
        shorty.c_str(), method.name, true));
    if (!entry.fnPtr) return JNI_ERR;
    wrapped.push_back(entry);
  }
  return env->RegisterNatives(klass, wrapped.data(), count);
}
const char* GetJMethodShorty(JNIEnv* env, jmethodID method) {
  return callbacks && callbacks->getMethodShorty ? callbacks->getMethodShorty(env, method) : nullptr;
}
}
extern "C" {
android::NativeBridgeCallbacks NativeBridgeItf = [] {
  android::NativeBridgeCallbacks table{};
  table.version = 3;
  table.initialize = initialize;
  table.loadLibrary = load;
  table.getTrampoline = trampoline;
  table.isSupported = supports;
  table.getAppEnv = environment;
  table.isCompatibleWith = compatible;
  table.getSignalHandler = signal_handler;
  table.unloadLibrary = unload;
  table.getError = error;
  table.isPathSupported = supports;
  table.createNamespace = create_namespace;
  table.linkNamespaces = link_namespaces;
  table.loadLibraryExt = load_extended;
  return table;
}();
}

namespace berberis {
void ShirazTraceJniObjectResult(JNIEnv* env,jobject result) {
  if(!std::getenv("SHIRAZ_TRACE_JNI_ARRAYS") || !result || env->ExceptionCheck())return;
  jclass objects=env->FindClass("[Ljava/lang/Object;");
  jclass strings=env->FindClass("[Ljava/lang/String;");
  jclass integers=env->FindClass("[I");
  if(integers && env->IsInstanceOf(result,integers)) {
    auto numbers=static_cast<jintArray>(result);jint values[16];jsize count=env->GetArrayLength(numbers);if(count>16)count=16;
    env->GetIntArrayRegion(numbers,0,count,values);
    for(jsize j=0;j<count;j++)std::fprintf(stderr,"SHIRAZ_JNI_INTS %d=%d\n",j,values[j]);
  }
  if(objects && strings && integers && env->IsInstanceOf(result,objects)) {
    auto array=static_cast<jobjectArray>(result);
    for(jsize i=0;i<env->GetArrayLength(array)&&i<16;i++) {
      jobject item=env->GetObjectArrayElement(array,i);
      if(item && env->IsInstanceOf(item,strings)) {
        auto messages=static_cast<jobjectArray>(item);
        for(jsize j=0;j<env->GetArrayLength(messages)&&j<16;j++) {
          auto message=static_cast<jstring>(env->GetObjectArrayElement(messages,j));
          if(message){const char* text=env->GetStringUTFChars(message,nullptr);if(text){std::fprintf(stderr,"SHIRAZ_JNI_ARRAY %d:%d %.256s\n",i,j,text);env->ReleaseStringUTFChars(message,text);}env->DeleteLocalRef(message);}
        }
      } else if(item && env->IsInstanceOf(item,integers)) {
        auto numbers=static_cast<jintArray>(item);jint values[4];jsize count=env->GetArrayLength(numbers);if(count>4)count=4;
        env->GetIntArrayRegion(numbers,0,count,values);
        for(jsize j=0;j<count;j++)std::fprintf(stderr,"SHIRAZ_JNI_ARRAY %d:%d int=%d\n",i,j,values[j]);
      }
      if(item)env->DeleteLocalRef(item);
    }
  }
  if(objects)env->DeleteLocalRef(objects);if(strings)env->DeleteLocalRef(strings);if(integers)env->DeleteLocalRef(integers);
}
}
