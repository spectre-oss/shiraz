// SPDX-License-Identifier: GPL-3.0-only
// Real Android NDK libaaudio.so implementation with state machine, ring buffer,
// callback pump, monotonic timestamps, and optional CoreAudio output on macOS.
#include "shiraz/ndk/aaudio.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <thread>
#include <vector>

#if defined(__APPLE__)
#include <AudioToolbox/AudioQueue.h>
#elif defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
struct ShirazWin32AudioStream;
extern "C" {
ShirazWin32AudioStream* shiraz_win32_audio_open(std::uint32_t sampleRate, std::uint16_t channels);
int shiraz_win32_audio_write(ShirazWin32AudioStream* s, const std::int16_t* pcm,
                             std::size_t frames);
void shiraz_win32_audio_close(ShirazWin32AudioStream* s);
}
#endif

namespace {

std::atomic<int32_t> gNextSessionId{100};

int32_t bytesPerSample(aaudio_format_t fmt) {
    switch (fmt) {
    case AAUDIO_FORMAT_PCM_I16:
    case AAUDIO_FORMAT_IEC61937:
        return 2;
    case AAUDIO_FORMAT_PCM_I24_PACKED:
        return 3;
    case AAUDIO_FORMAT_PCM_FLOAT:
    case AAUDIO_FORMAT_PCM_I32:
        return 4;
    default:
        return 0;
    }
}

int32_t popcount32(uint32_t x) {
    int32_t c = 0;
    while (x) {
        c += static_cast<int32_t>(x & 1u);
        x >>= 1;
    }
    return c;
}

int64_t nowNanos(clockid_t clk) {
#if defined(_WIN32)
    if (clk == CLOCK_REALTIME) {
        FILETIME ft{};
        GetSystemTimeAsFileTime(&ft);
        ULARGE_INTEGER uli{};
        uli.LowPart = ft.dwLowDateTime;
        uli.HighPart = ft.dwHighDateTime;
        constexpr uint64_t kEpochDiff100ns = 116444736000000000ULL;
        const uint64_t sinceEpoch100ns =
            (uli.QuadPart > kEpochDiff100ns) ? (uli.QuadPart - kEpochDiff100ns) : 0ULL;
        return static_cast<int64_t>(sinceEpoch100ns * 100ULL);
    }
    LARGE_INTEGER freq{}, counter{};
    QueryPerformanceFrequency(&freq);
    QueryPerformanceCounter(&counter);
    if (freq.QuadPart <= 0) {
        return 0;
    }
    const int64_t sec = counter.QuadPart / freq.QuadPart;
    const int64_t rem = counter.QuadPart % freq.QuadPart;
    return sec * 1000000000LL + (rem * 1000000000LL) / freq.QuadPart;
#else
    struct timespec ts{};
#if defined(__APPLE__)
    const clockid_t actual = (clk == CLOCK_REALTIME) ? CLOCK_REALTIME : CLOCK_MONOTONIC;
#else
    const clockid_t actual = clk;
#endif
    if (::clock_gettime(actual, &ts) != 0) {
        ::clock_gettime(CLOCK_MONOTONIC, &ts);
    }
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + static_cast<int64_t>(ts.tv_nsec);
#endif
}

bool isValidFormat(aaudio_format_t fmt) {
    return fmt == AAUDIO_FORMAT_UNSPECIFIED || fmt == AAUDIO_FORMAT_PCM_I16 ||
           fmt == AAUDIO_FORMAT_PCM_FLOAT || fmt == AAUDIO_FORMAT_PCM_I24_PACKED ||
           fmt == AAUDIO_FORMAT_PCM_I32 || fmt == AAUDIO_FORMAT_IEC61937;
}

} // namespace

struct AAudioStreamBuilderStruct {
    int32_t deviceId = AAUDIO_UNSPECIFIED;
    int32_t sampleRate = AAUDIO_UNSPECIFIED;
    int32_t channelCount = AAUDIO_UNSPECIFIED;
    aaudio_channel_mask_t channelMask = AAUDIO_UNSPECIFIED;
    aaudio_format_t format = AAUDIO_FORMAT_UNSPECIFIED;
    aaudio_sharing_mode_t sharingMode = AAUDIO_SHARING_MODE_SHARED;
    aaudio_direction_t direction = AAUDIO_DIRECTION_OUTPUT;
    int32_t bufferCapacityInFrames = AAUDIO_UNSPECIFIED;
    aaudio_performance_mode_t performanceMode = AAUDIO_PERFORMANCE_MODE_NONE;
    aaudio_usage_t usage = AAUDIO_USAGE_MEDIA;
    aaudio_content_type_t contentType = AAUDIO_CONTENT_TYPE_MUSIC;
    aaudio_input_preset_t inputPreset = AAUDIO_INPUT_PRESET_GENERIC;
    aaudio_spatialization_behavior_t spatializationBehavior = AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO;
    bool isContentSpatialized = false;
    aaudio_allowed_capture_policy_t allowedCapturePolicy = AAUDIO_ALLOW_CAPTURE_BY_ALL;
    aaudio_session_id_t sessionId = AAUDIO_SESSION_ID_NONE;
    bool privacySensitive = false;
    std::string packageName;
    std::string attributionTag;
    AAudioStream_dataCallback dataCallback = nullptr;
    void* dataCallbackUserData = nullptr;
    int32_t framesPerDataCallback = AAUDIO_UNSPECIFIED;
    AAudioStream_errorCallback errorCallback = nullptr;
    void* errorCallbackUserData = nullptr;
};

