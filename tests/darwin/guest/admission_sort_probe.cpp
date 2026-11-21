// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <iostream>
using namespace shiraz::guest;
static std::atomic<unsigned> routed{0};
extern "C" uintptr_t sort_admission_validate(uintptr_t target, uintptr_t site,
                                             uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        ++routed;
        return domain->route(site, target, sp).target;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "M5_SORT_ADMISSION_REJECTED %s\n", error.what());
        std::abort();
    }
}
int main(int argc, char** argv) {
    try {
        if (argc != 3)
            throw std::runtime_error("fixture directory and mode required");
        Loader* resident = nullptr;
        CodeValidator validate = [&](const void* code) {
            return resident && resident->contains_code(code);
        };
        Loader loader({argv[1]}, native_personality(validate), Loader::Mode::AdmissionFixture,
                      uintptr_t(&sort_admission_validate));
        resident = &loader;
        auto* module = loader.link("libadmission-sort.so");
        auto* entry = reinterpret_cast<void*>(loader.symbol(module, "admission_sort_run").address);
        prepare_guest_tls();
        auto tls = reinterpret_cast<uintptr_t>(guest_tls_base());
        errno = EBUSY;
        auto result = invoke_admitted(loader.admission_domain(), entry,
                                      {Argument::integer(std::atoi(argv[2])),
                                       Argument::integer(tls, ArgumentType::Int64)})
                          .result_integers[0];
        if (result || errno != EBUSY || !routed.load())
            throw std::runtime_error("result, routing or host errno mismatch");
        std::cout << "M5_ADMISSION_SORT_PASS mode=" << argv[2] << " tls=1 exact_return=1 errno=1\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
