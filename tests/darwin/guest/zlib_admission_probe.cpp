// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/zlib.hpp"
#include <atomic>
#include <cstdlib>
#include <filesystem>
#include <iostream>
#include <thread>
#include <vector>
using namespace shiraz::guest;
static std::atomic<unsigned> routed{0};
extern "C" uintptr_t zlib_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        ++routed;
        return domain->route(site, target, sp).target;
    } catch (...) {
        std::abort();
    }
}
int main(int argc, char** argv) {
    try {
        if (argc != 2)
            throw std::runtime_error("fixture directory required");
        Loader* owner = nullptr;
        auto providers = core_personality();
        providers.add(
            zlib_provider([&](const void* p) { return owner && owner->contains_code(p); }));
        Loader loader({std::filesystem::canonical(argv[1])}, std::move(providers),
                      Loader::Mode::AdmissionFixture, uintptr_t(&zlib_validate));
        owner = &loader;
        auto* module = loader.open("libzlib-fixture.so");
        auto domain = loader.admission_domain();
        auto* entry = reinterpret_cast<void*>(loader.symbol(module, "run_zlib").address);
        auto result = invoke_admitted(domain, entry, {}).result_integers[0];
        if (result)
            throw std::runtime_error("guest zlib failed at line " + std::to_string(result));
        std::atomic<bool> passed{true};
        std::vector<std::thread> threads;
        for (int i = 0; i < 4; ++i)
            threads.emplace_back([&] {
                for (int j = 0; j < 4; ++j)
                    if (invoke_admitted(domain, entry, {}).result_integers[0])
                        passed = false;
            });
        for (auto& thread : threads)
            thread.join();
        if (!passed || !routed)
            throw std::runtime_error("admitted concurrent zlib callbacks failed");
        std::cout
            << "M5_ZLIB_ADMISSION_PASS default_init=1 guest_callbacks=1 roundtrip=1 threads=4\n";
    } catch (const std::exception& error) {
        std::cerr << "zlib-admission-probe: " << error.what() << '\n';
        return 1;
    }
}
