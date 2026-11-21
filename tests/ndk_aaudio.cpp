// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N4: libaaudio.so (65/65 symbols).
#include "shiraz/ndk/aaudio.h"

#include <atomic>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

namespace {

struct CallbackState {
    std::atomic<int> calls{0};
    std::atomic<int64_t> frames{0};
};

aaudio_data_callback_result_t sineCallback(AAudioStream*, void* userData, void* audioData,
                                           int32_t numFrames) {
    auto* st = static_cast<CallbackState*>(userData);
    auto* out = static_cast<float*>(audioData);
    for (int32_t i = 0; i < numFrames * 2; ++i) {
        out[i] = std::sin(static_cast<float>(i) * 0.05f) * 0.25f;
    }
    st->frames.fetch_add(numFrames);
    const int n = st->calls.fetch_add(1) + 1;
    return (n >= 3) ? AAUDIO_CALLBACK_RESULT_STOP : AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void dummyErrorCallback(AAudioStream*, void*, aaudio_result_t) {}

void testBuilderAndBlockingWrite() {
    assert(std::strcmp(AAudio_convertResultToText(AAUDIO_OK), "AAUDIO_OK") == 0);
    assert(std::strcmp(AAudio_convertStreamStateToText(AAUDIO_STREAM_STATE_STARTED),
                       "AAUDIO_STREAM_STATE_STARTED") == 0);

    AAudioStreamBuilder* b = nullptr;
    assert(AAudio_createStreamBuilder(&b) == AAUDIO_OK && b != nullptr);
    AAudioStreamBuilder_setDeviceId(b, 7);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    AAudioStreamBuilder_setChannelMask(b, AAUDIO_CHANNEL_STEREO);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setBufferCapacityInFrames(b, 4096);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setInputPreset(b, AAUDIO_INPUT_PRESET_GENERIC);
    AAudioStreamBuilder_setSpatializationBehavior(b, AAUDIO_SPATIALIZATION_BEHAVIOR_NEVER);
    AAudioStreamBuilder_setIsContentSpatialized(b, true);
    AAudioStreamBuilder_setAllowedCapturePolicy(b, AAUDIO_ALLOW_CAPTURE_BY_ALL);
    AAudioStreamBuilder_setSessionId(b, AAUDIO_SESSION_ID_ALLOCATE);
    AAudioStreamBuilder_setPrivacySensitive(b, true);
    AAudioStreamBuilder_setPackageName(b, "com.shiraz.test");
    AAudioStreamBuilder_setAttributionTag(b, "audio");
    AAudioStreamBuilder_setErrorCallback(b, dummyErrorCallback, nullptr);

    AAudioStream* s = nullptr;
    assert(AAudioStreamBuilder_openStream(b, &s) == AAUDIO_OK && s != nullptr);
    assert(AAudioStreamBuilder_delete(b) == AAUDIO_OK);

    assert(AAudioStream_getDeviceId(s) == 7);
    assert(AAudioStream_getSampleRate(s) == 48000);
    assert(AAudioStream_getChannelCount(s) == 2);
    assert(AAudioStream_getChannelMask(s) == AAUDIO_CHANNEL_STEREO);
    assert(AAudioStream_getFormat(s) == AAUDIO_FORMAT_PCM_I16);
    assert(AAudioStream_getSharingMode(s) == AAUDIO_SHARING_MODE_SHARED);
    assert(AAudioStream_getDirection(s) == AAUDIO_DIRECTION_OUTPUT);
    assert(AAudioStream_getPerformanceMode(s) == AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    assert(AAudioStream_getUsage(s) == AAUDIO_USAGE_MEDIA);
    assert(AAudioStream_getContentType(s) == AAUDIO_CONTENT_TYPE_MUSIC);
    assert(AAudioStream_getInputPreset(s) == AAUDIO_INPUT_PRESET_GENERIC);
    assert(AAudioStream_getSpatializationBehavior(s) == AAUDIO_SPATIALIZATION_BEHAVIOR_NEVER);
    assert(AAudioStream_isContentSpatialized(s));
    assert(AAudioStream_getAllowedCapturePolicy(s) == AAUDIO_ALLOW_CAPTURE_BY_ALL);
    assert(AAudioStream_getSessionId(s) >= 100);
    assert(AAudioStream_isPrivacySensitive(s));
    assert(AAudioStream_getHardwareSampleRate(s) == 48000);
    assert(AAudioStream_getHardwareChannelCount(s) == 2);
    assert(AAudioStream_getHardwareFormat(s) == AAUDIO_FORMAT_PCM_I16);

    assert(AAudioStream_getBufferCapacityInFrames(s) == 4096);
    assert(AAudioStream_getFramesPerBurst(s) == 128);
    assert(AAudioStream_setBufferSizeInFrames(s, 1024) == 1024);
    assert(AAudioStream_getBufferSizeInFrames(s) == 1024);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_OPEN);

    assert(AAudioStream_requestStart(s) == AAUDIO_OK);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_STARTED);

    std::vector<int16_t> pcm(512 * 2, 1234);
    const aaudio_result_t written = AAudioStream_write(s, pcm.data(), 512, 100000000LL);
    assert(written == 512);
    assert(AAudioStream_getFramesWritten(s) == 512);

    int64_t pos = -1, ts = 0;
    assert(AAudioStream_getTimestamp(s, CLOCK_MONOTONIC, &pos, &ts) == AAUDIO_OK);
    assert(ts > 0 && pos >= 0);

    assert(AAudioStream_requestPause(s) == AAUDIO_OK);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_PAUSED);
    assert(AAudioStream_requestFlush(s) == AAUDIO_OK);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_FLUSHED);
    assert(AAudioStream_requestStop(s) == AAUDIO_OK);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_STOPPED);
    assert(AAudioStream_getXRunCount(s) >= 0);

    assert(AAudioStream_close(s) == AAUDIO_OK);
}

