// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio.hpp"
#include <cassert>
#include <cmath>
#include <fstream>
#include <iostream>
#include <iterator>
#include <limits>
#include <stdexcept>
using namespace shiraz::audio;
namespace {
void near(float actual, float expected) { if (std::abs(actual - expected) > .00001f) throw std::runtime_error("Incorrect mixed sample"); }
template<class F> void invalid(F function) {
    try { function(); } catch (const std::invalid_argument&) { return; }
    throw std::runtime_error("Invalid audio accepted");
}
std::vector<std::uint8_t> wave() {
    std::vector<std::uint8_t> result;
    auto text = [&](const char* value) { for (int i = 0; i < 4; ++i) result.push_back(value[i]); };
    auto number = [&](unsigned value, int count) { for (int i = 0; i < count; ++i) result.push_back((value >> (8 * i)) & 255); };
    text("RIFF"); number(44, 4); text("WAVE"); text("fmt "); number(16, 4); number(1, 2); number(1, 2);
    number(48000, 4); number(96000, 4); number(2, 2); number(16, 2); text("data"); number(8, 4);
    number(0, 2); number(8192, 2); number(16384, 2); number(0xe000, 2); return result;
}
std::shared_ptr<const Clip> clip(std::vector<float> values, int channels = 1, int rate = 48000) {
    auto result = std::make_shared<Clip>(); result->rate = rate; result->channels = channels; result->samples = std::move(values); return result;
}
}
int main(int argc, char** argv) {
    auto decoded = decode(wave()); assert(decoded->channels == 1 && decoded->rate == 48000 && decoded->frames() == 4);
    near(decoded->samples[1], .25f); near(decoded->samples[2], .5f); near(decoded->samples[3], -.25f);
    invalid([] { decode({}); }); invalid([] { decode(std::vector<std::uint8_t>(100, 0)); });
    invalid([] { decode(std::vector<std::uint8_t>(maxEncodedBytes + 1)); });
    auto oversized = wave(); oversized.resize(44 + (maxDecodedSamples + 1) * 2);
    for (int i = 0; i < 4; ++i) {
        oversized[4 + i] = ((oversized.size() - 8) >> (8 * i)) & 255;
        oversized[40 + i] = ((oversized.size() - 44) >> (8 * i)) & 255;
    }
    invalid([&] { decode(oversized); });
    auto multichannel = wave(); multichannel[22] = 3; invalid([&] { decode(multichannel); });
    invalid([] { Mixer mixer(0); }); invalid([] { Mixer mixer(65); });
    float output[32]; Mixer mixer(2);
    int first = mixer.play(decoded, 1, .5f, 3, 0, 1); assert(first > 0);
    mixer.render(output, 6); near(output[2], .25f); near(output[3], .125f); near(output[4], .5f); near(output[6], -.25f);
    for (int i = 8; i < 12; ++i) near(output[i], 0);
    assert(mixer.active() == 0);
    int slow = mixer.play(clip({0, .5f}), 1, 1, 0, 0, .5f);
    mixer.render(output, 4); near(output[0], 0); near(output[2], .25f); near(output[4], .5f); near(output[6], .5f); assert(mixer.active() == 0);
    mixer.stop(slow); // stale IDs are harmless and cannot control later voices.
    int loop = mixer.play(clip({.25f, -.25f}), 1, 1, 0, 2, 1);
    mixer.render(output, 7);
    for (int i = 0; i < 6; ++i) near(output[i * 2], i % 2 ? -.25f : .25f);
    near(output[12], 0); assert(mixer.active() == 0); mixer.stop(loop);
    int persistent = mixer.play(clip({.25f}), 1, 1, 1, -1, 1);
    int manual = mixer.play(clip({.5f}), 1, 1, 1, -1, 1);
    mixer.pause(manual); mixer.autoPause(); mixer.autoPause(); mixer.render(output, 2); near(output[0], 0);
    mixer.autoResume(); mixer.render(output, 2); near(output[0], .25f);
    mixer.resume(manual); mixer.render(output, 2); near(output[0], .75f);
    assert(mixer.play(decoded, 1, 1, 0, 0, 1) == 0); assert(mixer.active() == 2);
    int replacement = mixer.play(clip({-.5f}), .5f, 1, 1, -1, 1); assert(replacement > manual);
    mixer.stop(persistent); mixer.render(output, 1); near(output[0], .25f); near(output[1], 0);
    mixer.priority(manual, 9); mixer.volume(manual, 0, 0); mixer.pause(replacement); mixer.render(output, 1); near(output[0], 0);
    mixer.stop(manual); mixer.resume(replacement); mixer.loop(replacement, 0); mixer.render(output, 1); near(output[0], -.25f); assert(mixer.active() == 0);
    mixer.play(clip({1, -.5f, .5f, -1}, 2), 1, 1, 0, 0, 1); mixer.render(output, 2); near(output[0], 1); near(output[1], -.5f); near(output[2], .5f); near(output[3], -1);
    mixer.play(clip({.5f}), 1, 1, 0, -1, 1); mixer.play(clip({.75f}), 1, 1, 0, -1, 1);
    mixer.render(output, 1); near(output[0], 1); mixer.clear(); assert(mixer.active() == 0);
    mixer.play(clip({0, .25f, .5f, .75f}, 1, 96000), 1, 1, 0, 0, 1); mixer.render(output, 2); near(output[0], 0); near(output[2], .5f); assert(mixer.active() == 0);
    invalid([&] { mixer.play(decoded, -1, 1, 0, 0, 1); });
    invalid([&] { mixer.play(decoded, 1, 1, 0, -2, 1); });
    invalid([&] { mixer.play(decoded, 1, 1, 0, 0, std::numeric_limits<float>::quiet_NaN()); });
    invalid([&] { mixer.play(clip({std::numeric_limits<float>::infinity()}), 1, 1, 0, 0, 1); });
    invalid([&] { mixer.play(clip({}), 1, 1, 0, 0, 1); });
    invalid([&] { mixer.render(output, 16385); });
    mixer.play(clip({.25f}, 1, 192000), 1, 1, 0, 3, 2);
    mixer.render(output, 2); near(output[0], .25f); near(output[2], 0); assert(mixer.active() == 0);
    int adjusted = mixer.play(clip({0, .25f, .5f, .75f}), 1, 1, 0, 0, 1);
    mixer.render(output, 1); mixer.rate(adjusted, 2); mixer.render(output, 2);
    near(output[0], .25f); near(output[2], .75f); assert(mixer.active() == 0);
    mixer.play(clip({0, 1}), 1, 1, 0, 1, .5f); mixer.render(output, 8);
    const float wrapped[] = {0, .5f, 1, .5f, 0, .5f, 1, 1};
    for (int i = 0; i < 8; ++i) near(output[2 * i], wrapped[i]);
    assert(mixer.active() == 0);
    std::cout << "AUDIO_DECODE_MIX_OK\n";
    if (argc == 2) {
        std::ifstream input(argv[1], std::ios::binary); if (!input) throw std::runtime_error("Cannot open probe input");
        auto real = decode(std::vector<std::uint8_t>(std::istreambuf_iterator<char>(input), {}));
        float peak = 0; for (float sample : real->samples) peak = std::max(peak, std::abs(sample));
        std::cout << "AUDIO_FILE_DECODE frames=" << real->frames() << " channels=" << real->channels << " rate=" << real->rate << " peak=" << peak << '\n';
    }
}
