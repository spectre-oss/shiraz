// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

struct AMediaFormat;
typedef struct AMediaFormat AMediaFormat;

struct AMediaCodec;
typedef struct AMediaCodec AMediaCodec;

struct ANativeWindow;
typedef struct ANativeWindow ANativeWindow;

struct AMediaCrypto;
typedef struct AMediaCrypto AMediaCrypto;

struct AMediaCodecCryptoInfo;
typedef struct AMediaCodecCryptoInfo AMediaCodecCryptoInfo;

struct AMediaCodecBufferInfo;
typedef struct AMediaCodecBufferInfo AMediaCodecBufferInfo;

typedef int32_t media_status_t;

enum {
    AMEDIA_OK = 0,
    AMEDIA_ERROR_BASE = -10000,
    AMEDIA_ERROR_UNKNOWN = AMEDIA_ERROR_BASE,
    AMEDIA_ERROR_MALFORMED = AMEDIA_ERROR_BASE - 1,
    AMEDIA_ERROR_UNSUPPORTED = AMEDIA_ERROR_BASE - 2,
    AMEDIA_ERROR_INVALID_OBJECT = AMEDIA_ERROR_BASE - 3,
    AMEDIA_ERROR_INVALID_PARAMETER = AMEDIA_ERROR_BASE - 4,
    AMEDIA_ERROR_INVALID_OPERATION = AMEDIA_ERROR_BASE - 5,
    AMEDIA_ERROR_END_OF_STREAM = AMEDIA_ERROR_BASE - 6,
    AMEDIA_ERROR_IO = AMEDIA_ERROR_BASE - 7,
    AMEDIA_ERROR_WOULD_BLOCK = AMEDIA_ERROR_BASE - 8,
};

enum {
    AMEDIAEXTRACTOR_SAMPLE_FLAG_SYNC = 1,
    AMEDIAEXTRACTOR_SAMPLE_FLAG_ENCRYPTED = 2,
};

typedef enum {
    AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC = 0,
    AMEDIAEXTRACTOR_SEEK_NEXT_SYNC = 1,
    AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC = 2,
} SeekMode;

typedef enum {
    AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4 = 0,
    AMEDIAMUXER_OUTPUT_FORMAT_WEBM = 1,
    AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP = 2,
    AMEDIAMUXER_OUTPUT_FORMAT_HEIF = 3,
    AMEDIAMUXER_OUTPUT_FORMAT_OGG = 4,
} OutputFormat;

typedef struct AMediaDataSource AMediaDataSource;
typedef struct AMediaExtractor AMediaExtractor;
typedef struct AMediaMuxer AMediaMuxer;

typedef ssize_t (*AMediaDataSourceReadAt)(void* userdata, int64_t offset, void* buffer,
                                          size_t size);
typedef ssize_t (*AMediaDataSourceGetSize)(void* userdata);
typedef void (*AMediaDataSourceClose)(void* userdata);
typedef ssize_t (*AMediaDataSourceGetAvailableSize)(void* userdata, int64_t offset);

typedef void (*AMediaCodecOnAsyncInputAvailable)(AMediaCodec* codec, void* userdata, int32_t index);
typedef void (*AMediaCodecOnAsyncOutputAvailable)(AMediaCodec* codec, void* userdata, int32_t index,
                                                  AMediaCodecBufferInfo* bufferInfo);
typedef void (*AMediaCodecOnAsyncFormatChanged)(AMediaCodec* codec, void* userdata,
                                                AMediaFormat* format);
typedef void (*AMediaCodecOnAsyncError)(AMediaCodec* codec, void* userdata, media_status_t error,
                                        int32_t actionCode, const char* detail);

typedef struct AMediaCodecOnAsyncNotifyCallback {
    AMediaCodecOnAsyncInputAvailable onAsyncInputAvailable;
    AMediaCodecOnAsyncOutputAvailable onAsyncOutputAvailable;
    AMediaCodecOnAsyncFormatChanged onAsyncFormatChanged;
    AMediaCodecOnAsyncError onAsyncError;
} AMediaCodecOnAsyncNotifyCallback;

// AMediaFormat additions
bool AMediaFormat_getDouble(AMediaFormat* format, const char* name, double* out);
bool AMediaFormat_getSize(AMediaFormat* format, const char* name, size_t* out);
bool AMediaFormat_getRect(AMediaFormat* format, const char* name, int32_t* left, int32_t* top,
                          int32_t* right, int32_t* bottom);
void AMediaFormat_setDouble(AMediaFormat* format, const char* name, double value);
void AMediaFormat_setSize(AMediaFormat* format, const char* name, size_t value);
void AMediaFormat_setRect(AMediaFormat* format, const char* name, int32_t left, int32_t top,
                          int32_t right, int32_t bottom);
