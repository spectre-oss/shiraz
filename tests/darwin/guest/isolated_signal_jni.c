// SPDX-License-Identifier: GPL-3.0-only
#include <errno.h>
#include <jni.h>
#include <signal.h>

static void callback(int signal_number) {
    (void)signal_number;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env = 0;
    return (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK && env ? JNI_VERSION_1_6
                                                                             : JNI_ERR;
}

JNIEXPORT jint Java_IsolatedNativeLoad_probe(JNIEnv* env, jclass klass) {
    (void)env;
    (void)klass;
    errno = 0;
    return signal(SIGUSR1, callback) == SIG_ERR && errno == ENOTSUP ? 73 : -1;
}
