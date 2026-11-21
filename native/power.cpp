// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#if defined(__APPLE__)
#include <IOKit/pwr_mgt/IOPMLib.h>
#include <CoreFoundation/CoreFoundation.h>
#endif
#if defined(__linux__)
#include "shiraz/logind_power.hpp"
#endif
#include <cstdint>
#include <limits>
#include <mutex>
#include <new>
#include <unordered_map>

namespace {
void fail(JNIEnv* env, const char* type, const char* message) {
    env->ThrowNew(env->FindClass(type), message);
}
#if defined(__APPLE__)
std::mutex mutex;
std::unordered_map<jlong, IOPMAssertionID> assertions;
#elif defined(__linux__)
std::mutex mutex;
std::unordered_map<jlong, int> assertions;
#endif
#if defined(__APPLE__) || defined(__linux__)
jlong sequence = 0;
#endif
} // namespace
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativePower_acquire(JNIEnv* env, jclass) {
#if defined(__APPLE__)
    std::lock_guard<std::mutex> guard(mutex);
    if (assertions.size() >= 256 || sequence == std::numeric_limits<jlong>::max()) {
        fail(env, "java/lang/IllegalStateException", "Host power assertion limit");
        return 0;
    }
    IOPMAssertionID assertion = 0;
    if (IOPMAssertionCreateWithName(kIOPMAssertionTypePreventUserIdleSystemSleep,
                                    kIOPMAssertionLevelOn, CFSTR("Shiraz background work"),
                                    &assertion) != kIOReturnSuccess) {
        fail(env, "java/lang/IllegalStateException", "Cannot acquire host idle-sleep assertion");
        return 0;
    }
    const jlong handle = ++sequence;
    try {
        assertions.emplace(handle, assertion);
    } catch (const std::bad_alloc&) {
        IOPMAssertionRelease(assertion);
        fail(env, "java/lang/OutOfMemoryError", "Cannot retain host power assertion");
        return 0;
    }
    return handle;
#elif defined(__linux__)
    std::lock_guard<std::mutex> guard(mutex);
    if (assertions.size() >= 256 || sequence == std::numeric_limits<jlong>::max()) {
        fail(env, "java/lang/IllegalStateException", "Host power assertion limit");
        return 0;
    }
    int descriptor = -1;
    try {
        shiraz::LogindPower backend;
        descriptor = backend.acquire();
        const jlong handle = ++sequence;
        assertions.emplace(handle, descriptor);
        return handle;
    } catch (const std::bad_alloc&) {
        if (descriptor >= 0)
            close(descriptor);
        fail(env, "java/lang/OutOfMemoryError", "Cannot retain host power assertion");
        return 0;
    } catch (const std::exception& error) {
        if (descriptor >= 0)
            close(descriptor);
        fail(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
#else
    fail(env, "java/lang/UnsupportedOperationException",
         "Host idle-sleep inhibition backend unavailable");
    return 0;
#endif
}
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativePower_active(JNIEnv* env, jclass,
                                                                             jlong handle) {
#if defined(__APPLE__)
    std::lock_guard<std::mutex> guard(mutex);
    auto found = assertions.find(handle);
    if (found == assertions.end()) {
        fail(env, "java/lang/IllegalArgumentException", "Unknown power assertion handle");
        return JNI_FALSE;
    }
    CFDictionaryRef properties = IOPMAssertionCopyProperties(found->second);
    if (!properties) {
        fail(env, "java/lang/IllegalStateException", "Host power assertion disappeared");
        return JNI_FALSE;
    }
    CFTypeRef value = CFDictionaryGetValue(properties, kIOPMAssertionLevelKey);
    int level = 0;
    bool enabled = value && CFGetTypeID(value) == CFNumberGetTypeID() &&
                   CFNumberGetValue(static_cast<CFNumberRef>(value), kCFNumberIntType, &level) &&
                   level == kIOPMAssertionLevelOn;
    CFRelease(properties);
    return enabled ? JNI_TRUE : JNI_FALSE;
#elif defined(__linux__)
    std::lock_guard<std::mutex> guard(mutex);
    auto found = assertions.find(handle);
    if (found == assertions.end()) {
        fail(env, "java/lang/IllegalArgumentException", "Unknown power assertion handle");
        return JNI_FALSE;
    }
    try {
        return shiraz::LogindPower::active(found->second) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        fail(env, "java/lang/IllegalStateException", error.what());
        return JNI_FALSE;
    }
#else
    (void)handle;
    fail(env, "java/lang/UnsupportedOperationException",
         "Host idle-sleep inhibition backend unavailable");
    return JNI_FALSE;
#endif
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativePower_release(JNIEnv* env, jclass,
                                                                          jlong handle) {
#if defined(__APPLE__)
    std::lock_guard<std::mutex> guard(mutex);
    auto found = assertions.find(handle);
    if (found == assertions.end()) {
        fail(env, "java/lang/IllegalArgumentException", "Unknown power assertion handle");
        return;
    }
    if (IOPMAssertionRelease(found->second) != kIOReturnSuccess) {
        fail(env, "java/lang/IllegalStateException", "Cannot release host power assertion");
        return;
    }
    assertions.erase(found);
#elif defined(__linux__)
    std::lock_guard<std::mutex> guard(mutex);
    auto found = assertions.find(handle);
    if (found == assertions.end()) {
        fail(env, "java/lang/IllegalArgumentException", "Unknown power assertion handle");
        return;
    }
    // Linux consumes the descriptor even if close reports EINTR: never retry it.
    close(found->second);
    assertions.erase(found);
#else
    (void)handle;
    fail(env, "java/lang/UnsupportedOperationException",
         "Host idle-sleep inhibition backend unavailable");
#endif
}
