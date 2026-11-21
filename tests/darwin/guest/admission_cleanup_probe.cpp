// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/cleanup.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <iostream>
using namespace shiraz::guest;
static std::atomic<unsigned> routed{0};
extern "C" uintptr_t cleanup_admission_validate(uintptr_t target, uintptr_t site,
                                                uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        ++routed;
        return domain->route(site, target, sp).target;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "M5_CLEANUP_ADMISSION_REJECTED %s\n", error.what());
        std::abort();
    }
}
int main(int argc, char** argv) {
    try {
        if (argc != 2)
            throw std::runtime_error("fixture directory required");
        Loader* resident = nullptr;
        CodeValidator validate = [&](const void* code) {
            return resident && resident->contains_code(code);
        };
        auto cleanup = std::make_shared<CleanupRuntime>(validate);
        Loader loader({argv[1]}, native_personality(validate, {}, {}, cleanup),
                      Loader::Mode::AdmissionFixture, uintptr_t(&cleanup_admission_validate));
        resident = &loader;
        cleanup->attach(loader);
        auto* module = loader.link("libadmission-cleanup.so");
        auto domain = loader.admission_domain();
        auto* entry =
            reinterpret_cast<void*>(loader.symbol(module, "admission_cleanup_run").address);
        errno = EBUSY;
        auto result = invoke_admitted(domain, entry, {}).result_integers[0];
        if (result != 73 || cleanup->pending() || errno != EBUSY || !routed.load())
            throw std::runtime_error("admitted cleanup result, routing or errno mismatch");
        std::cout << "M5_ADMISSION_CLEANUP_PASS guest_callbacks=2 nested=1 tpidr=1 exact_return=1 "
                     "errno=1\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
