// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/hardware_buffer.h"
struct AImage;
struct AImageReader;
struct AImageReader_ImageListener { void* context; void (*onImageAvailable)(void*, AImageReader*); };
extern "C" {
int AImageReader_new(int32_t,int32_t,int32_t,int32_t,AImageReader**);
int AImageReader_newWithUsage(int32_t,int32_t,int32_t,uint64_t,int32_t,AImageReader**);
void AImageReader_delete(AImageReader*);
int AImageReader_getWindow(AImageReader*,ANativeWindow**);
int AImageReader_setImageListener(AImageReader*,AImageReader_ImageListener*);
int AImageReader_acquireNextImage(AImageReader*,AImage**);
int AImageReader_acquireNextImageAsync(AImageReader*,AImage**,int*);
int AImageReader_acquireLatestImage(AImageReader*,AImage**);
int AImageReader_acquireLatestImageAsync(AImageReader*,AImage**,int*);
void AImage_delete(AImage*);
void AImage_deleteAsync(AImage*,int);
int AImage_getHardwareBuffer(const AImage*,AHardwareBuffer**);
int AImage_getWidth(const AImage*,int32_t*);
int AImage_getHeight(const AImage*,int32_t*);
int AImage_getFormat(const AImage*,int32_t*);
int AImage_getTimestamp(const AImage*,int64_t*);
}
extern "C" {
int AImageReader_getWidth(AImageReader*,int32_t*);
int AImageReader_getHeight(AImageReader*,int32_t*);
int AImageReader_getFormat(AImageReader*,int32_t*);
int AImageReader_getMaxImages(AImageReader*,int32_t*);
int AImage_getNumberOfPlanes(const AImage*,int32_t*);
int AImage_getPlanePixelStride(const AImage*,int,int32_t*);
int AImage_getPlaneRowStride(const AImage*,int,int32_t*);
int AImage_getPlaneData(const AImage*,int,uint8_t**,int*);
int AImage_getCropRect(const AImage*,ARect*);
}