struct AAudioStreamStruct {
    int32_t deviceId = 1;
    int32_t sampleRate = 48000;
    int32_t channelCount = 2;
    aaudio_channel_mask_t channelMask = AAUDIO_CHANNEL_STEREO;
    aaudio_format_t format = AAUDIO_FORMAT_PCM_FLOAT;
    aaudio_sharing_mode_t sharingMode = AAUDIO_SHARING_MODE_SHARED;
    aaudio_direction_t direction = AAUDIO_DIRECTION_OUTPUT;
    aaudio_performance_mode_t performanceMode = AAUDIO_PERFORMANCE_MODE_NONE;
    aaudio_usage_t usage = AAUDIO_USAGE_MEDIA;
    aaudio_content_type_t contentType = AAUDIO_CONTENT_TYPE_MUSIC;
    aaudio_input_preset_t inputPreset = AAUDIO_INPUT_PRESET_GENERIC;
    aaudio_spatialization_behavior_t spatializationBehavior = AAUDIO_SPATIALIZATION_BEHAVIOR_AUTO;
    bool isContentSpatialized = false;
    aaudio_allowed_capture_policy_t allowedCapturePolicy = AAUDIO_ALLOW_CAPTURE_BY_ALL;
    aaudio_session_id_t sessionId = AAUDIO_SESSION_ID_NONE;
    bool privacySensitive = false;

    int32_t framesPerBurst = 192;
    int32_t bufferCapacityInFrames = 3840;
    int32_t bufferSizeInFrames = 768;
    int32_t framesPerDataCallback = 0;
    int32_t frameSizeBytes = 8;

    AAudioStream_dataCallback dataCallback = nullptr;
    void* dataCallbackUserData = nullptr;
    AAudioStream_errorCallback errorCallback = nullptr;
    void* errorCallbackUserData = nullptr;

    std::mutex mu;
    std::condition_variable cv;
    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_OPEN;
    bool released = false;
    bool stopWorker = false;

    // Ring buffer (in frames)
    std::vector<uint8_t> ring;
    int32_t ringReadIdx = 0;
    int32_t ringWriteIdx = 0;
    int32_t ringUsedFrames = 0;

    int64_t framesWritten = 0;
    int64_t framesRead = 0;
    int32_t xrunCount = 0;
    int64_t lastTimestampNs = 0;
#if defined(__APPLE__)
    std::mutex audioMu;
    AudioQueueRef audioQueue = nullptr;
    bool audioQueueStarted = false;
#elif defined(_WIN32)
    ShirazWin32AudioStream* win32Audio = nullptr;
#endif

    std::thread worker;

    void setStateLocked(aaudio_stream_state_t next) {
        state = next;
        cv.notify_all();
    }

    void joinWorker() {
        {
            std::lock_guard<std::mutex> lock(mu);
            stopWorker = true;
            cv.notify_all();
        }
        if (worker.joinable()) {
            worker.join();
        }
#if defined(__APPLE__)
        {
            std::lock_guard<std::mutex> audioLock(audioMu);
            if (audioQueue) {
                AudioQueueDispose(audioQueue, true);
                audioQueue = nullptr;
                audioQueueStarted = false;
            }
        }
#elif defined(_WIN32)
        if (win32Audio) {
            shiraz_win32_audio_close(win32Audio);
            win32Audio = nullptr;
        }
#endif
    }

    ~AAudioStreamStruct() {
        joinWorker();
    }
};

