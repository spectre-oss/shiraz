// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/thread_identity.hpp"
#include <jni.h>
#include <pthread.h>

extern "C" JNIEXPORT jint JNICALL Java_ThreadIdentitySmoke_nativeTid(JNIEnv*, jclass) {
    int value = 0;
    return shiraz_android_tid_current(&value) ? -1 : value;
}

extern "C" JNIEXPORT jboolean JNICALL Java_ThreadIdentitySmoke_nativeIsDarwinMain(JNIEnv*, jclass) {
    return pthread_main_np() ? JNI_TRUE : JNI_FALSE;
}