media_status_t AMediaFormat_copy(AMediaFormat* to, AMediaFormat* from);
void AMediaFormat_clear(AMediaFormat* format);

// AMediaCodec additions
AMediaFormat* AMediaCodec_getInputFormat(AMediaCodec* codec);
media_status_t AMediaCodec_setOutputSurface(AMediaCodec* codec, ANativeWindow* surface);
media_status_t AMediaCodec_createInputSurface(AMediaCodec* codec, ANativeWindow** surface);
media_status_t AMediaCodec_createPersistentInputSurface(ANativeWindow** surface);
media_status_t AMediaCodec_setInputSurface(AMediaCodec* codec, ANativeWindow* surface);
media_status_t AMediaCodec_signalEndOfInputStream(AMediaCodec* codec);
media_status_t AMediaCodec_setAsyncNotifyCallback(AMediaCodec* codec,
                                                  AMediaCodecOnAsyncNotifyCallback callback,
                                                  void* userdata);
media_status_t AMediaCodec_getName(AMediaCodec* codec, char** out_name);
void AMediaCodec_releaseName(AMediaCodec* codec, char* name);

// AMediaDataSource
AMediaDataSource* AMediaDataSource_new(void);
void AMediaDataSource_delete(AMediaDataSource* src);
void AMediaDataSource_setUserdata(AMediaDataSource* src, void* userdata);
void AMediaDataSource_setReadAt(AMediaDataSource* src, AMediaDataSourceReadAt readAt);
void AMediaDataSource_setGetSize(AMediaDataSource* src, AMediaDataSourceGetSize getSize);
void AMediaDataSource_setClose(AMediaDataSource* src, AMediaDataSourceClose close);
void AMediaDataSource_setGetAvailableSize(AMediaDataSource* src,
                                          AMediaDataSourceGetAvailableSize getAvailableSize);
void AMediaDataSource_close(AMediaDataSource* src);

// AMediaExtractor
AMediaExtractor* AMediaExtractor_new(void);
media_status_t AMediaExtractor_delete(AMediaExtractor* ex);
media_status_t AMediaExtractor_setDataSourceFd(AMediaExtractor* ex, int fd, int64_t offset,
                                               int64_t length);
media_status_t AMediaExtractor_setDataSource(AMediaExtractor* ex, const char* location);
media_status_t AMediaExtractor_setDataSourceCustom(AMediaExtractor* ex, AMediaDataSource* src);
size_t AMediaExtractor_getTrackCount(AMediaExtractor* ex);
AMediaFormat* AMediaExtractor_getTrackFormat(AMediaExtractor* ex, size_t idx);
AMediaFormat* AMediaExtractor_getFileFormat(AMediaExtractor* ex);
media_status_t AMediaExtractor_selectTrack(AMediaExtractor* ex, size_t idx);
media_status_t AMediaExtractor_unselectTrack(AMediaExtractor* ex, size_t idx);
ssize_t AMediaExtractor_readSampleData(AMediaExtractor* ex, uint8_t* buffer, size_t capacity);
uint32_t AMediaExtractor_getSampleFlags(AMediaExtractor* ex);
int AMediaExtractor_getSampleTrackIndex(AMediaExtractor* ex);
int64_t AMediaExtractor_getSampleTime(AMediaExtractor* ex);
int64_t AMediaExtractor_getSampleSize(AMediaExtractor* ex);
bool AMediaExtractor_advance(AMediaExtractor* ex);
media_status_t AMediaExtractor_seekTo(AMediaExtractor* ex, int64_t seekPosUs, SeekMode mode);
int64_t AMediaExtractor_getCachedDuration(AMediaExtractor* ex);

// AMediaMuxer
AMediaMuxer* AMediaMuxer_new(int fd, OutputFormat format);
media_status_t AMediaMuxer_delete(AMediaMuxer* muxer);
media_status_t AMediaMuxer_setLocation(AMediaMuxer* muxer, float latitude, float longitude);
media_status_t AMediaMuxer_setOrientationHint(AMediaMuxer* muxer, int degrees);
ssize_t AMediaMuxer_addTrack(AMediaMuxer* muxer, const AMediaFormat* format);
media_status_t AMediaMuxer_start(AMediaMuxer* muxer);
media_status_t AMediaMuxer_stop(AMediaMuxer* muxer);
media_status_t AMediaMuxer_writeSampleData(AMediaMuxer* muxer, size_t trackIdx, const uint8_t* data,
                                           const AMediaCodecBufferInfo* info);

#ifdef __cplusplus
}
#endif
