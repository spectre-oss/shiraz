/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Shiraz glibc adaptation; additional code is GPL-3.0-only.
// SPDX-License-Identifier: GPL-3.0-only AND Apache-2.0
// Experimental glibc proxy: reject unsupported ABI calls explicitly.
#include <cstdio>
#include <cstdlib>
#include <malloc.h>
#include <unistd.h>
#include <cstring>
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_os_primitives/scoped_pending_signals.h"
#include "guest_pthread_attributes.h"
#include <string>
#include <vector>
#include "android_profile_properties.h"
#include <mutex>
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
namespace berberis {
static void Unsupported(HostCode name, ThreadState*) {
 std::fprintf(stderr, "Shiraz Digitalis: unsupported libc bridge: %s\n", static_cast<const char*>(name));
 std::abort();
}
// Immutable per-process properties; no Android property daemon is present.
// Preserve Bionic's short-value prop_info prefix for native callers.
struct Property { uint32_t serial; char value[92]; char name[128]; };
static std::vector<Property> properties;
static std::once_flag properties_once;
static int PropertiesInit() {
 std::call_once(properties_once, [] {
  const char* display = std::getenv("SHIRAZ_ANDROID_DISPLAY_ID");
  const char* release = std::getenv("SHIRAZ_ANDROID_RELEASE");
  const char* names[] = {"ro.build.version.sdk", "ro.build.version.release", "ro.build.display.id",
    "ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.product.cpu.abilist64", "ro.product.cpu.abilist32"};
  const char* values[] = {"27", release ? release : "8.1", display ? display : "Shiraz",
    "arm64-v8a", "arm64-v8a", "arm64-v8a", ""};
  properties.resize(7);
  for (size_t i=0; i<properties.size(); ++i) {
   auto& p=properties[i];
   std::snprintf(p.name,sizeof(p.name),"%s",names[i]);
   std::snprintf(p.value,sizeof(p.value),"%s",values[i]);
   p.serial=static_cast<uint32_t>(std::strlen(p.value)) << 24;
  }
  for (const auto& entry : shiraz_profile_properties) {
   const char* value = std::getenv(entry.environment);
   if (!value || std::strlen(value) >= 92) continue;
   Property p{};
   std::snprintf(p.name,sizeof(p.name),"%s",entry.name);
   std::snprintf(p.value,sizeof(p.value),"%s",value);
   p.serial=static_cast<uint32_t>(std::strlen(p.value)) << 24;
   properties.push_back(p);
  }
 });
 return 0;
}
static const Property* PropertyFind(const char* name) {
 PropertiesInit();
 if (std::getenv("SHIRAZ_TRACE_PROPERTIES") && name) std::fprintf(stderr,"SHIRAZ_PROPERTY_LOOKUP %.128s\n",name);
 if (name) for (const auto& p:properties) if (std::strcmp(name,p.name)==0) return &p;
 return nullptr;
}
static const Property* PropertyNth(uint32_t index) { PropertiesInit(); return index<properties.size()?&properties[index]:nullptr; }
static int PropertyGet(const char* name, char* value) {
 const auto* p=PropertyFind(name);
 if (!value) return 0;
 std::strcpy(value,p?p->value:"");
 return static_cast<int>(std::strlen(value));
}
static int PropertyRead(const Property* p, char* name, char* value) {
 if (!p) return 0;
 if (name) std::strcpy(name,p->name);
 if (value) std::strcpy(value,p->value);
 return static_cast<int>(std::strlen(p->value));
}
static int PropertyForeach(void(*callback)(const Property*,void*),void* cookie) {
 PropertiesInit();
 if (!callback) return -1;
 for (const auto& p:properties) callback(&p,cookie);
 return 0;
}
static void PropertyReadCallback(const Property* p,void(*callback)(void*,const char*,const char*,uint32_t),void* cookie) {
 if (p && callback) callback(cookie,p->name,p->value,p->serial);
}
static uint32_t PropertySerial(const Property* p) { return p?p->serial:0; }
static uint32_t PropertyAreaSerial() { return 0; }
// Follows AOSP native_bridge_support proxy/unistd_thunks.cc: retain the
// guest thread across host fork, then discard inherited thread-map entries.
void ShirazRefreshGuestThreadIdentity();
static pid_t CloneForFork() {
 GuestThread* thread = GetCurrentGuestThread();
 pid_t pid = fork();
 if (pid == 0) {
  ResetCurrentGuestThreadAfterFork(thread);
  ShirazRefreshGuestThreadIdentity();
 }
 return pid;
}
static uintptr_t StackTop() { return GetCurrentGuestThread()->GetStackTop(); }
static void ThreadCreate(HostCode, ThreadState* state) {
 using Signature = int(pthread_t*, const ShirazGuestPthreadAttributes*, void*(*)(void*), void*);
 auto [thread, attributes, function, argument] = GuestParamsValues<Signature>(state);
 auto&& [result] = GuestReturnReference<Signature>(state);
 if (!thread || !ToGuestAddr(function)) { result=EINVAL; return; }
 ShirazHostPthreadAttributes translated(attributes);
 if (translated.error) { result=translated.error; return; }
 result=CreateNewGuestThread(thread, &translated.host, translated.stack,
     translated.stack_size, translated.guard_size, ToGuestAddr(function), ToGuestAddr(argument));
}
static void ThreadJoin(HostCode, ThreadState* state) {
 auto [thread, value] = GuestParamsValues<decltype(pthread_join)>(state);
 auto&& [result] = GuestReturnReference<decltype(pthread_join)>(state);
 ScopedPendingSignalsDisabler pending(state->thread);
 result=pthread_join(thread,value);
}
static void KeyCreate(HostCode, ThreadState* state) {
 auto [key, destructor] = GuestParamsValues<int(pthread_key_t*, void(*)(void*))>(state);
 auto&& [result] = GuestReturnReference<int(pthread_key_t*, void(*)(void*))>(state);
 using Dtor = void(*)(void*);
 Dtor wrapped = ToGuestAddr(destructor) ? AsFuncPtr<Dtor>(WrapGuestFunctionImpl(
   ToGuestAddr(destructor), kGuestFunctionWrapperSignature<Dtor>, RunGuestPthreadKeyDtor,
   "shiraz-pthread-destructor")) : nullptr;
 result = pthread_key_create(key, wrapped);
}
static const KnownTrampoline functions[] = {
{"__clone_for_fork", GetTrampolineFunc<int32_t()>(), reinterpret_cast<void*>(CloneForFork)},
{"__get_thread_stack_top", GetTrampolineFunc<uintptr_t()>(), reinterpret_cast<void*>(StackTop)},
{"__pthread_cleanup_pop", Unsupported, const_cast<char*>("__pthread_cleanup_pop")},
{"__pthread_cleanup_push", Unsupported, const_cast<char*>("__pthread_cleanup_push")},
{"__system_properties_init", GetTrampolineFunc<int()>(), reinterpret_cast<void*>(PropertiesInit)},
{"__system_property_add", Unsupported, const_cast<char*>("__system_property_add")},
{"__system_property_area_init", Unsupported, const_cast<char*>("__system_property_area_init")},
{"__system_property_area_serial", GetTrampolineFunc<uint32_t()>(), reinterpret_cast<void*>(PropertyAreaSerial)},
{"__system_property_find", GetTrampolineFunc<const Property*(const char*)>(), reinterpret_cast<void*>(PropertyFind)},
{"__system_property_find_nth", GetTrampolineFunc<const Property*(uint32_t)>(), reinterpret_cast<void*>(PropertyNth)},
{"__system_property_foreach", GetTrampolineFunc<int(void(*)(const Property*,void*),void*)>(), reinterpret_cast<void*>(PropertyForeach)},
{"__system_property_get", GetTrampolineFunc<int(const char*,char*)>(), reinterpret_cast<void*>(PropertyGet)},
{"__system_property_read", GetTrampolineFunc<int(const Property*,char*,char*)>(), reinterpret_cast<void*>(PropertyRead)},
{"__system_property_read_callback", GetTrampolineFunc<void(const Property*,void(*)(void*,const char*,const char*,uint32_t),void*)>(), reinterpret_cast<void*>(PropertyReadCallback)},
{"__system_property_serial", GetTrampolineFunc<uint32_t(const Property*)>(), reinterpret_cast<void*>(PropertySerial)},
{"__system_property_set", Unsupported, const_cast<char*>("__system_property_set")},
{"__system_property_set_filename", Unsupported, const_cast<char*>("__system_property_set_filename")},
{"__system_property_update", Unsupported, const_cast<char*>("__system_property_update")},
{"__system_property_wait", Unsupported, const_cast<char*>("__system_property_wait")},
{"__system_property_wait_any", Unsupported, const_cast<char*>("__system_property_wait_any")},
{"_longjmp", Unsupported, const_cast<char*>("_longjmp")},
{"_setjmp", Unsupported, const_cast<char*>("_setjmp")},
{"android_crash_detail_register", Unsupported, const_cast<char*>("android_crash_detail_register")},
{"android_crash_detail_replace_data", Unsupported, const_cast<char*>("android_crash_detail_replace_data")},
{"android_crash_detail_replace_name", Unsupported, const_cast<char*>("android_crash_detail_replace_name")},
{"android_crash_detail_unregister", Unsupported, const_cast<char*>("android_crash_detail_unregister")},
{"android_getaddrinfofornet", Unsupported, const_cast<char*>("android_getaddrinfofornet")},
{"android_getaddrinfofornetcontext", Unsupported, const_cast<char*>("android_getaddrinfofornetcontext")},
{"android_mallopt", Unsupported, const_cast<char*>("android_mallopt")},
{"android_set_abort_message", Unsupported, const_cast<char*>("android_set_abort_message")},
{"clock_getres", GetTrampolineFunc<auto(int32_t, void*) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"clock_gettime", GetTrampolineFunc<auto(int32_t, void*) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"freeaddrinfo", Unsupported, const_cast<char*>("freeaddrinfo")},
{"gai_strerror", Unsupported, const_cast<char*>("gai_strerror")},
{"getaddrinfo", Unsupported, const_cast<char*>("getaddrinfo")},
{"gethostbyaddr", Unsupported, const_cast<char*>("gethostbyaddr")},
{"gethostbyname", Unsupported, const_cast<char*>("gethostbyname")},
{"getnameinfo", Unsupported, const_cast<char*>("getnameinfo")},
{"gettimeofday", GetTrampolineFunc<auto(void*, void*) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"longjmp", Unsupported, const_cast<char*>("longjmp")},
{"memchr", GetTrampolineFunc<auto(const void*, int, size_t) -> void*>(), nullptr},
{"memcmp", GetTrampolineFunc<auto(const void*, const void*, size_t) -> int>(), nullptr},
{"memcpy", GetTrampolineFunc<auto(void*, const void*, size_t) -> void*>(), nullptr},
{"memset", GetTrampolineFunc<auto(void*, int, size_t) -> void*>(), nullptr},
{"strlen", GetTrampolineFunc<auto(const char*) -> size_t>(), nullptr},
{"native_bridge___cxa_thread_atexit_impl", Unsupported, const_cast<char*>("native_bridge___cxa_thread_atexit_impl")},
{"native_bridge_aligned_alloc", GetTrampolineFunc<auto(size_t, size_t) -> void*>(), reinterpret_cast<void*>(aligned_alloc)},
{"native_bridge_calloc", GetTrampolineFunc<auto(size_t, size_t) -> void*>(), reinterpret_cast<void*>(calloc)},
{"native_bridge_exit", GetTrampolineFunc<auto(int32_t) -> void>(), reinterpret_cast<void*>(exit)},
{"native_bridge_free", GetTrampolineFunc<auto(void*) -> void>(), reinterpret_cast<void*>(free)},
{"native_bridge_mallinfo", Unsupported, const_cast<char*>("native_bridge_mallinfo")},
{"native_bridge_malloc", GetTrampolineFunc<auto(size_t) -> void*>(), reinterpret_cast<void*>(malloc)},
{"native_bridge_malloc_disable", Unsupported, const_cast<char*>("native_bridge_malloc_disable")},
{"native_bridge_malloc_enable", Unsupported, const_cast<char*>("native_bridge_malloc_enable")},
{"native_bridge_malloc_info_helper", Unsupported, const_cast<char*>("native_bridge_malloc_info_helper")},
{"native_bridge_malloc_iterate", Unsupported, const_cast<char*>("native_bridge_malloc_iterate")},
{"native_bridge_malloc_usable_size", GetTrampolineFunc<auto(void*) -> size_t>(), reinterpret_cast<void*>(malloc_usable_size)},
{"native_bridge_mallopt", GetTrampolineFunc<auto(int32_t, int32_t) -> int32_t>(), reinterpret_cast<void*>(mallopt)},
{"native_bridge_memalign", GetTrampolineFunc<auto(size_t, size_t) -> void*>(), reinterpret_cast<void*>(memalign)},
{"native_bridge_posix_memalign", GetTrampolineFunc<auto(void*, size_t, size_t) -> int32_t>(), reinterpret_cast<void*>(posix_memalign)},
{"native_bridge_realloc", GetTrampolineFunc<auto(void*, size_t) -> void*>(), reinterpret_cast<void*>(realloc)},
{"pthread_attr_destroy", Unsupported, const_cast<char*>("pthread_attr_destroy")},
{"pthread_attr_getdetachstate", Unsupported, const_cast<char*>("pthread_attr_getdetachstate")},
{"pthread_attr_getguardsize", Unsupported, const_cast<char*>("pthread_attr_getguardsize")},
{"pthread_attr_getinheritsched", Unsupported, const_cast<char*>("pthread_attr_getinheritsched")},
{"pthread_attr_getschedparam", Unsupported, const_cast<char*>("pthread_attr_getschedparam")},
{"pthread_attr_getschedpolicy", Unsupported, const_cast<char*>("pthread_attr_getschedpolicy")},
{"pthread_attr_getscope", Unsupported, const_cast<char*>("pthread_attr_getscope")},
{"pthread_attr_getstack", Unsupported, const_cast<char*>("pthread_attr_getstack")},
{"pthread_attr_getstacksize", Unsupported, const_cast<char*>("pthread_attr_getstacksize")},
{"pthread_attr_init", Unsupported, const_cast<char*>("pthread_attr_init")},
{"pthread_attr_setdetachstate", Unsupported, const_cast<char*>("pthread_attr_setdetachstate")},
{"pthread_attr_setguardsize", Unsupported, const_cast<char*>("pthread_attr_setguardsize")},
{"pthread_attr_setinheritsched", Unsupported, const_cast<char*>("pthread_attr_setinheritsched")},
{"pthread_attr_setschedparam", Unsupported, const_cast<char*>("pthread_attr_setschedparam")},
{"pthread_attr_setschedpolicy", Unsupported, const_cast<char*>("pthread_attr_setschedpolicy")},
{"pthread_attr_setscope", Unsupported, const_cast<char*>("pthread_attr_setscope")},
{"pthread_attr_setstack", Unsupported, const_cast<char*>("pthread_attr_setstack")},
{"pthread_attr_setstacksize", Unsupported, const_cast<char*>("pthread_attr_setstacksize")},
{"pthread_create", ThreadCreate, nullptr},
{"pthread_detach", GetTrampolineFunc<int(pthread_t)>(), reinterpret_cast<void*>(pthread_detach)},
{"pthread_exit", Unsupported, const_cast<char*>("pthread_exit")},
{"pthread_getattr_np", Unsupported, const_cast<char*>("pthread_getattr_np")},
{"pthread_getcpuclockid", Unsupported, const_cast<char*>("pthread_getcpuclockid")},
{"pthread_getname_np", Unsupported, const_cast<char*>("pthread_getname_np")},
{"pthread_getschedparam", Unsupported, const_cast<char*>("pthread_getschedparam")},
{"pthread_getspecific", GetTrampolineFunc<auto(int32_t) -> void*>(), reinterpret_cast<void*>(NULL)},
{"pthread_gettid_np", Unsupported, const_cast<char*>("pthread_gettid_np")},
{"pthread_join", ThreadJoin, nullptr},
{"pthread_key_create", KeyCreate, nullptr},
{"pthread_key_delete", GetTrampolineFunc<auto(int32_t) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"pthread_kill", Unsupported, const_cast<char*>("pthread_kill")},
{"pthread_setname_np", Unsupported, const_cast<char*>("pthread_setname_np")},
{"pthread_setschedparam", Unsupported, const_cast<char*>("pthread_setschedparam")},
{"pthread_setschedprio", Unsupported, const_cast<char*>("pthread_setschedprio")},
{"pthread_setspecific", GetTrampolineFunc<auto(int32_t, void*) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"pthread_sigqueue", Unsupported, const_cast<char*>("pthread_sigqueue")},
{"setjmp", Unsupported, const_cast<char*>("setjmp")},
{"siglongjmp", Unsupported, const_cast<char*>("siglongjmp")},
{"sigsetjmp", Unsupported, const_cast<char*>("sigsetjmp")},
{"time", GetTrampolineFunc<auto(void*) -> long>(), reinterpret_cast<void*>(NULL)},
};
static const KnownVariable variables[] = {{"environ", sizeof(void*)}};
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* b) {
 b->Build("libc.so.6", sizeof(functions)/sizeof(functions[0]), functions, 1, variables);
}
}
