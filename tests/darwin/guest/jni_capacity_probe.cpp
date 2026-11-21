// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/elf.hpp"
#include "shiraz/darwin/guest/jni.hpp"
#include <cstdio>
#include <cstring>
#include <pthread.h>
#include <stdexcept>
using namespace shiraz::guest;
namespace shiraz::guest {
bool is_guest_code(const void*) {
    return true;
}
bool resolve_path_field_alias(JNIEnv*, jclass, const char*, const char*, const void*, jfieldID&) {
    return false;
}
bool prepare_path_field(JNIEnv*, jobject, jfieldID, const void*) {
    return false;
}
} // namespace shiraz::guest
static const char* mode;
extern "C" bool shiraz_test_native_jni_recovery_stack(uintptr_t) noexcept;
extern "C" bool shiraz_test_jni_capacity_recover(Capture*);
extern "C" bool shiraz_test_identity_capacity() {
    if (!std::strcmp(mode, "other"))
        throw std::runtime_error("other setup failure");
    if (!std::strcmp(mode, "duplicate"))
        throw ElfError("duplicate guest identity");
    return true;
}
static JNIEnv env{};
static JavaVM vm{};
static bool pending = false;
static unsigned throws = 0;
static unsigned get_env_calls = 0;
static jint get_env(JavaVM*, void** out, jint version) {
    ++get_env_calls;
    if (version != JNI_VERSION_1_6)
        return JNI_EVERSION;
    *out = &env;
    return JNI_OK;
}
static jboolean has_exception(JNIEnv*) {
    return pending ? JNI_TRUE : JNI_FALSE;
}
static jclass find_class(JNIEnv*, const char* name) {
    return std::strcmp(name, "java/lang/OutOfMemoryError") ? nullptr
                                                           : reinterpret_cast<jclass>(uintptr_t(1));
}
static jint throw_new(JNIEnv*, jclass, const char*) {
    ++throws;
    pending = true;
    return JNI_OK;
}
static void delete_ref(JNIEnv*, jobject) {}
int main(int argc, char** argv) {
    if (argc != 2)
        return 2;
    mode = argv[1];
    if (!std::strcmp(mode, "stack")) {
        uintptr_t current;
        asm volatile("mov %0, sp" : "=r"(current));
        const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
        const auto size = pthread_get_stacksize_np(pthread_self());
        if (!size || high <= size || !shiraz_test_native_jni_recovery_stack(current) ||
            shiraz_test_native_jni_recovery_stack(0) ||
            shiraz_test_native_jni_recovery_stack(current | 1) ||
            shiraz_test_native_jni_recovery_stack(high) ||
            shiraz_test_native_jni_recovery_stack(high - size))
            return 12;
        return 0;
    }
    static JNINativeInterface functions{};
    static JNIInvokeInterface vm_functions{};
    functions.ExceptionCheck = has_exception;
    functions.FindClass = find_class;
    functions.ThrowNew = throw_new;
    functions.DeleteLocalRef = delete_ref;
    env.functions = &functions;
    vm_functions.GetEnv = get_env;
    vm.functions = &vm_functions;
    (void)shadow_vm(&vm);
    if (!std::strcmp(mode, "bad-stack")) {
        Capture capture{};
        capture.frame.integers[0] = reinterpret_cast<uintptr_t>(&env);
        return !shiraz_test_jni_capacity_recover(&capture) && !get_env_calls ? 0 : 13;
    }
    auto* guest = reinterpret_cast<void*>(uintptr_t(0x1000));
    if (!std::strcmp(mode, "onload")) {
        auto fn = reinterpret_cast<jint (*)(JavaVM*, void*)>(onload_trampoline(guest, false));
        return fn(&vm, nullptr) == JNI_ERR ? 0 : 3;
    }
    if (!std::strcmp(mode, "onunload")) {
        auto fn = reinterpret_cast<void (*)(JavaVM*, void*)>(onload_trampoline(guest, true));
        fn(&vm, nullptr);
        return 4;
    }
    if (!std::strcmp(mode, "generic")) {
        auto fn = reinterpret_cast<void (*)()>(closure([](Capture&) {}));
        fn();
        return 5;
    }
    auto integer = reinterpret_cast<jint (*)(JNIEnv*, jobject)>(native_trampoline(guest, "I"));
    if (!std::strcmp(mode, "bad-env")) {
        (void)integer(reinterpret_cast<JNIEnv*>(uintptr_t(0x1234)), nullptr);
        return 6;
    }
    if (!std::strcmp(mode, "duplicate") || !std::strcmp(mode, "other")) {
        (void)integer(&env, nullptr);
        return 7;
    }
    if (std::strcmp(mode, "methods"))
        return 8;
    const auto value = integer(&env, nullptr);
    if (value != 0 || !pending || throws != 1) {
        std::fprintf(stderr, "integer=%d pending=%d throws=%u\n", value, pending, throws);
        return 9;
    }
    auto real = reinterpret_cast<jdouble (*)(JNIEnv*, jobject)>(native_trampoline(guest, "D"));
    if (real(&env, nullptr) != 0.0 || !pending || throws != 1)
        return 10;
    pending = false;
    auto object = reinterpret_cast<jobject (*)(JNIEnv*, jobject)>(native_trampoline(guest, "L"));
    if (object(&env, nullptr) != nullptr || !pending || throws != 2)
        return 11;
    return 0;
}
