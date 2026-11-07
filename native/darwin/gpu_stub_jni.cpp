// SPDX-License-Identifier: GPL-3.0-only
// macOS JNI side of shiraz.runtime.NativeGpu until Skia (Ganesh-Metal and CPU
// path ops) is built for darwin (docs/macos_final_goal.md, M4). available()
// reports false so Canvas uses the Java software raster. Every other entry
// throws UnsupportedOperationException: none returns a silent null or 0, so a
// caller such as Path.op cannot mistake the missing backend for a result.
#include <jni.h>

namespace {
void unsupported(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/UnsupportedOperationException");
    if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}
constexpr const char *kCanvas = "Skia GPU canvas pending (M4)";
constexpr const char *kPathOps = "Skia path ops pending (M4)";
} // namespace

#define GPU(ret, name) extern "C" JNIEXPORT ret JNICALL Java_shiraz_runtime_NativeGpu_##name

GPU(jboolean, available)(JNIEnv *, jclass) { return JNI_FALSE; }
GPU(jfloatArray, pathOp)(JNIEnv *env, jclass, jfloatArray, jint, jfloatArray, jint, jint) { unsupported(env, kPathOps); return nullptr; }
GPU(jboolean, valid)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); return JNI_FALSE; }
GPU(jlong, create)(JNIEnv *env, jclass, jint, jint, jintArray) { unsupported(env, kCanvas); return 0; }
GPU(void, release)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); }
GPU(void, read)(JNIEnv *env, jclass, jlong, jintArray) { unsupported(env, kCanvas); }
GPU(void, clear)(JNIEnv *env, jclass, jlong, jint) { unsupported(env, kCanvas); }
GPU(void, copyInto)(JNIEnv *env, jclass, jlong, jlong) { unsupported(env, kCanvas); }
GPU(void, post)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); }
GPU(void, begin)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); }
GPU(void, save)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); }
GPU(void, restore)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); }
GPU(void, matrix)(JNIEnv *env, jclass, jlong, jfloatArray) { unsupported(env, kCanvas); }
GPU(void, paint)(JNIEnv *env, jclass, jlong, jfloatArray, jintArray, jfloatArray, jfloatArray, jlong,
                 jfloatArray, jintArray, jfloatArray, jfloatArray, jfloatArray) { unsupported(env, kCanvas); }
GPU(void, layer)(JNIEnv *env, jclass, jlong, jfloatArray) { unsupported(env, kCanvas); }
GPU(void, shape)(JNIEnv *env, jclass, jlong, jint, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat) { unsupported(env, kCanvas); }
GPU(jboolean, clipRect)(JNIEnv *env, jclass, jlong, jfloat, jfloat, jfloat, jfloat, jboolean) { unsupported(env, kCanvas); return JNI_FALSE; }
GPU(void, path)(JNIEnv *env, jclass, jlong, jfloatArray, jint) { unsupported(env, kCanvas); }
GPU(void, clip)(JNIEnv *env, jclass, jlong, jfloatArray, jint, jboolean) { unsupported(env, kCanvas); }
GPU(jintArray, clipBounds)(JNIEnv *env, jclass, jlong) { unsupported(env, kCanvas); return nullptr; }
GPU(void, image)(JNIEnv *env, jclass, jlong, jlong, jfloatArray, jfloatArray, jboolean) { unsupported(env, kCanvas); }
GPU(void, shadow)(JNIEnv *env, jclass, jlong, jfloatArray, jfloat, jfloat) { unsupported(env, kCanvas); }
GPU(void, ripple)(JNIEnv *env, jclass, jlong, jfloatArray, jint, jint) { unsupported(env, kCanvas); }
GPU(void, color)(JNIEnv *env, jclass, jlong, jint) { unsupported(env, kCanvas); }
