// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef int32_t aaudio_result_t;
typedef int32_t aaudio_direction_t;
typedef int32_t aaudio_format_t;
typedef int32_t aaudio_sharing_mode_t;
typedef int32_t aaudio_performance_mode_t;
typedef int32_t aaudio_stream_state_t;
typedef int32_t aaudio_data_callback_result_t;
typedef int32_t aaudio_usage_t;
typedef int32_t aaudio_content_type_t;
typedef int32_t aaudio_input_preset_t;
typedef int32_t aaudio_allowed_capture_policy_t;
typedef int32_t aaudio_session_id_t;
typedef int32_t aaudio_spatialization_behavior_t;
typedef uint32_t aaudio_channel_mask_t;

enum {
    AAUDIO_UNSPECIFIED = 0,
};

enum {
    AAUDIO_DIRECTION_OUTPUT = 0,
    AAUDIO_DIRECTION_INPUT = 1,
};

enum {
    AAUDIO_FORMAT_INVALID = -1,
    AAUDIO_FORMAT_UNSPECIFIED = 0,
    AAUDIO_FORMAT_PCM_I16 = 1,
    AAUDIO_FORMAT_PCM_FLOAT = 2,
    AAUDIO_FORMAT_PCM_I24_PACKED = 3,
    AAUDIO_FORMAT_PCM_I32 = 4,
    AAUDIO_FORMAT_IEC61937 = 5,
};

enum {
    AAUDIO_OK = 0,
    AAUDIO_ERROR_BASE = -900,
    AAUDIO_ERROR_DISCONNECTED = AAUDIO_ERROR_BASE + 1,
    AAUDIO_ERROR_ILLEGAL_ARGUMENT = AAUDIO_ERROR_BASE + 2,
    AAUDIO_ERROR_INTERNAL = AAUDIO_ERROR_BASE + 4,
    AAUDIO_ERROR_INVALID_STATE = AAUDIO_ERROR_BASE + 5,
    AAUDIO_ERROR_INVALID_HANDLE = AAUDIO_ERROR_BASE + 8,
    AAUDIO_ERROR_UNIMPLEMENTED = AAUDIO_ERROR_BASE + 10,
    AAUDIO_ERROR_UNAVAILABLE = AAUDIO_ERROR_BASE + 11,
    AAUDIO_ERROR_NO_FREE_HANDLES = AAUDIO_ERROR_BASE + 12,
    AAUDIO_ERROR_NO_MEMORY = AAUDIO_ERROR_BASE + 13,
    AAUDIO_ERROR_NULL = AAUDIO_ERROR_BASE + 14,
    AAUDIO_ERROR_TIMEOUT = AAUDIO_ERROR_BASE + 15,
    AAUDIO_ERROR_WOULD_BLOCK = AAUDIO_ERROR_BASE + 16,
    AAUDIO_ERROR_INVALID_FORMAT = AAUDIO_ERROR_BASE + 17,
    AAUDIO_ERROR_OUT_OF_RANGE = AAUDIO_ERROR_BASE + 18,
    AAUDIO_ERROR_NO_SERVICE = AAUDIO_ERROR_BASE + 19,
    AAUDIO_ERROR_INVALID_RATE = AAUDIO_ERROR_BASE + 20,
};

enum {
    AAUDIO_STREAM_STATE_UNINITIALIZED = 0,
    AAUDIO_STREAM_STATE_UNKNOWN = 1,
    AAUDIO_STREAM_STATE_OPEN = 2,
    AAUDIO_STREAM_STATE_STARTING = 3,
    AAUDIO_STREAM_STATE_STARTED = 4,
    AAUDIO_STREAM_STATE_PAUSING = 5,
    AAUDIO_STREAM_STATE_PAUSED = 6,
    AAUDIO_STREAM_STATE_FLUSHING = 7,
    AAUDIO_STREAM_STATE_FLUSHED = 8,
    AAUDIO_STREAM_STATE_STOPPING = 9,
    AAUDIO_STREAM_STATE_STOPPED = 10,
    AAUDIO_STREAM_STATE_CLOSING = 11,
    AAUDIO_STREAM_STATE_CLOSED = 12,
    AAUDIO_STREAM_STATE_DISCONNECTED = 13,
};

