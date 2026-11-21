// SPDX-License-Identifier: GPL-3.0-only
// Disposable, outer-sandbox probe only. This deliberately relies on the local
// standalone ART's ArtMethod layout and must never be linked into the bridge.
#include <jni.h>
#include "shiraz/darwin/thread_identity.hpp"
#include <dlfcn.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>

namespace {
using Lookup = jclass (*)(JNIEnv*, jclass, jstring, jboolean, jobject);
Lookup original = nullptr;
std::atomic<unsigned> observations{0};
std::atomic_flag installed = ATOMIC_FLAG_INIT;
using ThreadId = jint (*)(JNIEnv*, jclass);
ThreadId original_tid = nullptr;
using GroupId = jint (*)(JNIEnv*, jclass, jstring);
GroupId original_gid = nullptr;
std::atomic<unsigned> group_observations{0};

jint observe_gid(JNIEnv* env, jclass klass, jstring name) {
    const char* kind = "other";
    if (!name)
        kind = "null";
    else if (env->GetStringLength(name) == 0)
        kind = "empty";
    else if (env->GetStringLength(name) <= 64) {
        const char* chars = env->GetStringUTFChars(name, nullptr);
        if (chars) {
            if (chars[0] >= '0' && chars[0] <= '9')
                kind = "numeric";
            else if (!std::strcmp(chars, "root") || !std::strcmp(chars, "system") ||
                     !std::strcmp(chars, "shell") || !std::strcmp(chars, "radio") ||
                     !std::strcmp(chars, "wifi") || !std::strcmp(chars, "inet"))
                kind = "known_android";
            env->ReleaseStringUTFChars(name, chars);
        }
    }
    if (env->ExceptionCheck())
        return -1;
    jint value = original_gid(env, klass, name);
    if (!env->ExceptionCheck() && group_observations.fetch_add(1) < 32)
        std::fprintf(stderr, "SHIRAZ_PROBE_GID kind=%s result=%s\n", kind,
                     value < 0    ? "negative"
                     : value == 0 ? "zero"
                                  : "positive");
    return value;
}

jint probe_small_tid(JNIEnv* env, jclass klass) {
    // Diagnostic only: this deliberately does not implement thread identity or
    // priority semantics and must not be used outside the disposable process.
    jint actual = original_tid(env, klass);
    jint mapped = actual & ((1 << 22) - 1);
    return mapped ? mapped : 1;
}

jclass observe(JNIEnv* env, jclass klass, jstring name, jboolean init, jobject loader) {
    jclass result = original(env, klass, name, init, loader);
    jthrowable thrown = env->ExceptionOccurred();
    if (!thrown) {
        if (result && name && observations.fetch_add(1) < 128) {
            const char* text = env->GetStringUTFChars(name, nullptr);
            if (text) {
                uint64_t fingerprint = UINT64_C(14695981039346656037);
                for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p;
                     ++p) {
                    fingerprint = (fingerprint ^ *p) * UINT64_C(1099511628211);
                }
                std::fprintf(stderr, "SHIRAZ_PROBE_CLASS_FOUND fingerprint=%016llx length=%zu\n",
                             static_cast<unsigned long long>(fingerprint), std::strlen(text));
                env->ReleaseStringUTFChars(name, text);
            }
            if (env->ExceptionCheck())
                env->ExceptionClear();
        }
        return result;
    }
    env->ExceptionClear();
    jclass missing = env->FindClass("java/lang/ClassNotFoundException");
    bool match = missing && env->IsInstanceOf(thrown, missing);
    if (env->ExceptionCheck())
        env->ExceptionClear();
    if (match && observations.fetch_add(1) < 128 && name) {
        const char* text = env->GetStringUTFChars(name, nullptr);
        if (text) {
            bool safe =
                !std::strncmp(text, "android.", 8) || !std::strncmp(text, "java.", 5) ||
                !std::strncmp(text, "javax.", 6) || !std::strncmp(text, "dalvik.", 7) ||
                !std::strncmp(text, "libcore.", 8) || !std::strncmp(text, "androidx.", 9) ||
                !std::strncmp(text, "com.android.", 12) || !std::strncmp(text, "com.google.", 11) ||
                !std::strncmp(text, "org.apache.", 11) || !std::strncmp(text, "org.json.", 9);
            const char* package = std::getenv("SHIRAZ_PROBE_APP_PACKAGE");
            if (package && *package) {
                size_t package_length = std::strlen(package);
                safe = safe || (!std::strncmp(text, package, package_length) &&
                                text[package_length] == '.');
            }
            safe = safe && std::strlen(text) <= 200;
            uint64_t fingerprint = UINT64_C(14695981039346656037);
            for (const char* p = text; safe && *p; ++p) {
                safe = (*p >= 'a' && *p <= 'z') || (*p >= 'A' && *p <= 'Z') ||
                       (*p >= '0' && *p <= '9') || *p == '.' || *p == '_' || *p == '$';
            }
            unsigned components = 1;
            bool identifier = std::strlen(text) > 0 && std::strlen(text) <= 200;
            bool component_start = true;
            for (const char* p = text; identifier && *p; ++p) {
                bool letter =
                    (*p >= 'a' && *p <= 'z') || (*p >= 'A' && *p <= 'Z') || *p == '_' || *p == '$';
                if (*p == '.') {
                    identifier = !component_start && p[1] != '\0';
                    component_start = true;
                    ++components;
                } else {
                    identifier = letter || (!component_start && *p >= '0' && *p <= '9');
                    component_start = false;
                }
            }
            identifier = identifier && components >= 3;
            for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p; ++p) {
                fingerprint = (fingerprint ^ *p) * UINT64_C(1099511628211);
            }
            std::fprintf(stderr,
                         "SHIRAZ_PROBE_CLASS_NOT_FOUND name=%s fingerprint=%016llx length=%zu "
                         "identifier=%d\n",
                         safe ? text : "<redacted>", static_cast<unsigned long long>(fingerprint),
                         std::strlen(text), identifier);
            env->ReleaseStringUTFChars(name, text);
        }
    }
    if (env->ExceptionCheck())
        env->ExceptionClear();
    if (missing)
        env->DeleteLocalRef(missing);
    env->Throw(thrown);
    env->DeleteLocalRef(thrown);
    return result;
}
} // namespace

