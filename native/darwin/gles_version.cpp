// SPDX-License-Identifier: GPL-3.0-only
// Darwin replacement for native/gles_version.cpp. macOS has no EGL/GLES until
// the ANGLE-Metal backend lands (docs/macos_final_goal.md, M4), so this reports
// no OpenGL ES support: ConfigurationInfo.reqGlEsVersion stays 0. It must never
// advertise a version the process cannot create a context for.
#include <jni.h>

extern "C" JNIEXPORT jint JNICALL Java_android_app_ActivityManager_nativeGlesVersion(JNIEnv*, jclass) {
    return 0;
}
