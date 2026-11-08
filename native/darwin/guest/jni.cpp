// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/jni.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/va_list.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/path.hpp"
#include <cstdio>
#include <cstring>
#include <map>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <tuple>
#include <type_traits>
#include <pthread.h>

namespace shiraz::guest {
namespace {
const android::NativeBridgeRuntimeCallbacks *runtime_callbacks = nullptr;
// Capture the registered/resolved native entry, not the immediate JNI helper
// return address: weak inline JNI helpers can be preempted by another DSO.
// Nested guest native calls restore their caller's origin on normal/exceptional
// C++ unwinding. Guest-created threads have no authorized origin by default.
thread_local const void *native_entry_origin = nullptr;
struct NativeEntryScope {
  const void *previous;
  explicit NativeEntryScope(const void *entry) : previous(native_entry_origin) {
    native_entry_origin = entry;
  }
  ~NativeEntryScope() { native_entry_origin = previous; }
};
struct ShadowEnv {
  JNIEnv guest;
  JNIEnv *host;
  std::thread::id owner;
};
struct ShadowVM {
  JavaVM guest;
  JavaVM *host;
};
pthread_key_t env_key;
std::once_flag env_key_once;
void ensure_env_key() {
  std::call_once(env_key_once, [] {
    if (pthread_key_create(&env_key, [](void *value) { delete static_cast<ShadowEnv *>(value); }))
      throw std::runtime_error("cannot allocate shadow JNIEnv key");
  });
}
ShadowEnv *current_env() { return static_cast<ShadowEnv *>(pthread_getspecific(env_key)); }
JNINativeInterface env_table{};
JNIInvokeInterface vm_table{};
static_assert(sizeof(JNIInvokeInterface) == 8 * sizeof(void *));
ShadowVM vm_state{};
std::mutex vm_mutex;
std::once_flag tables_once;
JNIEnv *unwrap(JNIEnv *env) {
  auto *thread_env = current_env();
  if (!thread_env || env != &thread_env->guest ||
      thread_env->owner != std::this_thread::get_id())
    throw std::runtime_error(
        "JNIEnv belongs to another thread or is not a guest wrapper");
  return thread_env->host;
}
JavaVM *unwrap(JavaVM *vm) {
  if (vm != &vm_state.guest || !vm_state.host)
    throw std::runtime_error("invalid shadow JavaVM");
  return vm_state.host;
}
std::string bounded(const char *text) {
  if (!text)
    throw std::runtime_error("null guest JNI string");
  size_t length = strnlen(text, 4096);
  if (length == 4096)
    throw std::runtime_error("guest JNI string limit");
  return std::string(text, length);
}
void validate_shorty(const std::string &shorty) {
  if (shorty.empty() || shorty.size() > 257 ||
      !std::strchr("VZBCSIJFDL", shorty[0]))
    throw std::runtime_error("invalid guest JNI shorty");
  for (size_t i = 1; i < shorty.size(); ++i)
    if (!std::strchr("ZBCSIJFDL", shorty[i]))
      throw std::runtime_error("invalid guest JNI parameter");
}
std::string signature_shorty(const char *signature) {
  std::string text = bounded(signature), result(1, 'V');
  size_t cursor = 0;
  auto type = [&](bool allow_void) {
    bool array = false;
    while (cursor < text.size() && text[cursor] == '[') {
      array = true;
      ++cursor;
    }
    if (cursor == text.size())
      throw std::runtime_error("truncated JNI descriptor");
    char kind = text[cursor++];
    if (kind == 'L') {
      size_t end = text.find(';', cursor);
      if (end == std::string::npos || end == cursor)
        throw std::runtime_error("invalid JNI object descriptor");
      cursor = end + 1;
    } else if (!std::strchr("ZBCSIJFD", kind) &&
               !(kind == 'V' && allow_void && !array))
      throw std::runtime_error("unsupported JNI descriptor");
    return array ? 'L' : kind;
  };
  if (text.empty() || text[cursor++] != '(')
    throw std::runtime_error("invalid JNI descriptor start");
  while (cursor < text.size() && text[cursor] != ')')
    result += type(false);
  if (cursor == text.size())
    throw std::runtime_error("missing JNI descriptor close");
  ++cursor;
  result[0] = type(true);
  if (cursor != text.size())
    throw std::runtime_error("trailing JNI descriptor data");
  validate_shorty(result);
  return result;
}
template <typename R, typename... Args>
void *fixed_slot(R (*JNINativeInterface::*member)(JNIEnv *, Args...)) {
  return closure([member](Capture &capture) {
    CallCursor cursor(capture, Convention::Aapcs64);
    JNIEnv *host = unwrap(cursor.read<JNIEnv *>());
    // List initialization guarantees reads happen in argument order.
    std::tuple<Args...> arguments{cursor.read<Args>()...};
    auto function = host->functions->*member;
    if (!function)
      throw std::runtime_error("host JNI slot is unavailable");
    if constexpr (std::is_void_v<R>) {
      std::apply([&](Args... args) { function(host, args...); }, arguments);
    } else {
      R result = std::apply(
          [&](Args... args) { return function(host, args...); }, arguments);
      return_value(capture, result);
    }
  });
}
enum class Family { Instance, Static, Nonvirtual, Constructor };
enum class Variant { Ellipsis, List };
struct Upcall {
  JNIEnv *host;
  jobject object;
  jclass klass;
  jmethodID method;
  std::vector<jvalue> values;
};
Upcall upcall(Capture &capture, Family family, Variant variant) {
  auto &registers = capture.frame.integers;
  unsigned named = family == Family::Nonvirtual ? 4 : 3;
  JNIEnv *host = unwrap(reinterpret_cast<JNIEnv *>(registers[0]));
  jmethodID method = reinterpret_cast<jmethodID>(registers[named - 1]);
  if (!method || !runtime_callbacks || !runtime_callbacks->getMethodShorty)
    throw std::runtime_error("guest JNI method metadata unavailable");
  std::string shorty =
      bounded(runtime_callbacks->getMethodShorty(host, method));
  validate_shorty(shorty);
  GuestVaList list;
  if (variant == Variant::List) {
    auto *pointer = reinterpret_cast<const GuestVaList *>(registers[named]);
    if (!pointer)
      throw std::runtime_error("null guest JNI va_list");
    list = *pointer;
  } else {
    list.stack = reinterpret_cast<uintptr_t>(capture.stack);
    list.integer_top = reinterpret_cast<uintptr_t>(registers + 8);
    list.vector_top = reinterpret_cast<uintptr_t>(capture.frame.vectors + 8);
    list.integer_offset = -int32_t((8 - named) * 8);
    list.vector_offset = -128;
  }
  VaListCursor cursor(list);
  Upcall call{host,
              reinterpret_cast<jobject>(registers[1]),
              reinterpret_cast<jclass>(
                  family == Family::Nonvirtual ? registers[2] : registers[1]),
              method,
              {}};
  for (size_t i = 1; i < shorty.size(); ++i) {
    jvalue value{};
    uint64_t bits = cursor.next(
        shorty[i] == 'D' || shorty[i] == 'F'   ? ArgumentType::Double
        : shorty[i] == 'J' || shorty[i] == 'L' ? ArgumentType::Int64
                                               : ArgumentType::Int32);
    switch (shorty[i]) {
    case 'Z':
      value.z = uint8_t(bits);
      break;
    case 'B':
      value.b = int8_t(bits);
      break;
    case 'C':
      value.c = uint16_t(bits);
      break;
    case 'S':
      value.s = int16_t(bits);
      break;
    case 'I':
      value.i = int32_t(bits);
      break;
    case 'J':
      std::memcpy(&value.j, &bits, 8);
      break;
    case 'L':
      value.l = reinterpret_cast<jobject>(bits);
      break;
    case 'D':
      std::memcpy(&value.d, &bits, 8);
      break;
    case 'F': {
      double promoted;
      std::memcpy(&promoted, &bits, 8);
      value.f = float(promoted);
      break;
    }
    default:
      throw std::runtime_error("unsupported JNI upcall type");
    }
    call.values.push_back(value);
  }
  return call;
}
void build_tables() {
#define SHIRAZ_ENV_SLOT(Name)                                                  \
  env_table.Name = reinterpret_cast<decltype(env_table.Name)>(                 \
      fixed_slot(&JNINativeInterface::Name));
#define SHIRAZ_UPCALL_Instance(A)                                              \
  call.host->functions->A(call.host, call.object, call.method,                 \
                          call.values.data())
#define SHIRAZ_UPCALL_Static(A)                                                \
  call.host->functions->A(call.host, call.klass, call.method,                  \
                          call.values.data())
#define SHIRAZ_UPCALL_Nonvirtual(A)                                            \
  call.host->functions->A(call.host, call.object, call.klass, call.method,     \
                          call.values.data())
#define SHIRAZ_UPCALL_Constructor(A)                                           \
  call.host->functions->A(call.host, call.klass, call.method,                  \
                          call.values.data())
#define SHIRAZ_RESULT_Object(value) return_value(capture, value)
#define SHIRAZ_RESULT_Boolean(value) return_value(capture, value)
#define SHIRAZ_RESULT_Byte(value) return_value(capture, value)
#define SHIRAZ_RESULT_Char(value) return_value(capture, value)
#define SHIRAZ_RESULT_Short(value) return_value(capture, value)
#define SHIRAZ_RESULT_Int(value) return_value(capture, value)
#define SHIRAZ_RESULT_Long(value) return_value(capture, value)
#define SHIRAZ_RESULT_Float(value) return_value(capture, value)
#define SHIRAZ_RESULT_Double(value) return_value(capture, value)
#define SHIRAZ_RESULT_Void(value) value
#define SHIRAZ_ENV_CALL(Name, A, F, K, V)                                      \
  env_table.Name = reinterpret_cast<decltype(env_table.Name)>(                 \
      closure([](Capture &capture) {                                           \
        auto call = upcall(capture, Family::F, Variant::V);                    \
        SHIRAZ_RESULT_##K(SHIRAZ_UPCALL_##F(A));                               \
      }));
#include "guest_jni_slots.inc"
#undef SHIRAZ_ENV_SLOT
  env_table.GetFieldID = reinterpret_cast<decltype(env_table.GetFieldID)>(
      closure([](Capture &capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        JNIEnv *host = unwrap(cursor.read<JNIEnv *>());
        jclass klass = cursor.read<jclass>();
        const char *name = cursor.read<const char *>();
        const char *signature = cursor.read<const char *>();
        // Validate guest strings before passing them through real JNI.
        bounded(name); bounded(signature);
        jfieldID field = nullptr;
        if (!resolve_path_field_alias(host, klass, name, signature,
                                      native_entry_origin, field))
          field = host->functions->GetFieldID(host, klass, name, signature);
        return_value(capture, field);
      }));
  env_table.GetLongField = reinterpret_cast<decltype(env_table.GetLongField)>(
      closure([](Capture &capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        JNIEnv *host = unwrap(cursor.read<JNIEnv *>());
        jobject object = cursor.read<jobject>();
        jfieldID field = cursor.read<jfieldID>();
        if (!prepare_path_field(host, object, field, native_entry_origin)) {
          return_value(capture, jlong(0));
          return;
        }
        return_value(capture, host->functions->GetLongField(host, object, field));
      }));
#undef SHIRAZ_ENV_CALL
  env_table.GetJavaVM = reinterpret_cast<decltype(env_table.GetJavaVM)>(
      closure([](Capture &capture) {
        auto *host =
            unwrap(reinterpret_cast<JNIEnv *>(capture.frame.integers[0]));
        auto **output = reinterpret_cast<JavaVM **>(capture.frame.integers[1]);
        if (!output)
          throw std::runtime_error("null GetJavaVM output");
        JavaVM *real = nullptr;
        jint result = host->functions->GetJavaVM(host, &real);
        *output = result == JNI_OK ? shadow_vm(real) : nullptr;
        return_value(capture, result);
      }));
  env_table.RegisterNatives =
      reinterpret_cast<decltype(env_table.RegisterNatives)>(
          closure([](Capture &capture) {
            CallCursor cursor(capture, Convention::Aapcs64);
            JNIEnv *host = unwrap(cursor.read<JNIEnv *>());
            jclass klass = cursor.read<jclass>();
            auto *methods = cursor.read<const JNINativeMethod *>();
            jint count = cursor.read<jint>();
            if (count < 0 || count > 65536 || (count && !methods))
              throw std::runtime_error("invalid guest RegisterNatives count");
            std::vector<JNINativeMethod> wrapped;
            for (jint i = 0; i < count; ++i) {
              if (!methods[i].fnPtr || bounded(methods[i].name).empty())
                throw std::runtime_error("invalid guest native registration");
              auto entry = methods[i];
              entry.fnPtr = native_trampoline(
                  entry.fnPtr, signature_shorty(entry.signature));
              wrapped.push_back(entry);
            }
            return_value(capture, host->functions->RegisterNatives(
                                      host, klass, wrapped.data(), count));
          }));
  vm_table.GetEnv =
      reinterpret_cast<decltype(vm_table.GetEnv)>(closure([](Capture &capture) {
        JavaVM *host =
            unwrap(reinterpret_cast<JavaVM *>(capture.frame.integers[0]));
        auto **output = reinterpret_cast<void **>(capture.frame.integers[1]);
        jint version = jint(capture.frame.integers[2]);
        if (!output)
          throw std::runtime_error("null GetEnv output");
        *output = nullptr;
        if (version != JNI_VERSION_1_1 && version != JNI_VERSION_1_2 &&
            version != JNI_VERSION_1_4 && version != JNI_VERSION_1_6) {
          return_value(capture, jint(JNI_EVERSION));
          return;
        }
        void *env = nullptr;
        jint result = host->functions->GetEnv(host, &env, version);
        if (result == JNI_OK)
          *output = shadow_env(static_cast<JNIEnv *>(env));
        return_value(capture, result);
      }));
  auto attach = [](bool daemon) {
    return closure([daemon](Capture &capture) {
      JavaVM *host =
          unwrap(reinterpret_cast<JavaVM *>(capture.frame.integers[0]));
      auto **output = reinterpret_cast<JNIEnv **>(capture.frame.integers[1]);
      auto *args = reinterpret_cast<void *>(capture.frame.integers[2]);
      if (!output)
        throw std::runtime_error("null AttachCurrentThread output");
      JNIEnv *env = nullptr;
      jint result =
          daemon
              ? host->functions->AttachCurrentThreadAsDaemon(host, &env, args)
              : host->functions->AttachCurrentThread(host, &env, args);
      *output = result == JNI_OK ? shadow_env(env) : nullptr;
      return_value(capture, result);
    });
  };
  vm_table.AttachCurrentThread =
      reinterpret_cast<decltype(vm_table.AttachCurrentThread)>(attach(false));
  vm_table.AttachCurrentThreadAsDaemon =
      reinterpret_cast<decltype(vm_table.AttachCurrentThreadAsDaemon)>(
          attach(true));
  vm_table.DetachCurrentThread =
      reinterpret_cast<decltype(vm_table.DetachCurrentThread)>(
          closure([](Capture &capture) {
            JavaVM *host =
                unwrap(reinterpret_cast<JavaVM *>(capture.frame.integers[0]));
            jint result = host->functions->DetachCurrentThread(host);
            if (result == JNI_OK) {
              auto *wrapper = current_env();
              if (pthread_setspecific(env_key, nullptr))
                throw std::runtime_error("cannot clear detached shadow JNIEnv");
              delete wrapper;
            }
            return_value(capture, result);
          }));
  vm_table.DestroyJavaVM = reinterpret_cast<decltype(vm_table.DestroyJavaVM)>(
      closure([](Capture &capture) {
        std::fprintf(stderr,
                     "SHIRAZ_GUEST_UNSUPPORTED DestroyJavaVM from guest\n");
        return_value(capture, jint(JNI_ERR));
      }));
}
} // namespace
void initialize_jni(const android::NativeBridgeRuntimeCallbacks *callbacks) {
  if (!callbacks || !callbacks->getMethodShorty)
    throw std::runtime_error("NativeBridge JNI callbacks missing");
  runtime_callbacks = callbacks;
  ensure_env_key();
  std::call_once(tables_once, build_tables);
}
JNIEnv *host_env(JNIEnv *guest) { return unwrap(guest); }
JNIEnv *shadow_env(JNIEnv *host) {
  if (!host)
    throw std::runtime_error("null host JNIEnv");
  auto *thread_env = current_env();
  if (!thread_env) {
    auto wrapper = std::make_unique<ShadowEnv>();
    if (pthread_setspecific(env_key, wrapper.get()))
      throw std::runtime_error("cannot store shadow JNIEnv");
    thread_env = wrapper.release();
  }
  thread_env->guest.functions = &env_table;
  thread_env->host = host;
  thread_env->owner = std::this_thread::get_id();
  return &thread_env->guest;
}
JavaVM *shadow_vm(JavaVM *host) {
  if (!host)
    throw std::runtime_error("null host JavaVM");
  std::lock_guard<std::mutex> lock(vm_mutex);
  if (vm_state.host && vm_state.host != host)
    throw std::runtime_error("multiple host JavaVMs unsupported");
  if (!vm_state.host) {
    vm_state.host = host;
    vm_state.guest.functions = &vm_table;
  }
  return &vm_state.guest;
}
void *native_trampoline(void *guest, const std::string &shorty) {
  validate_shorty(shorty);
  if (!guest || !is_guest_code(guest))
    throw std::runtime_error("native entry is outside guest executable images");
  static std::mutex mutex;
  static std::map<std::pair<uintptr_t, std::string>, void *> cache;
  std::lock_guard<std::mutex> lock(mutex);
  auto key = std::make_pair(reinterpret_cast<uintptr_t>(guest), shorty);
  auto found = cache.find(key);
  if (found != cache.end())
    return found->second;
  void *entry = closure([guest, shorty](Capture &capture) {
    NativeEntryScope origin(guest);
    prepare_guest_tls();
    CallCursor cursor(capture, Convention::Apple);
    JNIEnv *host = cursor.read<JNIEnv *>();
    jobject object = cursor.read<jobject>();
    std::vector<Argument> args{
        Argument::integer(reinterpret_cast<uintptr_t>(shadow_env(host)),
                          ArgumentType::Int64),
        Argument::integer(reinterpret_cast<uintptr_t>(object),
                          ArgumentType::Int64),
    };
    for (size_t i = 1; i < shorty.size(); ++i) {
      switch (shorty[i]) {
      case 'Z':
        args.push_back(Argument::integer(cursor.read<jboolean>()));
        break;
      case 'B':
        args.push_back(Argument::integer(cursor.read<jbyte>()));
        break;
      case 'C':
        args.push_back(Argument::integer(cursor.read<jchar>()));
        break;
      case 'S':
        args.push_back(Argument::integer(cursor.read<jshort>()));
        break;
      case 'I':
        args.push_back(Argument::integer(cursor.read<jint>()));
        break;
      case 'J':
        args.push_back(
            Argument::integer(cursor.read<jlong>(), ArgumentType::Int64));
        break;
      case 'L':
        args.push_back(Argument::integer(
            reinterpret_cast<uintptr_t>(cursor.read<jobject>()),
            ArgumentType::Int64));
        break;
      case 'F':
        args.push_back(Argument::real(cursor.read<jfloat>()));
        break;
      case 'D':
        args.push_back(Argument::real(cursor.read<jdouble>()));
        break;
      default:
        throw std::runtime_error("invalid JNI native argument");
      }
    }
    auto result = invoke(guest, args);
    capture.frame.result_integers[0] = result.result_integers[0];
    capture.frame.result_integers[1] = result.result_integers[1];
    std::memcpy(capture.frame.result_vectors, result.result_vectors,
                sizeof(result.result_vectors));
    // Apple callers require the callee to extend narrow integral results.
    switch (shorty[0]) {
    case 'Z':
      return_value(capture, jboolean(result.result_integers[0]));
      break;
    case 'B':
      return_value(capture, jbyte(result.result_integers[0]));
      break;
    case 'C':
      return_value(capture, jchar(result.result_integers[0]));
      break;
    case 'S':
      return_value(capture, jshort(result.result_integers[0]));
      break;
    default:
      break;
    }
  });
  cache.emplace(std::move(key), entry);
  return entry;
}
void *onload_trampoline(void *guest, bool unload, const std::string &module) {
  return closure([guest, unload, module](Capture &capture) {
    NativeEntryScope origin(guest);
    prepare_guest_tls();
    JavaVM *host = reinterpret_cast<JavaVM *>(capture.frame.integers[0]);
    auto result = invoke(
        guest,
        {
            Argument::integer(reinterpret_cast<uintptr_t>(shadow_vm(host)),
                              ArgumentType::Int64),
            Argument::integer(int64_t(capture.frame.integers[1]),
                              ArgumentType::Int64),
        });
    if (!unload) {
      std::fprintf(stderr, "SHIRAZ_GUEST_JNI_ONLOAD module=%s version=0x%x\n",
                   module.c_str(), unsigned(jint(result.result_integers[0])));
      return_value(capture, jint(result.result_integers[0]));
    }
  });
}
} // namespace shiraz::guest