extern "C" void shiraz_probe_observe_class_lookup(JavaVM* vm) noexcept {
    if (!std::getenv("SHIRAZ_PROBE_CLASS_LOOKUP"))
        return;
    if (installed.test_and_set())
        return;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
        return;
    jclass cls = env->FindClass("java/lang/Class");
    jmethodID method = cls ? env->GetStaticMethodID(
                                 cls, "classForName",
                                 "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")
                           : nullptr;
    if (!method || env->ExceptionCheck()) {
        if (env->ExceptionCheck())
            env->ExceptionClear();
        std::fprintf(stderr, "SHIRAZ_PROBE_CLASS_LOOKUP_UNAVAILABLE method\n");
        return;
    }
    // Android 9 standalone ART ArtMethod: data_ is the first pointer-sized field
    // at offset 24. Check its owner before invoking or registering it.
    void* entry = nullptr;
    std::memcpy(&entry, reinterpret_cast<const char*>(method) + 24, sizeof(entry));
    Dl_info info{};
    if (!entry || !dladdr(entry, &info) || !info.dli_fname ||
        !std::strstr(info.dli_fname, "/libart.so")) {
        std::fprintf(stderr, "SHIRAZ_PROBE_CLASS_LOOKUP_UNAVAILABLE layout\n");
        env->DeleteLocalRef(cls);
        return;
    }
    original = reinterpret_cast<Lookup>(entry);
    JNINativeMethod registration = {
        const_cast<char*>("classForName"),
        const_cast<char*>("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"),
        reinterpret_cast<void*>(observe)};
    if (env->RegisterNatives(cls, &registration, 1) != JNI_OK) {
        if (env->ExceptionCheck())
            env->ExceptionClear();
        std::fprintf(stderr, "SHIRAZ_PROBE_CLASS_LOOKUP_UNAVAILABLE registration\n");
        env->DeleteLocalRef(cls);
        return;
    }
    // Exercise success and failure with known class names before app execution.
    jstring present = env->NewStringUTF("java.lang.String");
    jobject found = env->CallStaticObjectMethod(cls, method, present, JNI_FALSE, nullptr);
    bool success = found && !env->ExceptionCheck();
    if (env->ExceptionCheck())
        env->ExceptionClear();
    jstring absent = env->NewStringUTF("java.lang.ShirazProbeMissingClass");
    env->CallStaticObjectMethod(cls, method, absent, JNI_FALSE, nullptr);
    jthrowable thrown = env->ExceptionOccurred();
    env->ExceptionClear();
    jclass missing = env->FindClass("java/lang/ClassNotFoundException");
    bool failure = thrown && missing && env->IsInstanceOf(thrown, missing);
    if (env->ExceptionCheck())
        env->ExceptionClear();
    if (found)
        env->DeleteLocalRef(found);
    if (present)
        env->DeleteLocalRef(present);
    if (absent)
        env->DeleteLocalRef(absent);
    if (thrown)
        env->DeleteLocalRef(thrown);
    if (missing)
        env->DeleteLocalRef(missing);
    std::fprintf(stderr, "SHIRAZ_PROBE_CLASS_LOOKUP_FIXTURE success=%d missing=%d\n", success,
                 failure);
    if (!success || !failure)
        std::abort();
    env->DeleteLocalRef(cls);
    if (std::getenv("SHIRAZ_PROBE_OBSERVE_GID")) {
        jclass process = env->FindClass("android/os/Process");
        jmethodID gid =
            process ? env->GetStaticMethodID(process, "getGidForName", "(Ljava/lang/String;)I")
                    : nullptr;
        jstring empty = env->NewStringUTF("");
        jint before = gid && empty ? env->CallStaticIntMethod(process, gid, empty) : 0;
        if (!gid || !empty || env->ExceptionCheck() || before != -1)
            std::abort();
        void* gid_entry = nullptr;
        std::memcpy(&gid_entry, reinterpret_cast<const char*>(gid) + 24, sizeof(gid_entry));
        Dl_info gid_info{};
        if (!gid_entry || !dladdr(gid_entry, &gid_info) || !gid_info.dli_fname ||
            !std::strstr(gid_info.dli_fname, "/libshiraz-host.so"))
            std::abort();
        original_gid = reinterpret_cast<GroupId>(gid_entry);
        JNINativeMethod registration = {const_cast<char*>("getGidForName"),
                                        const_cast<char*>("(Ljava/lang/String;)I"),
                                        reinterpret_cast<void*>(observe_gid)};
        if (env->RegisterNatives(process, &registration, 1) != JNI_OK)
            std::abort();
        jint after = env->CallStaticIntMethod(process, gid, empty);
        if (env->ExceptionCheck() || after != before)
            std::abort();
        std::fprintf(stderr, "SHIRAZ_PROBE_GID_FIXTURE pass=1\n");
        env->DeleteLocalRef(empty);
        env->DeleteLocalRef(process);
    }
    if (std::getenv("SHIRAZ_PROBE_VERIFY_TID")) {
        jclass process = env->FindClass("android/os/Process");
        jmethodID tid = process ? env->GetStaticMethodID(process, "myTid", "()I") : nullptr;
        jint java_tid = tid ? env->CallStaticIntMethod(process, tid) : -1;
        int guest_tid = 0;
        bool same = !env->ExceptionCheck() && !shiraz_android_tid_current(&guest_tid) &&
                    java_tid > 0 && java_tid == guest_tid && java_tid < (1 << 22);
        std::fprintf(stderr, "SHIRAZ_PROBE_SHARED_TID same=%d\n", same);
        if (!same)
            std::abort();
        env->DeleteLocalRef(process);
    }
    if (std::getenv("SHIRAZ_PROBE_SMALL_TID")) {
        jclass process = env->FindClass("android/os/Process");
        jmethodID tid = process ? env->GetStaticMethodID(process, "myTid", "()I") : nullptr;
        jint before = tid ? env->CallStaticIntMethod(process, tid) : -1;
        if (!tid || env->ExceptionCheck()) {
            if (env->ExceptionCheck())
                env->ExceptionClear();
            std::fprintf(stderr, "SHIRAZ_PROBE_SMALL_TID_UNAVAILABLE lookup\n");
            std::abort();
        }
        void* tid_entry = nullptr;
        std::memcpy(&tid_entry, reinterpret_cast<const char*>(tid) + 24, sizeof(tid_entry));
        Dl_info tid_info{};
        if (!tid_entry || !dladdr(tid_entry, &tid_info) || !tid_info.dli_fname ||
            !std::strstr(tid_info.dli_fname, "/libshiraz-host.so")) {
            std::fprintf(stderr, "SHIRAZ_PROBE_SMALL_TID_UNAVAILABLE layout\n");
            std::abort();
        }
        original_tid = reinterpret_cast<ThreadId>(tid_entry);
        JNINativeMethod tid_registration = {const_cast<char*>("myTid"), const_cast<char*>("()I"),
                                            reinterpret_cast<void*>(probe_small_tid)};
        if (env->RegisterNatives(process, &tid_registration, 1) != JNI_OK)
            std::abort();
        jint after = env->CallStaticIntMethod(process, tid);
        if (env->ExceptionCheck() || before <= 0 || after <= 0 || after >= (1 << 22))
            std::abort();
        std::fprintf(stderr, "SHIRAZ_PROBE_SMALL_TID_FIXTURE actual_shift=%d mapped_shift=%d\n",
                     before >> 22, after >> 22);
        env->DeleteLocalRef(process);
    }
}