void testCallbackPumpAndWaitForStateChange() {
    CallbackState cbState;
    AAudioStreamBuilder* b = nullptr;
    assert(AAudio_createStreamBuilder(&b) == AAUDIO_OK);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setDataCallback(b, sineCallback, &cbState);
    AAudioStreamBuilder_setFramesPerDataCallback(b, 128);

    AAudioStream* s = nullptr;
    assert(AAudioStreamBuilder_openStream(b, &s) == AAUDIO_OK);
    AAudioStreamBuilder_delete(b);
    assert(AAudioStream_getFramesPerDataCallback(s) == 128);

    assert(AAudioStream_requestStart(s) == AAUDIO_OK);
    aaudio_stream_state_t next = AAUDIO_STREAM_STATE_UNKNOWN;
    assert(AAudioStream_waitForStateChange(s, AAUDIO_STREAM_STATE_STARTED, &next, 2000000000LL) ==
           AAUDIO_OK);
    assert(next == AAUDIO_STREAM_STATE_STOPPED);
    assert(cbState.calls.load() == 3);
    assert(cbState.frames.load() == 384);
    assert(AAudioStream_getFramesWritten(s) == 384);
    assert(AAudioStream_getFramesRead(s) == 384);

    assert(AAudioStream_release(s) == AAUDIO_OK);
    assert(AAudioStream_getState(s) == AAUDIO_STREAM_STATE_CLOSED);
    assert(AAudioStream_close(s) == AAUDIO_OK);
}

void testInputStreamAndValidation() {
    AAudioStreamBuilder* b = nullptr;
    assert(AAudio_createStreamBuilder(&b) == AAUDIO_OK);
    AAudioStreamBuilder_setFormat(b, 999);
    AAudioStream* bad = nullptr;
    assert(AAudioStreamBuilder_openStream(b, &bad) == AAUDIO_ERROR_INVALID_FORMAT);
    assert(bad == nullptr);

    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(b, 48000);
    AAudioStreamBuilder_setChannelCount(b, 1);
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_INPUT);
    AAudioStream* in = nullptr;
    assert(AAudioStreamBuilder_openStream(b, &in) == AAUDIO_OK);
    AAudioStreamBuilder_delete(b);

    assert(AAudioStream_requestStart(in) == AAUDIO_OK);
    std::vector<int16_t> cap(128, 1);
    const aaudio_result_t n = AAudioStream_read(in, cap.data(), 128, 500000000LL);
    assert(n > 0);
    assert(AAudioStream_getFramesRead(in) == n);
    assert(AAudioStream_close(in) == AAUDIO_OK);
}

} // namespace

int main() {
    testBuilderAndBlockingWrite();
    testCallbackPumpAndWaitForStateChange();
    testInputStreamAndValidation();
    std::printf("ndk_aaudio: ALL TESTS PASSED\n");
    return 0;
}