enum {
    AAUDIO_SHARING_MODE_EXCLUSIVE = 0,
    AAUDIO_SHARING_MODE_SHARED = 1,
};

enum {
    AAUDIO_PERFORMANCE_MODE_NONE = 10,
    AAUDIO_PERFORMANCE_MODE_POWER_SAVING = 11,
    AAUDIO_PERFORMANCE_MODE_LOW_LATENCY = 12,
};

enum {
    AAUDIO_CALLBACK_RESULT_CONTINUE = 0,
    AAUDIO_CALLBACK_RESULT_STOP = 1,
};

enum {
    AAUDIO_USAGE_MEDIA = 1,
    AAUDIO_CONTENT_TYPE_MUSIC = 2,
    AAUDIO_INPUT_PRESET_GENERIC = 1,
    AAUDIO_ALLOW_CAPTURE_BY_ALL = 1,
    AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO = 1,
    AAUDIO_SPATIALIZATION_BEHAVIOR_NEVER = 2,
    AAUDIO_SESSION_ID_NONE = -1,
    AAUDIO_SESSION_ID_ALLOCATE = 0,
};

enum {
    AAUDIO_CHANNEL_MONO = 0x1,
    AAUDIO_CHANNEL_STEREO = 0x3,
};

typedef struct AAudioStreamBuilderStruct AAudioStreamBuilder;
typedef struct AAudioStreamStruct AAudioStream;

typedef aaudio_data_callback_result_t (*AAudioStream_dataCallback)(AAudioStream* stream,
                                                                   void* userData, void* audioData,
                                                                   int32_t numFrames);
typedef void (*AAudioStream_errorCallback)(AAudioStream* stream, void* userData,
                                           aaudio_result_t error);

aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder** builder);
const char* AAudio_convertResultToText(aaudio_result_t returnCode);
const char* AAudio_convertStreamStateToText(aaudio_stream_state_t state);

void AAudioStreamBuilder_setDeviceId(AAudioStreamBuilder* builder, int32_t deviceId);
void AAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* builder, int32_t sampleRate);
void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* builder, int32_t channelCount);
void AAudioStreamBuilder_setChannelMask(AAudioStreamBuilder* builder,
                                        aaudio_channel_mask_t channelMask);
void AAudioStreamBuilder_setFormat(AAudioStreamBuilder* builder, aaudio_format_t format);
void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder* builder,
                                        aaudio_sharing_mode_t sharingMode);
void AAudioStreamBuilder_setDirection(AAudioStreamBuilder* builder, aaudio_direction_t direction);
void AAudioStreamBuilder_setBufferCapacityInFrames(AAudioStreamBuilder* builder, int32_t numFrames);
void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder* builder,
                                            aaudio_performance_mode_t mode);
void AAudioStreamBuilder_setUsage(AAudioStreamBuilder* builder, aaudio_usage_t usage);
void AAudioStreamBuilder_setContentType(AAudioStreamBuilder* builder,
                                        aaudio_content_type_t contentType);
void AAudioStreamBuilder_setInputPreset(AAudioStreamBuilder* builder,
                                        aaudio_input_preset_t inputPreset);
void AAudioStreamBuilder_setSpatializationBehavior(
    AAudioStreamBuilder* builder, aaudio_spatialization_behavior_t spatializationBehavior);
void AAudioStreamBuilder_setIsContentSpatialized(AAudioStreamBuilder* builder, bool isSpatialized);
void AAudioStreamBuilder_setAllowedCapturePolicy(AAudioStreamBuilder* builder,
                                                 aaudio_allowed_capture_policy_t capturePolicy);
void AAudioStreamBuilder_setSessionId(AAudioStreamBuilder* builder, aaudio_session_id_t sessionId);
void AAudioStreamBuilder_setPrivacySensitive(AAudioStreamBuilder* builder, bool privacySensitive);
void AAudioStreamBuilder_setPackageName(AAudioStreamBuilder* builder, const char* packageName);
void AAudioStreamBuilder_setAttributionTag(AAudioStreamBuilder* builder,
                                           const char* attributionTag);
