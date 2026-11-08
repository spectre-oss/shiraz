// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio_output.hpp"
#include <chrono>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <thread>
using namespace shiraz::audio;
static void require(bool value, const char *message) { if (!value) throw std::runtime_error(message); }
int main(int argc, char **argv) {
    try {
        require(argc == 3, "usage: audio-output sound-file available|unavailable");
        std::ifstream input(argv[1], std::ios::binary);
        require(bool(input), "open actual encoded sound");
        std::vector<uint8_t> bytes(std::istreambuf_iterator<char>(input), {});
        bool unavailable = std::string(argv[2]) == "unavailable";
        {
            Pool pool(2);
            require(pool.reserve(1) && !pool.reserve(1), "sound reservation");
            require(pool.load(1, bytes), "actual codec load");
            int stream = pool.play(1, 0.15f, 0.15f, 0, 0, 1);
            require(unavailable ? stream == 0 : stream > 0, "real output availability matches result");
            if (!unavailable) {
                std::this_thread::sleep_for(std::chrono::milliseconds(120));
                pool.control(stream, 1, 0, 0, 0);
                pool.control(stream, 2, 0, 0, 0);
                pool.control(stream, 5, 0.1f, 0.2f, 0);
                pool.control(stream, 6, 0.5f, 0, 0);
                pool.control(stream, 0, 0, 0, 0);
                // Exceed the backend's stalled-consumption timeout and verify
                // a second real request is still accepted after the idle gap.
                std::this_thread::sleep_for(std::chrono::milliseconds(2100));
                int second = pool.play(1, 0.05f, 0.05f, 0, 0, 1);
                require(second > 0, "output remains healthy after idle consumption");
                pool.control(second, 0, 0, 0, 0);
            }
            require(pool.unload(1), "loaded sound ownership");
            pool.release(); pool.release();
            require(!pool.reserve(2), "released pool stays closed");
        }
        if (!unavailable) {
            for (int i = 0; i < 4; ++i) {
                Pool pool(1);
                require(pool.reserve(1) && pool.load(1, bytes), "repeated real codec load");
                require(pool.play(1, 0.05f, 0.05f, 0, -1, 1) > 0, "real looping output");
                std::this_thread::sleep_for(std::chrono::milliseconds(20));
                pool.release();
            }
        }
        std::cout << (unavailable ? "MACOS_AUDIO_DEVICE_REJECTION_PASS\n" : "MACOS_AUDIO_OUTPUT_PASS real_driver=1 controls=1 release=1\n");
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "macos-audio-output: " << error.what() << '\n'; return 1;
    }
}
