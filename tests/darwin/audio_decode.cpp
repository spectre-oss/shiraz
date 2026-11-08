// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio.hpp"
#include <algorithm>
#include <atomic>
#include <cmath>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <thread>
using namespace shiraz::audio;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
static void reject(const std::vector<uint8_t>& bytes) {
    try { decode(bytes); } catch (const std::invalid_argument&) { return; }
    throw std::runtime_error("Malformed audio accepted");
}
int main(int argc, char **argv) {
    try {
        require(argc >= 2, "usage: audio-decode sound-file [...]");
        std::vector<std::vector<uint8_t>> inputs;
        size_t oggCases = 0;
        for (int i = 1; i < argc; ++i) {
            std::ifstream file(argv[i], std::ios::binary);
            require(bool(file), "open real audio input");
            inputs.emplace_back(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
            auto clip = decode(inputs.back());
            require(clip->frames() && clip->samples.size() <= maxDecodedSamples, "bounded decoded shape");
            float peak = 0;
            for (float sample : clip->samples) {
                require(std::isfinite(sample) && std::abs(sample) <= 1, "finite normalized samples");
                peak = std::max(peak, std::abs(sample));
            }
            require(peak > 0, "actual sound contains nonzero decoded PCM");
            std::cout << "MACOS_AUDIO_DECODE file=" << argv[i] << " rate=" << clip->rate
                      << " channels=" << clip->channels << " frames=" << clip->frames() << " peak=" << peak << '\n';
            if (inputs.back().size() >= 4 && inputs.back()[0] == 'O' && inputs.back()[1] == 'g') {
                ++oggCases;
                auto truncated = inputs.back(); truncated.pop_back(); reject(truncated);
                auto corrupt = inputs.back(); corrupt[corrupt.size() / 2] ^= 1; reject(corrupt);
            }
        }
        reject({}); reject(std::vector<uint8_t>(100, 0));
        std::atomic<bool> passed{true};
        std::vector<std::thread> workers;
        for (int i = 0; i < 4; ++i) workers.emplace_back([&] {
            try { for (auto &bytes : inputs) if (!decode(bytes)->frames()) passed = false; }
            catch (...) { passed = false; }
        });
        for (auto &worker : workers) worker.join();
        require(passed, "concurrent real codec decode");
        std::cout << "MACOS_AUDIO_DECODE_PASS malformed=1 truncated_ogg=" << oggCases
                  << " crc_ogg=" << oggCases << " concurrent=4\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "macos-audio-decode: " << error.what() << '\n'; return 1;
    }
}