namespace {
#if defined(__APPLE__) || defined(_WIN32)
bool hostAudioBackendEnabled() {
    const char* backend = std::getenv("SHIRAZ_AUDIO_BACKEND");
    if (backend && std::strcmp(backend, "null") == 0) {
        return false;
    }
    const char* device = std::getenv("SHIRAZ_AUDIO_DEVICE");
    if (device && std::strcmp(device, "null") == 0) {
        return false;
    }
    return true;
}
#endif
#if defined(__APPLE__)
void openDarwinAudioQueue(AAudioStreamStruct* s) {
    if (!s || s->direction != AAUDIO_DIRECTION_OUTPUT || !hostAudioBackendEnabled()) {
        return;
    }
    if (s->format != AAUDIO_FORMAT_PCM_I16 && s->format != AAUDIO_FORMAT_PCM_FLOAT &&
        s->format != AAUDIO_FORMAT_PCM_I32) {
        return;
    }
    AudioStreamBasicDescription desc{};
    desc.mSampleRate = static_cast<Float64>(s->sampleRate);
    desc.mFormatID = kAudioFormatLinearPCM;
    desc.mFormatFlags = kAudioFormatFlagIsPacked |
                        ((s->format == AAUDIO_FORMAT_PCM_FLOAT) ? kAudioFormatFlagIsFloat
                                                                : kAudioFormatFlagIsSignedInteger);
    desc.mBytesPerPacket = static_cast<UInt32>(s->frameSizeBytes);
    desc.mFramesPerPacket = 1;
    desc.mBytesPerFrame = static_cast<UInt32>(s->frameSizeBytes);
    desc.mChannelsPerFrame = static_cast<UInt32>(s->channelCount);
    desc.mBitsPerChannel = static_cast<UInt32>(bytesPerSample(s->format) * 8);
    if (AudioQueueNewOutput(
            &desc,
            [](void*, AudioQueueRef q, AudioQueueBufferRef buf) { AudioQueueFreeBuffer(q, buf); },
            nullptr, nullptr, nullptr, 0, &s->audioQueue) != noErr) {
        s->audioQueue = nullptr;
    }
}

void submitDarwinAudio(AAudioStreamStruct* s, const uint8_t* data, size_t byteLen) {
    if (!s || !data || byteLen == 0) {
        return;
    }
    std::lock_guard<std::mutex> audioLock(s->audioMu);
    if (!s->audioQueue) {
        return;
    }
    AudioQueueBufferRef buf = nullptr;
    if (AudioQueueAllocateBuffer(s->audioQueue, static_cast<UInt32>(byteLen), &buf) != noErr ||
        !buf) {
        return;
    }
    std::memcpy(buf->mAudioData, data, byteLen);
    buf->mAudioDataByteSize = static_cast<UInt32>(byteLen);
    if (AudioQueueEnqueueBuffer(s->audioQueue, buf, 0, nullptr) != noErr) {
        AudioQueueFreeBuffer(s->audioQueue, buf);
        return;
    }
    if (!s->audioQueueStarted) {
        if (AudioQueueStart(s->audioQueue, nullptr) == noErr) {
            s->audioQueueStarted = true;
        }
    }
}
#elif defined(_WIN32)
void openWin32Audio(AAudioStreamStruct* s) {
    if (!s || s->direction != AAUDIO_DIRECTION_OUTPUT || !hostAudioBackendEnabled()) {
        return;
    }
    s->win32Audio = shiraz_win32_audio_open(static_cast<std::uint32_t>(s->sampleRate),
                                            static_cast<std::uint16_t>(s->channelCount));
}

void submitWin32Audio(AAudioStreamStruct* s, const uint8_t* data, int32_t frames) {
    if (!s || !s->win32Audio || !data || frames <= 0) {
        return;
    }
    const size_t samples = static_cast<size_t>(frames) * static_cast<size_t>(s->channelCount);
    if (s->format == AAUDIO_FORMAT_PCM_I16) {
        shiraz_win32_audio_write(s->win32Audio, reinterpret_cast<const std::int16_t*>(data),
                                 static_cast<std::size_t>(frames));
        return;
    }
    std::vector<std::int16_t> converted(samples, 0);
    if (s->format == AAUDIO_FORMAT_PCM_FLOAT) {
        const auto* src = reinterpret_cast<const float*>(data);
        for (size_t i = 0; i < samples; ++i) {
            const float clamped = std::clamp(src[i], -1.0f, 1.0f);
            converted[i] = static_cast<std::int16_t>(clamped * 32767.0f);
        }
    } else if (s->format == AAUDIO_FORMAT_PCM_I32) {
        const auto* src = reinterpret_cast<const int32_t*>(data);
        for (size_t i = 0; i < samples; ++i) {
            converted[i] = static_cast<std::int16_t>(src[i] >> 16);
        }
    } else {
        return;
    }
    shiraz_win32_audio_write(s->win32Audio, converted.data(), static_cast<std::size_t>(frames));
}
#endif

void workerLoop(AAudioStreamStruct* s) {
    const int32_t burst =
        (s->framesPerDataCallback > 0) ? s->framesPerDataCallback : s->framesPerBurst;
    std::vector<uint8_t> scratch(static_cast<size_t>(burst) *
                                 static_cast<size_t>(s->frameSizeBytes));
    const auto periodUs = std::chrono::microseconds(
        std::max<int64_t>(1000, (static_cast<int64_t>(burst) * 1000000LL) / s->sampleRate));

    auto nextWake = std::chrono::steady_clock::now();

    while (true) {
        AAudioStream_dataCallback cb = nullptr;
        void* ud = nullptr;
        bool running = false;
        {
            std::unique_lock<std::mutex> lock(s->mu);
            s->cv.wait_until(lock, nextWake, [&] {
                return s->stopWorker || s->state != AAUDIO_STREAM_STATE_STARTED;
            });
            if (s->stopWorker) {
                break;
            }
            if (s->state != AAUDIO_STREAM_STATE_STARTED) {
                s->cv.wait(
                    lock, [&] { return s->stopWorker || s->state == AAUDIO_STREAM_STATE_STARTED; });
                if (s->stopWorker) {
                    break;
                }
                nextWake = std::chrono::steady_clock::now();
            }
            cb = s->dataCallback;
            ud = s->dataCallbackUserData;
            running = (s->state == AAUDIO_STREAM_STATE_STARTED);
        }

        if (!running) {
            continue;
        }

        nextWake += periodUs;
        if (cb) {
            std::memset(scratch.data(), 0, scratch.size());
            const aaudio_data_callback_result_t res = cb(s, ud, scratch.data(), burst);
#if defined(__APPLE__)
            if (s->direction == AAUDIO_DIRECTION_OUTPUT) {
                submitDarwinAudio(s, scratch.data(), scratch.size());
            }
#elif defined(_WIN32)
            if (s->direction == AAUDIO_DIRECTION_OUTPUT) {
                submitWin32Audio(s, scratch.data(), burst);
            }
#endif
            std::lock_guard<std::mutex> lock(s->mu);
            if (s->direction == AAUDIO_DIRECTION_OUTPUT) {
                s->framesWritten += burst;
                s->framesRead += burst;
            } else {
                s->framesRead += burst;
            }
            s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
            if (res == AAUDIO_CALLBACK_RESULT_STOP && s->state == AAUDIO_STREAM_STATE_STARTED) {
                s->setStateLocked(AAUDIO_STREAM_STATE_STOPPED);
            }
        } else {
            // Blocking write/read mode: drain output ring or feed input silence.
#if defined(__APPLE__) || defined(_WIN32)
            int32_t drainedFrames = 0;
            std::unique_lock<std::mutex> lock(s->mu);
#else
            std::lock_guard<std::mutex> lock(s->mu);
#endif
            if (s->state != AAUDIO_STREAM_STATE_STARTED) {
                continue;
            }
            if (s->direction == AAUDIO_DIRECTION_OUTPUT) {
                const int32_t drain = std::min(burst, s->ringUsedFrames);
                if (drain > 0) {
#if defined(__APPLE__) || defined(_WIN32)
                    for (int32_t i = 0; i < drain; ++i) {
                        const int32_t idx = (s->ringReadIdx + i) % s->bufferCapacityInFrames;
                        const size_t srcOff =
                            static_cast<size_t>(idx) * static_cast<size_t>(s->frameSizeBytes);
                        const size_t dstOff =
                            static_cast<size_t>(i) * static_cast<size_t>(s->frameSizeBytes);
                        std::memcpy(scratch.data() + dstOff, s->ring.data() + srcOff,
                                    static_cast<size_t>(s->frameSizeBytes));
                    }
                    drainedFrames = drain;
#endif
                    s->ringReadIdx = (s->ringReadIdx + drain) % s->bufferCapacityInFrames;
                    s->ringUsedFrames -= drain;
                    s->framesRead += drain;
                    s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
                    s->cv.notify_all();
                } else if (s->framesWritten > 0) {
                    s->xrunCount++;
                }
            } else {
                const int32_t freeFrames = s->bufferCapacityInFrames - s->ringUsedFrames;
                const int32_t produce = std::min(burst, freeFrames);
                if (produce > 0) {
                    for (int32_t i = 0; i < produce; ++i) {
                        const size_t off = static_cast<size_t>(s->ringWriteIdx) *
                                           static_cast<size_t>(s->frameSizeBytes);
                        std::memset(s->ring.data() + off, 0,
                                    static_cast<size_t>(s->frameSizeBytes));
                        s->ringWriteIdx = (s->ringWriteIdx + 1) % s->bufferCapacityInFrames;
                    }
                    s->ringUsedFrames += produce;
                    s->framesWritten += produce;
                    s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
                    s->cv.notify_all();
                }
            }
#if defined(__APPLE__)
            lock.unlock();
            if (drainedFrames > 0) {
                submitDarwinAudio(s, scratch.data(),
                                  static_cast<size_t>(drainedFrames) *
                                      static_cast<size_t>(s->frameSizeBytes));
            }
#elif defined(_WIN32)
            lock.unlock();
            if (drainedFrames > 0) {
                submitWin32Audio(s, scratch.data(), drainedFrames);
            }
#endif
        }
    }
}

} // namespace

