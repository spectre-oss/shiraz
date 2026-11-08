// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio_output.hpp"
#include <AudioToolbox/AudioToolbox.h>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <stdexcept>

namespace shiraz::audio {
struct AudioDevice {
    static constexpr UInt32 frames = 480, bytes = frames * 2 * sizeof(float);
    AudioQueueRef queue = nullptr;
    std::array<AudioQueueBufferRef, 2> available{};
    size_t count = 0;
    std::mutex mutex;
    std::atomic<bool> closing{false};
    bool failed = false;
    ~AudioDevice() {
        closing = true;
        if (queue) {
            AudioQueueStop(queue, true);
            // Synchronous disposal waits for callbacks before freeing this
            // callback context and all AudioQueue-owned buffers.
            AudioQueueDispose(queue, true);
        }
    }
    static void consumed(void *context, AudioQueueRef, AudioQueueBufferRef buffer) noexcept {
        auto &device = *static_cast<AudioDevice *>(context);
        if (device.closing) return;
        std::lock_guard<std::mutex> lock(device.mutex);
        if (device.count == device.available.size()) { device.failed = true; return; }
        device.available[device.count++] = buffer;
    }
    AudioQueueBufferRef take() {
        std::lock_guard<std::mutex> lock(mutex);
        return !failed && count ? available[--count] : nullptr;
    }
    bool broken() { std::lock_guard<std::mutex> lock(mutex); return failed; }
};
Pool::Pool(int streams) : mixer_(streams) {}
Pool::~Pool() { release(); }
bool Pool::reserve(int sound) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (closed_ || sound < 1 || sounds_.size() >= 64) return false;
    return sounds_.emplace(sound, nullptr).second;
}
bool Pool::load(int sound, const std::vector<std::uint8_t>& encoded) {
    auto clip = decode(encoded);
    std::lock_guard<std::mutex> lock(mutex_);
    auto entry = sounds_.find(sound);
    if (closed_ || entry == sounds_.end() || entry->second || decodedSamples_ + clip->samples.size() > 8 * 1024 * 1024) return false;
    decodedSamples_ += clip->samples.size(); entry->second = std::move(clip); return true;
}
bool Pool::unload(int sound) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto entry = sounds_.find(sound); if (entry == sounds_.end()) return false;
    if (entry->second) decodedSamples_ -= entry->second->samples.size();
    sounds_.erase(entry); return true;
}
bool Pool::openDevice() {
    if (device_) return true;
    auto device = std::make_unique<AudioDevice>();
    AudioStreamBasicDescription format{};
    format.mSampleRate = 48000;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian;
    format.mBytesPerPacket = format.mBytesPerFrame = 2 * sizeof(float);
    format.mFramesPerPacket = 1; format.mChannelsPerFrame = 2; format.mBitsPerChannel = 32;
    OSStatus status = AudioQueueNewOutput(&format, AudioDevice::consumed, device.get(), nullptr, nullptr, 0, &device->queue);
    const char *name = std::getenv("SHIRAZ_AUDIO_DEVICE");
    if (status == noErr && name && *name && std::strcmp(name, "default")) {
        // CoreAudio identifies devices by their UID, never a synthetic sink.
        CFStringRef uid = CFStringCreateWithCString(kCFAllocatorDefault, name, kCFStringEncodingUTF8);
        if (!uid) status = kAudio_ParamError;
        else {
            status = AudioQueueSetProperty(device->queue, kAudioQueueProperty_CurrentDevice, &uid, sizeof(uid));
            CFRelease(uid);
        }
    }
    for (size_t i = 0; status == noErr && i < device->available.size(); ++i) {
        AudioQueueBufferRef buffer = nullptr;
        status = AudioQueueAllocateBuffer(device->queue, AudioDevice::bytes, &buffer);
        if (status == noErr) device->available[device->count++] = buffer;
    }
    if (status == noErr) {
        // Prime only one10ms buffer so opening/starting verifies real output
        // before play reports a stream ID; the worker then queues actual PCM.
        auto buffer = device->take();
        std::memset(buffer->mAudioData, 0, AudioDevice::bytes);
        buffer->mAudioDataByteSize = AudioDevice::bytes;
        status = AudioQueueEnqueueBuffer(device->queue, buffer, 0, nullptr);
        if (status == noErr) status = AudioQueueStart(device->queue, nullptr);
    }
    if (status != noErr) {
        std::fprintf(stderr, "Shiraz audio: output unavailable (CoreAudio %d)\n", int(status));
        return false;
    }
    device_ = device.release();
    try { worker_ = std::thread(&Pool::output, this); }
    catch (...) { delete device_; device_ = nullptr; throw; }
    return true;
}
int Pool::play(int sound, float left, float right, int priority, int loops, float rate) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto entry = sounds_.find(sound);
    if (closed_ || failed_ || entry == sounds_.end() || !entry->second) return 0;
    if (!openDevice()) return 0;
    int stream = mixer_.play(entry->second, left, right, priority, loops, rate);
    changed_.notify_all(); return stream;
}
void Pool::control(int stream, int operation, float a, float b, int value) {
    std::lock_guard<std::mutex> lock(mutex_); if (closed_) return;
    switch (operation) {
        case 0: mixer_.stop(stream); break; case 1: mixer_.pause(stream); break; case 2: mixer_.resume(stream); break;
        case 3: mixer_.autoPause(); break; case 4: mixer_.autoResume(); break;
        case 5: mixer_.volume(stream, a, b); break; case 6: mixer_.rate(stream, a); break;
        case 7: mixer_.loop(stream, value); break; case 8: mixer_.priority(stream, value); break;
        default: throw std::invalid_argument("Unknown sound stream control");
    }
    // A maximum20ms of already mixed PCM can remain. Clearing the queue here
    // would incorrectly cut other active voices sharing the output device.
    changed_.notify_all();
}
void Pool::release() {
    std::lock_guard<std::mutex> teardown(releaseMutex_);
    { std::lock_guard<std::mutex> lock(mutex_); closed_ = true; mixer_.clear(); sounds_.clear(); decodedSamples_ = 0; }
    changed_.notify_all();
    if (worker_.joinable()) worker_.join();
    delete device_; device_ = nullptr;
}
void Pool::output() {
    using Clock = std::chrono::steady_clock;
    std::unique_lock<std::mutex> lock(mutex_);
    auto deadline = Clock::now() + std::chrono::seconds(2);
    while (!closed_) {
        // Keep the real device supplied with bounded silence between effects;
        // this avoids an underrun invalidating later SoundPool play requests.
        // No device is opened until the first actual play, and release stops it.
        auto buffer = device_->take();
        if (!buffer) {
            if (device_->broken() || Clock::now() >= deadline) {
                std::fprintf(stderr, "Shiraz audio: output stopped consuming queued PCM\n");
                failed_ = true; mixer_.clear(); break;
            }
            changed_.wait_for(lock, std::chrono::milliseconds(2), [&] { return closed_; });
            continue;
        }
        deadline = Clock::now() + std::chrono::seconds(2);
        mixer_.render(static_cast<float *>(buffer->mAudioData), AudioDevice::frames);
        buffer->mAudioDataByteSize = AudioDevice::bytes;
        OSStatus status = AudioQueueEnqueueBuffer(device_->queue, buffer, 0, nullptr);
        if (status != noErr) {
            std::fprintf(stderr, "Shiraz audio: PCM queue failed (CoreAudio %d)\n", int(status));
            failed_ = true; mixer_.clear(); break;
        }
    }
}
}
