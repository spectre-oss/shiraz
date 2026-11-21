// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <nativebridge/native_bridge.h>
#include <memory>
#include <string>
namespace shiraz::guest {
class AdmissionDomain;
bool is_guest_code(const void* pointer);
void initialize_jni(const android::NativeBridgeRuntimeCallbacks* callbacks);
JNIEnv* shadow_env(JNIEnv* host);
JNIEnv* host_env(JNIEnv* guest);
JavaVM* shadow_vm(JavaVM* host);
void* native_trampoline(void* guest, const std::string& shorty,
                        std::shared_ptr<AdmissionDomain> domain = {});
void* onload_trampoline(void* guest, bool unload, const std::string& module = {},
                        std::shared_ptr<AdmissionDomain> domain = {});
} // namespace shiraz::guest
