// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
JNIEXPORT jint Java_GuestJniFixture_hostCheck(JNIEnv *env, jclass klass) {
  (void)klass;
  return (*env)->GetVersion(env) >= JNI_VERSION_1_6 ? 19 : -1;
}
