// SPDX-License-Identifier: GPL-3.0-only
// Android NDK bitmap ABI; implemented for Shiraz software Bitmap storage.
#pragma once
#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
struct AHardwareBuffer;
typedef struct {
    uint32_t width, height, stride;
    int32_t format;
    uint32_t flags;
} AndroidBitmapInfo;
typedef bool (*AndroidBitmap_CompressWriteFunc)(void* userContext, const void* data, size_t size);
enum {
    ANDROID_BITMAP_COMPRESS_FORMAT_JPEG = 0,
    ANDROID_BITMAP_COMPRESS_FORMAT_PNG = 1,
    ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSY = 3,
    ANDROID_BITMAP_COMPRESS_FORMAT_WEBP_LOSSLESS = 4,
};
int AndroidBitmap_getInfo(JNIEnv*, jobject, AndroidBitmapInfo*);
int AndroidBitmap_lockPixels(JNIEnv*, jobject, void**);
int AndroidBitmap_unlockPixels(JNIEnv*, jobject);
int32_t AndroidBitmap_getDataSpace(JNIEnv*, jobject);
int AndroidBitmap_compress(const AndroidBitmapInfo* info, int32_t dataspace, const void* pixels,
                           int32_t format, int32_t quality, void* userContext,
                           AndroidBitmap_CompressWriteFunc fn);
int AndroidBitmap_getHardwareBuffer(JNIEnv*, jobject, struct AHardwareBuffer** outBuffer);
#ifdef __cplusplus
}
#endif