extern "C" {

aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder** builder) {
    if (!builder) {
        return AAUDIO_ERROR_NULL;
    }
    auto* b = new (std::nothrow) AAudioStreamBuilderStruct();
    if (!b) {
        *builder = nullptr;
        return AAUDIO_ERROR_NO_MEMORY;
    }
    *builder = b;
    return AAUDIO_OK;
}

const char* AAudio_convertResultToText(aaudio_result_t returnCode) {
    switch (returnCode) {
    case AAUDIO_OK:
        return "AAUDIO_OK";
    case AAUDIO_ERROR_DISCONNECTED:
        return "AAUDIO_ERROR_DISCONNECTED";
    case AAUDIO_ERROR_ILLEGAL_ARGUMENT:
        return "AAUDIO_ERROR_ILLEGAL_ARGUMENT";
    case AAUDIO_ERROR_INTERNAL:
        return "AAUDIO_ERROR_INTERNAL";
    case AAUDIO_ERROR_INVALID_STATE:
        return "AAUDIO_ERROR_INVALID_STATE";
    case AAUDIO_ERROR_INVALID_HANDLE:
        return "AAUDIO_ERROR_INVALID_HANDLE";
    case AAUDIO_ERROR_UNIMPLEMENTED:
        return "AAUDIO_ERROR_UNIMPLEMENTED";
    case AAUDIO_ERROR_UNAVAILABLE:
        return "AAUDIO_ERROR_UNAVAILABLE";
    case AAUDIO_ERROR_NO_FREE_HANDLES:
        return "AAUDIO_ERROR_NO_FREE_HANDLES";
    case AAUDIO_ERROR_NO_MEMORY:
        return "AAUDIO_ERROR_NO_MEMORY";
    case AAUDIO_ERROR_NULL:
        return "AAUDIO_ERROR_NULL";
    case AAUDIO_ERROR_TIMEOUT:
        return "AAUDIO_ERROR_TIMEOUT";
    case AAUDIO_ERROR_WOULD_BLOCK:
        return "AAUDIO_ERROR_WOULD_BLOCK";
    case AAUDIO_ERROR_INVALID_FORMAT:
        return "AAUDIO_ERROR_INVALID_FORMAT";
    case AAUDIO_ERROR_OUT_OF_RANGE:
        return "AAUDIO_ERROR_OUT_OF_RANGE";
    case AAUDIO_ERROR_NO_SERVICE:
        return "AAUDIO_ERROR_NO_SERVICE";
    case AAUDIO_ERROR_INVALID_RATE:
        return "AAUDIO_ERROR_INVALID_RATE";
    default:
        return "AAUDIO_ERROR_UNKNOWN";
    }
}

