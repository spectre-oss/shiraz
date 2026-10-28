// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"

namespace {
jobject createDescriptor(JNIEnv* env) { return jniCreateFileDescriptor(env, -1); }
int getDescriptor(JNIEnv* env, jobject object) { return jniGetFDFromFileDescriptor(env, object); }
void setDescriptor(JNIEnv* env, jobject object, int fd) {
  if (!object) {
    jclass type = env->FindClass("java/lang/NullPointerException");
    if (type) { env->ThrowNew(type, "fileDescriptor"); env->DeleteLocalRef(type); }
    return;
  }
  jniSetFileDescriptorOfFD(env, object, fd);
}
}
namespace berberis {
static const KnownTrampoline functions[] = {
  {"AFileDescriptor_create", GetTrampolineFunc<decltype(createDescriptor)>(), reinterpret_cast<void*>(createDescriptor)},
  {"AFileDescriptor_getFd", GetTrampolineFunc<decltype(getDescriptor)>(), reinterpret_cast<void*>(getDescriptor)},
  {"AFileDescriptor_setFd", GetTrampolineFunc<decltype(setDescriptor)>(), reinterpret_cast<void*>(setDescriptor)},
};
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
  builder->Build("libnativehelper.so", sizeof(functions)/sizeof(functions[0]), functions, 0, nullptr);
}
}