void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder* builder,
                                         AAudioStream_dataCallback callback, void* userData);
void AAudioStreamBuilder_setFramesPerDataCallback(AAudioStreamBuilder* builder, int32_t numFrames);
void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder* builder,
                                          AAudioStream_errorCallback callback, void* userData);
aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder* builder, AAudioStream** stream);
aaudio_result_t AAudioStreamBuilder_delete(AAudioStreamBuilder* builder);

aaudio_result_t AAudioStream_close(AAudioStream* stream);
aaudio_result_t AAudioStream_release(AAudioStream* stream);
aaudio_result_t AAudioStream_requestStart(AAudioStream* stream);
aaudio_result_t AAudioStream_requestPause(AAudioStream* stream);
aaudio_result_t AAudioStream_requestFlush(AAudioStream* stream);
aaudio_result_t AAudioStream_requestStop(AAudioStream* stream);
aaudio_stream_state_t AAudioStream_getState(AAudioStream* stream);
aaudio_result_t AAudioStream_waitForStateChange(AAudioStream* stream,
                                                aaudio_stream_state_t inputState,
                                                aaudio_stream_state_t* nextState,
                                                int64_t timeoutNanoseconds);
aaudio_result_t AAudioStream_read(AAudioStream* stream, void* buffer, int32_t numFrames,
                                  int64_t timeoutNanoseconds);
aaudio_result_t AAudioStream_write(AAudioStream* stream, const void* buffer, int32_t numFrames,
                                   int64_t timeoutNanoseconds);
aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream* stream, int32_t numFrames);
int32_t AAudioStream_getBufferSizeInFrames(AAudioStream* stream);
int32_t AAudioStream_getFramesPerBurst(AAudioStream* stream);
int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream* stream);
int32_t AAudioStream_getFramesPerDataCallback(AAudioStream* stream);
int32_t AAudioStream_getXRunCount(AAudioStream* stream);
int32_t AAudioStream_getSampleRate(AAudioStream* stream);
int32_t AAudioStream_getChannelCount(AAudioStream* stream);
aaudio_channel_mask_t AAudioStream_getChannelMask(AAudioStream* stream);
aaudio_direction_t AAudioStream_getDirection(AAudioStream* stream);
int32_t AAudioStream_getDeviceId(AAudioStream* stream);
aaudio_format_t AAudioStream_getFormat(AAudioStream* stream);
aaudio_sharing_mode_t AAudioStream_getSharingMode(AAudioStream* stream);
aaudio_performance_mode_t AAudioStream_getPerformanceMode(AAudioStream* stream);
int64_t AAudioStream_getFramesWritten(AAudioStream* stream);
int64_t AAudioStream_getFramesRead(AAudioStream* stream);
aaudio_result_t AAudioStream_getTimestamp(AAudioStream* stream, clockid_t clockid,
                                          int64_t* framePosition, int64_t* timeNanoseconds);
aaudio_session_id_t AAudioStream_getSessionId(AAudioStream* stream);
aaudio_usage_t AAudioStream_getUsage(AAudioStream* stream);
aaudio_content_type_t AAudioStream_getContentType(AAudioStream* stream);
aaudio_input_preset_t AAudioStream_getInputPreset(AAudioStream* stream);
bool AAudioStream_isPrivacySensitive(AAudioStream* stream);
aaudio_allowed_capture_policy_t AAudioStream_getAllowedCapturePolicy(AAudioStream* stream);
aaudio_spatialization_behavior_t AAudioStream_getSpatializationBehavior(AAudioStream* stream);
bool AAudioStream_isContentSpatialized(AAudioStream* stream);
int32_t AAudioStream_getHardwareChannelCount(AAudioStream* stream);
int32_t AAudioStream_getHardwareSampleRate(AAudioStream* stream);
aaudio_format_t AAudioStream_getHardwareFormat(AAudioStream* stream);

#ifdef __cplusplus
}
#endif
