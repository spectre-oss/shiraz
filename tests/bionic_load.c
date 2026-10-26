// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
static int initialized;
__attribute__((constructor)) static void initialize(void) { initialized = 73; }
JNIEXPORT jint JNICALL Java_BionicLoadSmoke_value(JNIEnv *env, jclass type) {
    (void)env;
    (void)type;
    return initialized;
}