const char* AAudio_convertStreamStateToText(aaudio_stream_state_t state) {
    switch (state) {
    case AAUDIO_STREAM_STATE_UNINITIALIZED:
        return "AAUDIO_STREAM_STATE_UNINITIALIZED";
    case AAUDIO_STREAM_STATE_UNKNOWN:
        return "AAUDIO_STREAM_STATE_UNKNOWN";
    case AAUDIO_STREAM_STATE_OPEN:
        return "AAUDIO_STREAM_STATE_OPEN";
    case AAUDIO_STREAM_STATE_STARTING:
        return "AAUDIO_STREAM_STATE_STARTING";
    case AAUDIO_STREAM_STATE_STARTED:
        return "AAUDIO_STREAM_STATE_STARTED";
    case AAUDIO_STREAM_STATE_PAUSING:
        return "AAUDIO_STREAM_STATE_PAUSING";
    case AAUDIO_STREAM_STATE_PAUSED:
        return "AAUDIO_STREAM_STATE_PAUSED";
    case AAUDIO_STREAM_STATE_FLUSHING:
        return "AAUDIO_STREAM_STATE_FLUSHING";
    case AAUDIO_STREAM_STATE_FLUSHED:
        return "AAUDIO_STREAM_STATE_FLUSHED";
    case AAUDIO_STREAM_STATE_STOPPING:
        return "AAUDIO_STREAM_STATE_STOPPING";
    case AAUDIO_STREAM_STATE_STOPPED:
        return "AAUDIO_STREAM_STATE_STOPPED";
    case AAUDIO_STREAM_STATE_CLOSING:
        return "AAUDIO_STREAM_STATE_CLOSING";
    case AAUDIO_STREAM_STATE_CLOSED:
        return "AAUDIO_STREAM_STATE_CLOSED";
    case AAUDIO_STREAM_STATE_DISCONNECTED:
        return "AAUDIO_STREAM_STATE_DISCONNECTED";
    default:
        return "AAUDIO_STREAM_STATE_UNKNOWN";
    }
}

void AAudioStreamBuilder_setDeviceId(AAudioStreamBuilder* b, int32_t deviceId) {
    if (b) {
        b->deviceId = deviceId;
    }
}
void AAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* b, int32_t sampleRate) {
    if (b) {
        b->sampleRate = sampleRate;
    }
}
void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* b, int32_t channelCount) {
    if (b) {
        b->channelCount = channelCount;
        b->channelMask = AAUDIO_UNSPECIFIED;
    }
}
void AAudioStreamBuilder_setChannelMask(AAudioStreamBuilder* b, aaudio_channel_mask_t channelMask) {
    if (b) {
        b->channelMask = channelMask;
        if (channelMask != AAUDIO_UNSPECIFIED) {
            b->channelCount = popcount32(channelMask);
        }
    }
}
void AAudioStreamBuilder_setFormat(AAudioStreamBuilder* b, aaudio_format_t format) {
    if (b) {
        b->format = format;
    }
}
void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder* b, aaudio_sharing_mode_t mode) {
    if (b) {
        b->sharingMode = mode;
    }
}
void AAudioStreamBuilder_setDirection(AAudioStreamBuilder* b, aaudio_direction_t direction) {
    if (b) {
        b->direction = direction;
    }
}
void AAudioStreamBuilder_setBufferCapacityInFrames(AAudioStreamBuilder* b, int32_t numFrames) {
    if (b) {
        b->bufferCapacityInFrames = numFrames;
    }
}
void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder* b,
                                            aaudio_performance_mode_t mode) {
    if (b) {
        b->performanceMode = mode;
    }
}
void AAudioStreamBuilder_setUsage(AAudioStreamBuilder* b, aaudio_usage_t usage) {
    if (b) {
        b->usage = usage;
    }
}
void AAudioStreamBuilder_setContentType(AAudioStreamBuilder* b, aaudio_content_type_t contentType) {
    if (b) {
        b->contentType = contentType;
    }
}
void AAudioStreamBuilder_setInputPreset(AAudioStreamBuilder* b, aaudio_input_preset_t inputPreset) {
    if (b) {
        b->inputPreset = inputPreset;
    }
}
void AAudioStreamBuilder_setSpatializationBehavior(AAudioStreamBuilder* b,
                                                   aaudio_spatialization_behavior_t behavior) {
    if (b) {
        b->spatializationBehavior = behavior;
    }
}
void AAudioStreamBuilder_setIsContentSpatialized(AAudioStreamBuilder* b, bool isSpatialized) {
    if (b) {
        b->isContentSpatialized = isSpatialized;
    }
}
void AAudioStreamBuilder_setAllowedCapturePolicy(AAudioStreamBuilder* b,
                                                 aaudio_allowed_capture_policy_t policy) {
    if (b) {
        b->allowedCapturePolicy = policy;
    }
}
void AAudioStreamBuilder_setSessionId(AAudioStreamBuilder* b, aaudio_session_id_t sessionId) {
    if (b) {
        b->sessionId = sessionId;
    }
}
void AAudioStreamBuilder_setPrivacySensitive(AAudioStreamBuilder* b, bool privacySensitive) {
    if (b) {
        b->privacySensitive = privacySensitive;
    }
}
void AAudioStreamBuilder_setPackageName(AAudioStreamBuilder* b, const char* packageName) {
    if (b) {
        b->packageName = packageName ? packageName : "";
    }
}
void AAudioStreamBuilder_setAttributionTag(AAudioStreamBuilder* b, const char* attributionTag) {
    if (b) {
        b->attributionTag = attributionTag ? attributionTag : "";
    }
}
void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder* b, AAudioStream_dataCallback callback,
                                         void* userData) {
    if (b) {
        b->dataCallback = callback;
        b->dataCallbackUserData = userData;
    }
}
void AAudioStreamBuilder_setFramesPerDataCallback(AAudioStreamBuilder* b, int32_t numFrames) {
    if (b) {
        b->framesPerDataCallback = numFrames;
    }
}
void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder* b,
                                          AAudioStream_errorCallback callback, void* userData) {
    if (b) {
        b->errorCallback = callback;
        b->errorCallbackUserData = userData;
    }
}

aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder* b, AAudioStream** outStream) {
    if (!b || !outStream) {
        return AAUDIO_ERROR_NULL;
    }
    *outStream = nullptr;
    if (!isValidFormat(b->format)) {
        return AAUDIO_ERROR_INVALID_FORMAT;
    }
    if (b->sampleRate != AAUDIO_UNSPECIFIED && (b->sampleRate < 8000 || b->sampleRate > 384000)) {
        return AAUDIO_ERROR_INVALID_RATE;
    }
    if (b->channelCount != AAUDIO_UNSPECIFIED && (b->channelCount < 1 || b->channelCount > 8)) {
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }

    auto* s = new (std::nothrow) AAudioStreamStruct();
    if (!s) {
        return AAUDIO_ERROR_NO_MEMORY;
    }
    s->deviceId = (b->deviceId == AAUDIO_UNSPECIFIED) ? 1 : b->deviceId;
    s->sampleRate = (b->sampleRate == AAUDIO_UNSPECIFIED) ? 48000 : b->sampleRate;
    s->channelCount = (b->channelCount == AAUDIO_UNSPECIFIED) ? 2 : b->channelCount;
    if (b->channelMask != AAUDIO_UNSPECIFIED) {
        s->channelMask = b->channelMask;
    } else {
        s->channelMask = (s->channelCount == 1) ? AAUDIO_CHANNEL_MONO : AAUDIO_CHANNEL_STEREO;
    }
    s->format = (b->format == AAUDIO_FORMAT_UNSPECIFIED) ? AAUDIO_FORMAT_PCM_FLOAT : b->format;
    s->sharingMode = b->sharingMode;
    s->direction = b->direction;
    s->performanceMode = b->performanceMode;
    s->usage = b->usage;
    s->contentType = b->contentType;
    s->inputPreset = b->inputPreset;
    s->spatializationBehavior = b->spatializationBehavior;
    s->isContentSpatialized = b->isContentSpatialized;
    s->allowedCapturePolicy = b->allowedCapturePolicy;
    s->privacySensitive = b->privacySensitive;
    if (b->sessionId == AAUDIO_SESSION_ID_ALLOCATE) {
        s->sessionId = gNextSessionId.fetch_add(1);
    } else {
        s->sessionId = b->sessionId;
    }

    s->framesPerBurst = (s->performanceMode == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY) ? 128 : 192;
    const int32_t defaultCap = s->framesPerBurst * 16;
    s->bufferCapacityInFrames = (b->bufferCapacityInFrames > 0)
                                    ? std::max(b->bufferCapacityInFrames, s->framesPerBurst * 2)
                                    : defaultCap;
    s->bufferSizeInFrames = std::min(s->bufferCapacityInFrames, s->framesPerBurst * 4);
    s->framesPerDataCallback = (b->framesPerDataCallback > 0) ? b->framesPerDataCallback : 0;
    s->frameSizeBytes = s->channelCount * bytesPerSample(s->format);
    s->dataCallback = b->dataCallback;
    s->dataCallbackUserData = b->dataCallbackUserData;
    s->errorCallback = b->errorCallback;
    s->errorCallbackUserData = b->errorCallbackUserData;

    s->ring.resize(static_cast<size_t>(s->bufferCapacityInFrames) *
                   static_cast<size_t>(s->frameSizeBytes));
    s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
    s->state = AAUDIO_STREAM_STATE_OPEN;
#if defined(__APPLE__)
    openDarwinAudioQueue(s);
#elif defined(_WIN32)
    openWin32Audio(s);
#endif
    s->worker = std::thread(workerLoop, s);

    *outStream = s;
    return AAUDIO_OK;
}

