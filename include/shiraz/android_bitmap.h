// SPDX-License-Identifier: GPL-3.0-only
// Android NDK bitmap ABI; implemented for Shiraz software Bitmap storage.
#pragma once
#include <jni.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {
    uint32_t width, height, stride;
    int32_t format;
    uint32_t flags;
} AndroidBitmapInfo;
int AndroidBitmap_getInfo(JNIEnv*, jobject, AndroidBitmapInfo*);
int AndroidBitmap_lockPixels(JNIEnv*, jobject, void**);
int AndroidBitmap_unlockPixels(JNIEnv*, jobject);
#ifdef __cplusplus
}
#endif
