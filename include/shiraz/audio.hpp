// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace shiraz::audio {
constexpr std::size_t maxEncodedBytes = 16 * 1024 * 1024;
// SoundPool is for short effects. Bound decoded storage to 1 MiB of PCM16.
constexpr std::size_t maxDecodedSamples = 1024 * 1024 / 2;
struct Clip {
    int rate = 0, channels = 0;
    std::vector<float> samples;
    std::size_t frames() const { return channels > 0 ? samples.size() / channels : 0; }
};
std::shared_ptr<const Clip> decode(const std::vector<std::uint8_t>& encoded);

// The output worker owns synchronization; no device or JNI state belongs here.
class Mixer {
public:
    explicit Mixer(int maxStreams, int outputRate = 48000);
    int play(std::shared_ptr<const Clip> clip, float left, float right, int priority, int loops, float rate);
    void stop(int id);
    void pause(int id);
    void resume(int id);
    void autoPause();
    void autoResume();
    void volume(int id, float left, float right);
    void rate(int id, float rate);
    void loop(int id, int loops);
    void priority(int id, int priority);
    void clear();
    void render(float* stereo, std::size_t frames);
    std::size_t active() const { return voices_.size(); }
    bool runnable() const;
private:
    struct Voice {
        int id, priority, loops;
        std::shared_ptr<const Clip> clip;
        double position = 0;
        float left, right, rate;
        bool paused = false, autoPaused = false;
    };
    Voice* find(int id);
    int maxStreams_, outputRate_;
    std::int64_t nextId_ = 1;
    std::vector<Voice> voices_;
};
}
