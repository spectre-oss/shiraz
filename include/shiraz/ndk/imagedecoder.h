// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/native_window.h"
#include <cstddef>
#include <cstdint>

struct AAsset;
struct AImageDecoder;
struct AImageDecoderHeaderInfo;
struct AImageDecoderFrameInfo;

enum {
    ANDROID_IMAGE_DECODER_SUCCESS = 0,
    ANDROID_IMAGE_DECODER_INCOMPLETE = -1,
    ANDROID_IMAGE_DECODER_ERROR = -2,
    ANDROID_IMAGE_DECODER_INVALID_CONVERSION = -3,
    ANDROID_IMAGE_DECODER_INVALID_SCALE = -4,
    ANDROID_IMAGE_DECODER_BAD_PARAMETER = -5,
    ANDROID_IMAGE_DECODER_INVALID_INPUT = -6,
    ANDROID_IMAGE_DECODER_SEEK_ERROR = -7,
    ANDROID_IMAGE_DECODER_INTERNAL_ERROR = -8,
    ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT = -9,
    ANDROID_IMAGE_DECODER_FINISHED = -10,
    ANDROID_IMAGE_DECODER_INVALID_STATE = -11,
};

extern "C" {
const char* AImageDecoder_resultToString(int result);
int AImageDecoder_createFromAAsset(AAsset* asset, AImageDecoder** outDecoder);
int AImageDecoder_createFromFd(int fd, AImageDecoder** outDecoder);
int AImageDecoder_createFromBuffer(const void* buffer, size_t length, AImageDecoder** outDecoder);
void AImageDecoder_delete(AImageDecoder* decoder);

const AImageDecoderHeaderInfo* AImageDecoder_getHeaderInfo(const AImageDecoder* decoder);
int32_t AImageDecoderHeaderInfo_getWidth(const AImageDecoderHeaderInfo* info);
int32_t AImageDecoderHeaderInfo_getHeight(const AImageDecoderHeaderInfo* info);
const char* AImageDecoderHeaderInfo_getMimeType(const AImageDecoderHeaderInfo* info);
int32_t AImageDecoderHeaderInfo_getAndroidBitmapFormat(const AImageDecoderHeaderInfo* info);
int32_t AImageDecoderHeaderInfo_getAlphaFlags(const AImageDecoderHeaderInfo* info);
int32_t AImageDecoderHeaderInfo_getDataSpace(const AImageDecoderHeaderInfo* info);

int AImageDecoder_setAndroidBitmapFormat(AImageDecoder* decoder, int32_t format);
int AImageDecoder_setUnpremultipliedRequired(AImageDecoder* decoder, bool required);
int AImageDecoder_setDataSpace(AImageDecoder* decoder, int32_t dataspace);
int AImageDecoder_setTargetSize(AImageDecoder* decoder, int32_t width, int32_t height);
int AImageDecoder_computeSampledSize(const AImageDecoder* decoder, int sampleSize, int32_t* width,
                                     int32_t* height);
int AImageDecoder_setCrop(AImageDecoder* decoder, ARect crop);
size_t AImageDecoder_getMinimumStride(AImageDecoder* decoder);
int AImageDecoder_decodeImage(AImageDecoder* decoder, void* pixels, size_t stride, size_t size);

bool AImageDecoder_isAnimated(AImageDecoder* decoder);
int32_t AImageDecoder_getRepeatCount(AImageDecoder* decoder);
int AImageDecoder_advanceFrame(AImageDecoder* decoder);
int AImageDecoder_rewind(AImageDecoder* decoder);
int AImageDecoder_getFrameInfo(AImageDecoder* decoder, AImageDecoderFrameInfo* info);
void AImageDecoder_setInternallyHandleDisposePrevious(AImageDecoder* decoder, bool handle);

AImageDecoderFrameInfo* AImageDecoderFrameInfo_create();
void AImageDecoderFrameInfo_delete(AImageDecoderFrameInfo* info);
int64_t AImageDecoderFrameInfo_getDuration(const AImageDecoderFrameInfo* info);
ARect AImageDecoderFrameInfo_getFrameRect(const AImageDecoderFrameInfo* info);
bool AImageDecoderFrameInfo_hasAlphaWithinBounds(const AImageDecoderFrameInfo* info);
int32_t AImageDecoderFrameInfo_getDisposeOp(const AImageDecoderFrameInfo* info);
int32_t AImageDecoderFrameInfo_getBlendOp(const AImageDecoderFrameInfo* info);
}