aaudio_result_t AAudioStreamBuilder_delete(AAudioStreamBuilder* b) {
    if (!b) {
        return AAUDIO_ERROR_NULL;
    }
    delete b;
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_release(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    s->joinWorker();
    std::lock_guard<std::mutex> lock(s->mu);
    s->released = true;
    s->setStateLocked(AAUDIO_STREAM_STATE_CLOSED);
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_close(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    AAudioStream_release(s);
    delete s;
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_requestStart(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
    s->setStateLocked(AAUDIO_STREAM_STATE_STARTED);
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_requestPause(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    if (s->direction == AAUDIO_DIRECTION_INPUT) {
        return AAUDIO_ERROR_UNIMPLEMENTED;
    }
    if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
#if defined(__APPLE__)
    {
        std::lock_guard<std::mutex> audioLock(s->audioMu);
        if (s->audioQueue && s->audioQueueStarted) {
            AudioQueuePause(s->audioQueue);
            s->audioQueueStarted = false;
        }
    }
#endif
    s->setStateLocked(AAUDIO_STREAM_STATE_PAUSED);
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_requestFlush(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    if (s->direction == AAUDIO_DIRECTION_INPUT) {
        return AAUDIO_ERROR_UNIMPLEMENTED;
    }
    if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
    s->ringReadIdx = 0;
    s->ringWriteIdx = 0;
    s->ringUsedFrames = 0;
#if defined(__APPLE__)
    {
        std::lock_guard<std::mutex> audioLock(s->audioMu);
        if (s->audioQueue) {
            AudioQueueReset(s->audioQueue);
        }
    }
#endif
    s->setStateLocked(AAUDIO_STREAM_STATE_FLUSHED);
    return AAUDIO_OK;
}

aaudio_result_t AAudioStream_requestStop(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
#if defined(__APPLE__)
    {
        std::lock_guard<std::mutex> audioLock(s->audioMu);
        if (s->audioQueue && s->audioQueueStarted) {
            AudioQueueStop(s->audioQueue, true);
            s->audioQueueStarted = false;
        }
    }
#endif
    s->setStateLocked(AAUDIO_STREAM_STATE_STOPPED);
    return AAUDIO_OK;
}

aaudio_stream_state_t AAudioStream_getState(AAudioStream* s) {
    if (!s) {
        return AAUDIO_STREAM_STATE_UNINITIALIZED;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    return s->state;
}

aaudio_result_t AAudioStream_waitForStateChange(AAudioStream* s, aaudio_stream_state_t inputState,
                                                aaudio_stream_state_t* nextState,
                                                int64_t timeoutNanoseconds) {
    if (!s || !nextState) {
        return AAUDIO_ERROR_NULL;
    }
    std::unique_lock<std::mutex> lock(s->mu);
    if (s->state != inputState) {
        *nextState = s->state;
        return AAUDIO_OK;
    }
    if (timeoutNanoseconds <= 0) {
        *nextState = s->state;
        return AAUDIO_ERROR_TIMEOUT;
    }
    const bool changed = s->cv.wait_for(lock, std::chrono::nanoseconds(timeoutNanoseconds),
                                        [&] { return s->state != inputState; });
    *nextState = s->state;
    return changed ? AAUDIO_OK : AAUDIO_ERROR_TIMEOUT;
}

aaudio_result_t AAudioStream_write(AAudioStream* s, const void* buffer, int32_t numFrames,
                                   int64_t timeoutNanoseconds) {
    if (!s || !buffer) {
        return AAUDIO_ERROR_NULL;
    }
    if (numFrames < 0) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }
    if (s->direction != AAUDIO_DIRECTION_OUTPUT || s->dataCallback != nullptr) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
    if (numFrames == 0) {
        return 0;
    }

    const auto deadline = std::chrono::steady_clock::now() +
                          std::chrono::nanoseconds(std::max<int64_t>(0, timeoutNanoseconds));
    const auto* src = static_cast<const uint8_t*>(buffer);
    int32_t written = 0;

    std::unique_lock<std::mutex> lock(s->mu);
    while (written < numFrames) {
        if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
            return written > 0 ? written : AAUDIO_ERROR_INVALID_STATE;
        }
        const int32_t freeFrames = s->bufferSizeInFrames - s->ringUsedFrames;
        if (freeFrames > 0) {
            const int32_t chunk = std::min(numFrames - written, freeFrames);
            for (int32_t i = 0; i < chunk; ++i) {
                const size_t dstOff =
                    static_cast<size_t>(s->ringWriteIdx) * static_cast<size_t>(s->frameSizeBytes);
                const size_t srcOff =
                    static_cast<size_t>(written + i) * static_cast<size_t>(s->frameSizeBytes);
                std::memcpy(s->ring.data() + dstOff, src + srcOff,
                            static_cast<size_t>(s->frameSizeBytes));
                s->ringWriteIdx = (s->ringWriteIdx + 1) % s->bufferCapacityInFrames;
            }
            s->ringUsedFrames += chunk;
            s->framesWritten += chunk;
            written += chunk;
            s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
        } else {
            if (timeoutNanoseconds <= 0 || std::chrono::steady_clock::now() >= deadline) {
                break;
            }
            s->cv.wait_until(lock, deadline, [&] {
                return s->released || s->state == AAUDIO_STREAM_STATE_CLOSED ||
                       s->ringUsedFrames < s->bufferSizeInFrames;
            });
        }
    }
    return written;
}

aaudio_result_t AAudioStream_read(AAudioStream* s, void* buffer, int32_t numFrames,
                                  int64_t timeoutNanoseconds) {
    if (!s || !buffer) {
        return AAUDIO_ERROR_NULL;
    }
    if (numFrames < 0) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }
    if (s->direction != AAUDIO_DIRECTION_INPUT || s->dataCallback != nullptr) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
    if (numFrames == 0) {
        return 0;
    }

    const auto deadline = std::chrono::steady_clock::now() +
                          std::chrono::nanoseconds(std::max<int64_t>(0, timeoutNanoseconds));
    auto* dst = static_cast<uint8_t*>(buffer);
    int32_t totalRead = 0;

    std::unique_lock<std::mutex> lock(s->mu);
    while (totalRead < numFrames) {
        if (s->released || s->state == AAUDIO_STREAM_STATE_CLOSED) {
            return totalRead > 0 ? totalRead : AAUDIO_ERROR_INVALID_STATE;
        }
        if (s->ringUsedFrames > 0) {
            const int32_t chunk = std::min(numFrames - totalRead, s->ringUsedFrames);
            for (int32_t i = 0; i < chunk; ++i) {
                const size_t srcOff =
                    static_cast<size_t>(s->ringReadIdx) * static_cast<size_t>(s->frameSizeBytes);
                const size_t dstOff =
                    static_cast<size_t>(totalRead + i) * static_cast<size_t>(s->frameSizeBytes);
                std::memcpy(dst + dstOff, s->ring.data() + srcOff,
                            static_cast<size_t>(s->frameSizeBytes));
                s->ringReadIdx = (s->ringReadIdx + 1) % s->bufferCapacityInFrames;
            }
            s->ringUsedFrames -= chunk;
            s->framesRead += chunk;
            totalRead += chunk;
            s->lastTimestampNs = nowNanos(CLOCK_MONOTONIC);
        } else {
            if (timeoutNanoseconds <= 0 || std::chrono::steady_clock::now() >= deadline) {
                break;
            }
            s->cv.wait_until(lock, deadline, [&] {
                return s->released || s->state == AAUDIO_STREAM_STATE_CLOSED ||
                       s->ringUsedFrames > 0;
            });
        }
    }
    return totalRead;
}

aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream* s, int32_t numFrames) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    if (numFrames <= 0) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    s->bufferSizeInFrames = std::clamp(numFrames, s->framesPerBurst, s->bufferCapacityInFrames);
    return s->bufferSizeInFrames;
}

int32_t AAudioStream_getBufferSizeInFrames(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    return s->bufferSizeInFrames;
}
int32_t AAudioStream_getFramesPerBurst(AAudioStream* s) {
    return s ? s->framesPerBurst : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream* s) {
    return s ? s->bufferCapacityInFrames : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getFramesPerDataCallback(AAudioStream* s) {
    return s ? s->framesPerDataCallback : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getXRunCount(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    return s->xrunCount;
}
int32_t AAudioStream_getSampleRate(AAudioStream* s) {
    return s ? s->sampleRate : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getChannelCount(AAudioStream* s) {
    return s ? s->channelCount : AAUDIO_ERROR_NULL;
}
aaudio_channel_mask_t AAudioStream_getChannelMask(AAudioStream* s) {
    return s ? s->channelMask : AAUDIO_UNSPECIFIED;
}
aaudio_direction_t AAudioStream_getDirection(AAudioStream* s) {
    return s ? s->direction : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getDeviceId(AAudioStream* s) {
    return s ? s->deviceId : AAUDIO_UNSPECIFIED;
}
aaudio_format_t AAudioStream_getFormat(AAudioStream* s) {
    return s ? s->format : AAUDIO_FORMAT_INVALID;
}
aaudio_sharing_mode_t AAudioStream_getSharingMode(AAudioStream* s) {
    return s ? s->sharingMode : AAUDIO_ERROR_NULL;
}
aaudio_performance_mode_t AAudioStream_getPerformanceMode(AAudioStream* s) {
    return s ? s->performanceMode : AAUDIO_ERROR_NULL;
}
int64_t AAudioStream_getFramesWritten(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    return s->framesWritten;
}
int64_t AAudioStream_getFramesRead(AAudioStream* s) {
    if (!s) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    return s->framesRead;
}
aaudio_result_t AAudioStream_getTimestamp(AAudioStream* s, clockid_t clockid,
                                          int64_t* framePosition, int64_t* timeNanoseconds) {
    if (!s || !framePosition || !timeNanoseconds) {
        return AAUDIO_ERROR_NULL;
    }
    std::lock_guard<std::mutex> lock(s->mu);
    if (s->state != AAUDIO_STREAM_STATE_STARTED && s->state != AAUDIO_STREAM_STATE_PAUSED) {
        return AAUDIO_ERROR_INVALID_STATE;
    }
    *framePosition = (s->direction == AAUDIO_DIRECTION_OUTPUT) ? s->framesRead : s->framesWritten;
    *timeNanoseconds = nowNanos(clockid);
    return AAUDIO_OK;
}
aaudio_session_id_t AAudioStream_getSessionId(AAudioStream* s) {
    return s ? s->sessionId : AAUDIO_SESSION_ID_NONE;
}
aaudio_usage_t AAudioStream_getUsage(AAudioStream* s) {
    return s ? s->usage : AAUDIO_ERROR_NULL;
}
aaudio_content_type_t AAudioStream_getContentType(AAudioStream* s) {
    return s ? s->contentType : AAUDIO_ERROR_NULL;
}
aaudio_input_preset_t AAudioStream_getInputPreset(AAudioStream* s) {
    return s ? s->inputPreset : AAUDIO_ERROR_NULL;
}
bool AAudioStream_isPrivacySensitive(AAudioStream* s) {
    return s ? s->privacySensitive : false;
}
aaudio_allowed_capture_policy_t AAudioStream_getAllowedCapturePolicy(AAudioStream* s) {
    return s ? s->allowedCapturePolicy : AAUDIO_ERROR_NULL;
}
aaudio_spatialization_behavior_t AAudioStream_getSpatializationBehavior(AAudioStream* s) {
    return s ? s->spatializationBehavior : AAUDIO_ERROR_NULL;
}
bool AAudioStream_isContentSpatialized(AAudioStream* s) {
    return s ? s->isContentSpatialized : false;
}
int32_t AAudioStream_getHardwareChannelCount(AAudioStream* s) {
    return s ? s->channelCount : AAUDIO_ERROR_NULL;
}
int32_t AAudioStream_getHardwareSampleRate(AAudioStream* s) {
    return s ? s->sampleRate : AAUDIO_ERROR_NULL;
}
aaudio_format_t AAudioStream_getHardwareFormat(AAudioStream* s) {
    return s ? s->format : AAUDIO_FORMAT_INVALID;
}

} // extern "C"
