// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/path.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <vector>

// ABI facts, independently implemented as byte views. AndroidX graphics-path
// 1.0.1 release head 8a05a22af450d589ef911d772a001a49dcb05b71:
// https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/src/main/cpp/Path.h
// decoded SHA256 4ee841338063d8c6f71ff4c655ecf52559f6c6df9af79835388dae1ebcd8b982.
// API26-29: points24, reversed-verbs end32, verb count40, points44,
// free space48, conic weights56, generation72; Path's first word is PathRef*.
// No upstream implementation is copied. This is NOT a host Skia object and
// must never be passed to arbitrary SkPath methods. Old point/verb snapshots
// survive mutation until owner destruction, because native iterators copy them.
namespace shiraz::guest {
namespace {
constexpr size_t max_values = 65536, max_paths = 4096;
constexpr size_t max_bytes = 64 * 1024 * 1024;
struct Snapshot {
  std::vector<float> points;
  std::vector<uint8_t> verbs;
  std::vector<float> semantic;
  size_t bytes() const {
    // Include retained allocations and conservative snapshot-index overhead.
    return points.capacity()*sizeof(float)+verbs.capacity()+semantic.capacity()*sizeof(float)
        +sizeof(Snapshot)+2*sizeof(std::unique_ptr<Snapshot>);
  }
};
struct Mirror {
  alignas(8) std::array<uint8_t, 8> path{};
  alignas(8) std::array<uint8_t, 80> ref{};
  std::vector<std::unique_ptr<Snapshot>> snapshots;
  size_t bytes = 0;
  uint32_t generation = 0;
};
static_assert(sizeof(void *) == 8 && sizeof(float) == 4);
std::mutex mirrors_mutex;
std::map<uintptr_t, std::unique_ptr<Mirror>> mirrors;
size_t allocated_bytes = 0;
template<class T, size_t N> void put(std::array<uint8_t,N>& out, size_t offset, T value) {
  std::memcpy(out.data()+offset, &value, sizeof(value));
}
std::unique_ptr<Snapshot> parse(const std::vector<float>& commands) {
  auto s=std::make_unique<Snapshot>(); s->semantic=commands;
  std::vector<uint8_t> chronological;
  bool needs_move=true; float start_x=0, start_y=0;
  auto move=[&](float x,float y) { chronological.push_back(0);s->points.push_back(x);s->points.push_back(y);start_x=x;start_y=y;needs_move=false; };
  for(size_t i=0;i<commands.size();) {
    float tag=commands[i++]; size_t n;
    if(tag==1 || tag==2)n=2;else if(tag==3)n=6;else if(tag==4)n=0;else if(tag==5)n=4;
    else throw std::invalid_argument("invalid native Path command");
    if(n>commands.size()-i)throw std::invalid_argument("truncated native Path command");
    if(tag==1)move(commands[i],commands[i+1]);
    else if(tag==4) {
      if(!chronological.empty() && chronological.back()!=5)chronological.push_back(5);
      needs_move=true;
    } else {
      // API27 SkPath injectMoveToIfNeeded: origin initially, contour start
      // after close. Repeated moveTo is preserved by this Skia release.
      if(needs_move)move(start_x,start_y);
      chronological.push_back(tag==2?1:tag==3?4:2);
      s->points.insert(s->points.end(),commands.begin()+i,commands.begin()+i+n);
    }
    i+=n;
  }
  s->verbs.assign(chronological.rbegin(),chronological.rend());
  return s;
}
void publish(Mirror& m, Snapshot& s, uint32_t generation) {
  put(m.ref,0,int32_t(1));
  float l=0,t=0,r=0,b=0;
  bool finite=true;
  if(!s.points.empty()) { l=r=s.points[0];t=b=s.points[1]; }
  for(size_t i=0;i<s.points.size();i+=2) {
    float x=s.points[i],y=s.points[i+1];finite=finite&&std::isfinite(x)&&std::isfinite(y);
    l=std::min(l,x);r=std::max(r,x);t=std::min(t,y);b=std::max(b,y);
  }
  if(!finite)l=t=r=b=0;
  put(m.ref,4,l);put(m.ref,8,t);put(m.ref,12,r);put(m.ref,16,b);
  put(m.ref,24,s.points.data());
  put(m.ref,32,s.verbs.empty()?s.verbs.data():s.verbs.data()+s.verbs.size());
  put(m.ref,40,int32_t(s.verbs.size()));put(m.ref,44,int32_t(s.points.size()/2));
  put(m.ref,48,size_t(0));put(m.ref,56,uintptr_t(0));
  put(m.ref,64,int32_t(0));put(m.ref,68,int32_t(0));put(m.ref,72,generation);
  m.generation=generation;
}
void fail(JNIEnv* env,const char* type,const char* message) {
  if(env->ExceptionCheck())return;
  jclass cls=env->FindClass(type);
  if(cls){env->ThrowNew(cls,message);env->DeleteLocalRef(cls);}
}
std::mutex field_mutex;
jclass path_class=nullptr;
jmethodID prepare_method=nullptr;
jfieldID path_field=nullptr;
std::shared_ptr<const std::function<bool(const void *)>> reader_validator;
bool cache_path_class(JNIEnv* env,jclass actual) {
  // Raw host JNI produces a real field ID. Cache it before nCreate publishes
  // any nonzero mirror so reads through reflected/inherited/backing-name field
  // lookup also reach the origin guard, regardless of alias-lookup ordering.
  jfieldID backing=env->functions->GetFieldID(env,actual,"shirazNativePath","J");
  if(!backing || env->ExceptionCheck())return false;
  jmethodID method=env->functions->GetMethodID(env,actual,"shirazPrepareNativePath","()V");
  if(!method || env->ExceptionCheck())return false;
  std::lock_guard<std::mutex> lock(field_mutex);
  if(path_class && !env->IsSameObject(actual,path_class)) {
    fail(env,"java/lang/UnsupportedOperationException","native Path class identity changed");
    return false;
  }
  if(!path_class)path_class=static_cast<jclass>(env->NewGlobalRef(actual));
  if(!path_class)return false;
  prepare_method=method;
  path_field=backing;
  return true;
}
bool initialize_path_cache(JNIEnv* env) {
  jclass actual=env->functions->FindClass(env,"android/graphics/Path");
  if(!actual)return false;
  bool ready=cache_path_class(env,actual);
  env->DeleteLocalRef(actual);
  return ready;
}
}
bool resolve_path_field_alias(JNIEnv* env,jclass klass,const char* name,const char* signature,
                              const void* native_entry,jfieldID& field) {
  if(!name || !signature || std::strcmp(name,"mNativePath") || std::strcmp(signature,"J") || env->ExceptionCheck())return false;
  auto validate=std::atomic_load(&reader_validator);
  if(!native_entry || !validate || !*validate || !(*validate)(native_entry))return false;
  // The compatibility alias exists only in this shadow JNI table. Ordinary
  // JNI and other origins still observe no physical mNativePath field, avoiding
  // a null/minimal SkPath being mistaken for a general native Skia object.
  // Undo a failed identity probe so foreign same-named fields retain normal JNI.
  jclass actual=env->FindClass("android/graphics/Path");
  if(!actual){env->ExceptionClear();return false;}
  bool same=env->IsSameObject(klass,actual);
  if(!same){env->DeleteLocalRef(actual);return false;}
  {
    std::lock_guard<std::mutex> lock(field_mutex);
    if(path_class && !env->IsSameObject(actual,path_class)) {
      env->DeleteLocalRef(actual);
      return false;
    }
  }
  field=nullptr;
  if(cache_path_class(env,actual)) {
    std::lock_guard<std::mutex> lock(field_mutex);
    field=path_field;
  }
  env->DeleteLocalRef(actual);
  return true;
}
void set_path_reader_validator(std::function<bool(const void *)> validate) {
  std::atomic_store(&reader_validator,
      std::make_shared<const std::function<bool(const void *)>>(std::move(validate)));
}
bool prepare_path_field(JNIEnv* env,jobject object,jfieldID field,const void* native_entry) {
  jclass klass; jmethodID method;
  {std::lock_guard<std::mutex> lock(field_mutex);if(field!=path_field || !path_class)return true;klass=path_class;method=prepare_method;}
  if(!object || env->ExceptionCheck() || !env->IsInstanceOf(object,klass))return true;
  auto validate=std::atomic_load(&reader_validator);
  if(!native_entry || !validate || !*validate || !(*validate)(native_entry)) {
    fail(env,"java/lang/UnsupportedOperationException",
         "native Path view is available only to audited AndroidXPath JNI entries");
    return false;
  }
  env->CallNonvirtualVoidMethodA(object,klass,method,nullptr);
  return !env->ExceptionCheck();
}
}

extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeGuestPath_nCreate(JNIEnv* env,jclass) {
  using namespace shiraz::guest;
  try {
    if(!initialize_path_cache(env))return 0;
    auto mirror=std::make_unique<Mirror>();
    put(mirror->path,0,mirror->ref.data());
    auto empty=parse({});publish(*mirror,*empty,0);mirror->bytes=empty->bytes();mirror->snapshots.push_back(std::move(empty));
    uintptr_t key=reinterpret_cast<uintptr_t>(mirror->path.data());
    std::lock_guard<std::mutex> lock(mirrors_mutex);
    if(mirrors.size()>=max_paths || mirror->bytes>max_bytes-allocated_bytes)throw std::length_error("native Path live-object limit");
    size_t bytes=mirror->bytes;
    mirrors.emplace(key,std::move(mirror));allocated_bytes+=bytes;return static_cast<jlong>(key);
  }catch(const std::exception& e){fail(env,"java/lang/OutOfMemoryError",e.what());return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeGuestPath_nUpdate(JNIEnv* env,jclass,jlong pointer,jfloatArray array,jint generation) {
  using namespace shiraz::guest;
  try {
    if(!array)throw std::invalid_argument("null native Path commands");
    jsize count=env->GetArrayLength(array);
    if(count<0 || size_t(count)>max_values)throw std::invalid_argument("native Path command limit");
    std::vector<float> commands(static_cast<size_t>(count));
    if(count)env->GetFloatArrayRegion(array,0,count,commands.data());
    if(env->ExceptionCheck())return;
    auto snapshot=parse(commands);
    std::lock_guard<std::mutex> lock(mirrors_mutex);
    auto it=mirrors.find(static_cast<uintptr_t>(pointer));
    if(it==mirrors.end())throw std::invalid_argument("unknown or destroyed native Path");
    auto& m=*it->second;
    if(m.generation==static_cast<uint32_t>(generation) && m.snapshots.back()->semantic.size()==commands.size() &&
       (commands.empty() || !std::memcmp(m.snapshots.back()->semantic.data(),commands.data(),commands.size()*sizeof(float))))return;
    size_t bytes=snapshot->bytes();
    if(m.snapshots.size()>=1024 || bytes>max_bytes-allocated_bytes)throw std::length_error("native Path snapshot storage limit");
    // Allocate retention bookkeeping before changing any published pointer.
    m.snapshots.push_back(std::move(snapshot));
    publish(m,*m.snapshots.back(),static_cast<uint32_t>(generation));
    m.bytes+=bytes;allocated_bytes+=bytes;
  }catch(const std::invalid_argument& e){fail(env,"java/lang/IllegalArgumentException",e.what());}
   catch(const std::exception& e){fail(env,"java/lang/OutOfMemoryError",e.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeGuestPath_nDestroy(JNIEnv* env,jclass,jlong pointer) {
  using namespace shiraz::guest;
  if(!pointer)return;
  std::lock_guard<std::mutex> lock(mirrors_mutex);
  auto it=mirrors.find(static_cast<uintptr_t>(pointer));
  if(it==mirrors.end()){fail(env,"java/lang/IllegalArgumentException","unknown or destroyed native Path");return;}
  allocated_bytes-=it->second->bytes;mirrors.erase(it);
}
