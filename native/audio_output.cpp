// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio_output.hpp"
#include <alsa/asoundlib.h>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <stdexcept>
namespace shiraz::audio {
Pool::Pool(int streams) : mixer_(streams) { }
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
    if (pcm_) return true;
    const char* device = std::getenv("SHIRAZ_AUDIO_DEVICE"); if (!device || !*device) device = "default";
    int status = snd_pcm_open(&pcm_, device, SND_PCM_STREAM_PLAYBACK, SND_PCM_NONBLOCK);
    if (status >= 0) status = snd_pcm_set_params(pcm_, SND_PCM_FORMAT_FLOAT_LE, SND_PCM_ACCESS_RW_INTERLEAVED, 2, 48000, 1, 40000);
    if (status < 0) {
        std::fprintf(stderr, "Shiraz audio: output unavailable: %s\n", snd_strerror(status));
        if (pcm_) { snd_pcm_close(pcm_); pcm_ = nullptr; }
        return false;
    }
    try { worker_ = std::thread(&Pool::output, this); }
    catch (...) { snd_pcm_close(pcm_); pcm_ = nullptr; throw; }
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
    changed_.notify_all();
}
void Pool::release() {
    // Serializes concurrent releases and joins without holding the mixer mutex.
    std::lock_guard<std::mutex> teardown(releaseMutex_);
    { std::lock_guard<std::mutex> lock(mutex_); closed_ = true; mixer_.clear(); sounds_.clear(); decodedSamples_ = 0; }
    changed_.notify_all(); if (worker_.joinable()) worker_.join();
    if (pcm_) { snd_pcm_drop(pcm_); snd_pcm_close(pcm_); pcm_ = nullptr; }
}
void Pool::output() {
    using Clock = std::chrono::steady_clock;
    std::unique_lock<std::mutex> lock(mutex_);
    float block[480 * 2];
    while (!closed_) {
        changed_.wait(lock, [&] { return closed_ || mixer_.runnable(); });
        if (closed_) break;
        const auto next = Clock::now() + std::chrono::milliseconds(10);
        mixer_.render(block, 480);
        lock.unlock();
        std::size_t done = 0; int failure = 0;
        const auto timeout = Clock::now() + std::chrono::seconds(2);
        while (done < 480) {
            auto count = snd_pcm_writei(pcm_, block + done * 2, 480 - done);
            if (count > 0) done += static_cast<std::size_t>(count);
            else if (count == -EPIPE || count == -ESTRPIPE) { failure = snd_pcm_prepare(pcm_); if (failure < 0) break; }
            else if (count != -EAGAIN && count != 0 && count != -EINTR) { failure = static_cast<int>(count); break; }
            lock.lock();
            if (closed_) break;
            if (Clock::now() >= timeout) { failure = -ETIMEDOUT; break; }
            if (count <= 0) changed_.wait_for(lock, std::chrono::milliseconds(5), [&] { return closed_; });
            if (closed_) break;
            lock.unlock();
        }
        if (!lock.owns_lock()) lock.lock();
        if (failure < 0) {
            std::fprintf(stderr, "Shiraz audio: playback failed: %s\n", snd_strerror(failure));
            failed_ = true; mixer_.clear(); break;
        }
        // Also pace non-hardware sinks; a null/file PCM must not run infinite loops at CPU speed.
        changed_.wait_until(lock, next, [&] { return closed_; });
    }
}
}
