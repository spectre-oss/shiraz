// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/audio_output.hpp"
#include <cassert>
#include <chrono>
#include <fstream>
#include <iostream>
#include <iterator>
#include <thread>
#include <filesystem>
using namespace shiraz::audio;
std::size_t descriptors() { return static_cast<std::size_t>(std::distance(std::filesystem::directory_iterator("/proc/self/fd"), std::filesystem::directory_iterator{})); }
int main(int argc, char** argv) {
    assert(argc == 3);
    std::ifstream input(argv[1], std::ios::binary); assert(input);
    std::vector<std::uint8_t> bytes(std::istreambuf_iterator<char>(input), {});
    const bool unavailable = std::string(argv[2]) == "unavailable";
    auto before = descriptors();
    {
        Pool pool(2);
        assert(pool.reserve(1)); assert(!pool.reserve(1)); assert(pool.play(1, 1, 1, 1, 0, 1) == 0);
        assert(pool.load(1, bytes));
        assert(pool.reserve(2)); assert(pool.unload(2)); assert(!pool.load(2, bytes));
        int stream = pool.play(1, 1, .5f, 1, 0, 1);
        assert(unavailable ? stream == 0 : stream > 0);
        if (!unavailable) std::this_thread::sleep_for(std::chrono::milliseconds(180));
        assert(pool.unload(1)); assert(!pool.unload(1));
        pool.release(); pool.release(); assert(!pool.reserve(3)); assert(pool.play(1, 1, 1, 1, 0, 1) == 0);
    }
    if (!unavailable && std::string(argv[2]) != "single") {
        // Repeated close interrupts an indefinitely looping effect without leaking a device or worker.
        for (int i = 0; i < 16; ++i) {
            Pool pool(1); assert(pool.reserve(1) && pool.load(1, bytes));
            assert(pool.play(1, 1, 1, 0, -1, 1) > 0);
            pool.control(0, 3, 0, 0, 0); pool.control(0, 4, 0, 0, 0);
            pool.release();
        }
    }
    assert(descriptors() == before);
    std::cout << (unavailable ? "AUDIO_DEVICE_FAILURE_OK\n" : "AUDIO_OUTPUT_OWNERSHIP_OK\n");
}
