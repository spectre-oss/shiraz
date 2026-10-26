// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/audio.hpp"
#include <condition_variable>
#include <mutex>
#include <thread>
#include <unordered_map>
struct _snd_pcm;
namespace shiraz::audio {
class Pool {
public:
    explicit Pool(int streams);
    ~Pool();
    bool reserve(int sound);
    bool load(int sound, const std::vector<std::uint8_t>& encoded);
    bool unload(int sound);
    int play(int sound, float left, float right, int priority, int loops, float rate);
    void control(int stream, int operation, float a, float b, int value);
    void release();
private:
    void output();
    bool openDevice();
    std::mutex mutex_, releaseMutex_;
    std::condition_variable changed_;
    Mixer mixer_;
    std::unordered_map<int, std::shared_ptr<const Clip>> sounds_;
    std::size_t decodedSamples_ = 0;
    bool closed_ = false, failed_ = false;
    _snd_pcm* pcm_ = nullptr;
    std::thread worker_;
};
}
