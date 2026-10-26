// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/android_bitmap.h"
#include <cstdint>
#include <stdexcept>
static void require(bool valid) { if (!valid) throw std::runtime_error("NDK bitmap contract failed"); }
extern "C" JNIEXPORT void JNICALL Java_AndroidBitmapSmoke_exercise(JNIEnv* env, jclass, jobject bitmap, jint mode) {
    try {
        AndroidBitmapInfo info{};
        require(AndroidBitmap_getInfo(env, bitmap, &info) == 0);
        require(info.width == 2 && info.height == 1 && info.stride == (mode == 1 ? 2u : 8u));
        require(info.format == (mode == 1 ? 8 : 1));
        void* first = nullptr; void* second = nullptr;
        require(AndroidBitmap_lockPixels(env, bitmap, &first) == 0 && first);
        require(AndroidBitmap_lockPixels(env, bitmap, &second) == 0 && second == first);
        auto* p = static_cast<unsigned char*>(first);
        if (mode == 1) {
            require(p[0] == 128 && p[1] == 255); p[0] = 17; p[1] = 0;
        } else {
            require(p[0] == 128 && p[1] == 32 && p[2] == 0 && p[3] == 128);
            require(p[4] == 17 && p[5] == 34 && p[6] == 51 && p[7] == 255);
            if (mode != 2) { p[4] = 0; p[5] = 128; p[6] = 0; p[7] = 128; }
        }
        require(AndroidBitmap_unlockPixels(env, bitmap) == 0);
        // A nested unlock must retain the pointer and postpone writeback.
        require(p[0] == (mode == 1 ? 17 : 128));
        if (mode == 3) {
            auto cls = env->GetObjectClass(bitmap);
            auto recycle = env->GetMethodID(cls, "recycle", "()V");
            env->CallVoidMethod(bitmap, recycle); env->DeleteLocalRef(cls);
            require(!env->ExceptionCheck());
        }
        require(AndroidBitmap_unlockPixels(env, bitmap) == 0);
        require(AndroidBitmap_unlockPixels(env, bitmap) == -1);
        require(AndroidBitmap_getInfo(env, nullptr, &info) == -1);
        require(AndroidBitmap_getInfo(env, bitmap, nullptr) == -1);
        if (mode == 3) {
            require(AndroidBitmap_getInfo(env, bitmap, &info) == -1);
            require(AndroidBitmap_lockPixels(env, bitmap, &first) == -1);
        }
    } catch (const std::exception& failure) {
        if (!env->ExceptionCheck()) {
            auto cls = env->FindClass("java/lang/AssertionError"); env->ThrowNew(cls, failure.what());
        }
    }
}
extern "C" JNIEXPORT void JNICALL Java_AndroidBitmapSmoke_capacity(JNIEnv* env, jclass, jobjectArray bitmaps) {
    bool valid = env->GetArrayLength(bitmaps) == 65;
    for (int i = 0; valid && i < 65; ++i) {
        auto bitmap = env->GetObjectArrayElement(bitmaps, i); void* pointer = nullptr;
        valid = AndroidBitmap_lockPixels(env, bitmap, &pointer) == (i == 64 ? -3 : 0);
        env->DeleteLocalRef(bitmap);
    }
    // Always release successful locks, including on test failure.
    for (int i = 0; i < 65; ++i) {
        auto bitmap = env->GetObjectArrayElement(bitmaps, i);
        int result = AndroidBitmap_unlockPixels(env, bitmap);
        if (i < 64 && result != 0) valid = false;
        env->DeleteLocalRef(bitmap);
    }
    auto bitmap = env->GetObjectArrayElement(bitmaps, 64); void* pointer = nullptr;
    valid = (AndroidBitmap_lockPixels(env, bitmap, &pointer) == 0) && valid;
    valid = (AndroidBitmap_unlockPixels(env, bitmap) == 0) && valid;
    env->DeleteLocalRef(bitmap);
    if (!valid && !env->ExceptionCheck()) {
        auto cls = env->FindClass("java/lang/AssertionError"); env->ThrowNew(cls, "NDK lock capacity/recovery failed");
    }
}
