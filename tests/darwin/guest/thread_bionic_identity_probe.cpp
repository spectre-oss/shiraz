// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <filesystem>
#include <iostream>
#include <map>
#include <mutex>
#include <thread>
#include <vector>
#include <unistd.h>
using namespace shiraz::guest;

int main(int argc, char** argv) {
    try {
        if (argc != 2 && !(argc == 3 && std::string(argv[2]) == "--stress-join"))
            throw std::runtime_error("fixture directory and optional --stress-join required");
        const bool stress = argc == 3;
        if (stress)
            alarm(30);
        Loader* resident = nullptr;
        auto providers = native_personality(
            [&](const void* code) { return resident && resident->contains_code(code); });
        Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers));
        resident = &loader;
        auto* module = loader.open("libthread.so");
        auto* entry = reinterpret_cast<void*>(
            loader.symbol(module, "inspect_bionic_thread_identity").address);
        std::atomic<unsigned> passed{0}, join_failures{0};
        std::mutex mutex;
        std::map<unsigned, unsigned> masks;
        auto run = [&] {
            unsigned mask = unsigned(invoke(entry, {}).result_integers[0]);
            {
                std::lock_guard<std::mutex> lock(mutex);
                ++masks[mask];
            }
            if (mask == 1023)
                ++passed;
            if ((mask & 12) != 12)
                ++join_failures;
        };
        run();
        std::vector<std::thread> workers;
        for (int i = 0; i < 4; ++i)
            workers.emplace_back([&] {
                for (int iteration = 0; iteration < (stress ? 200 : 1); ++iteration)
                    run();
            });
        for (auto& worker : workers)
            worker.join();
        if (stress) {
            if (join_failures)
                throw std::runtime_error("concurrent create/join failures=" +
                                         std::to_string(join_failures.load()));
            alarm(0);
            std::cout << "M5_THREAD_HANDLE_STRESS_PASS threads=4 joins=801 identity_claim=0\n";
            return 0;
        }
        for (const auto& entry : masks)
            std::cout << "M5_BIONIC_IDENTITY_OBSERVED mask=" << entry.first
                      << " cases=" << entry.second << '\n';
        if (passed != 5) {
            std::cerr << "M5_BIONIC_IDENTITY_UNSUPPORTED expected=1023 passed=" << passed << "/5\n";
            return 1;
        }
        std::cout
            << "M5_BIONIC_IDENTITY_PASS parent_and_child=1 distinct=1 fields=1 tid=1 workers=4\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
