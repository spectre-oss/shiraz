// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
#include "guest_tls_layout.hpp"
#endif
#include "shiraz/darwin/guest/dynamic.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <cstdlib>
#include <iostream>
using namespace shiraz::guest;
static std::atomic<unsigned> transfers{0};
extern "C" uintptr_t dynamic_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        auto domain = AdmissionDomain::current_invocation();
        if (!domain)
            std::abort();
        ++transfers;
        return domain->route(site, target, sp).target;
    } catch (...) {
        std::abort();
    }
}
static void require(bool value, const char* message) {
    if (!value)
        throw std::runtime_error(message);
}
int main(int argc, char** argv) {
    try {
        require(argc == 2, "fixture directory required");
        Loader* current = nullptr;
        auto mutex = std::make_shared<std::recursive_mutex>();
        auto providers = dynamic_personality(
            native_personality([&](const void* p) { return current && current->contains_code(p); }),
            {[&] { return current; }, mutex, [] {}});
        Loader loader({argv[1]}, std::move(providers), Loader::Mode::AdmissionFixture,
                      uintptr_t(&dynamic_validate));
        current = &loader;
        auto* module = loader.link("libadmission-dynamic.so");
        auto domain = loader.admission_domain();
        for (const auto& binding : loader.bindings())
            require(binding.symbol != "dl_iterate_phdr", "iterator must be resolved dynamically");
        prepare_guest_tls();
        auto self = loader.symbol(module, "pthread_self");
#ifdef SHIRAZ_EXPERIMENTAL_THREAD_CAPTURE
        require(known_guest_closure(reinterpret_cast<void*>(self.address)),
                "research pthread_self uses an admitted closure");
        auto self_entry = reinterpret_cast<void*>(
            loader.symbol(module, "admission_dynamic_pthread_self").address);
        auto research_self = invoke_admitted(domain, self_entry, {}).result_integers[0];
        require(research_self && research_self == reinterpret_cast<uintptr_t*>(
                                                      guest_tls_base())[bionic::kTlsThreadIdSlot],
                "admitted research pthread_self returns guest thread identity");
#else
        require(self.address == reinterpret_cast<uintptr_t>(&shiraz_guest_cached_pthread_self) &&
                    known_guest_provider_binding("pthread_self", "prepared/pthread-self-noargs",
                                                 reinterpret_cast<void*>(self.address)) &&
                    !known_guest_provider_binding("other", "prepared/pthread-self-noargs",
                                                  reinterpret_cast<void*>(self.address)) &&
                    !known_guest_provider_binding("pthread_self", "wrong",
                                                  reinterpret_cast<void*>(self.address)) &&
                    !known_guest_provider_binding("pthread_self", "prepared/pthread-self-noargs",
                                                  reinterpret_cast<void*>(&dynamic_validate)),
                "exact pthread_self binding only");
        auto self_entry = reinterpret_cast<void*>(
            loader.symbol(module, "admission_dynamic_pthread_self").address);
        require(invoke_admitted(domain, self_entry, {}).result_integers[0] ==
                    reinterpret_cast<uintptr_t>(pthread_self()),
                "admitted guest pthread_self preserves native identity");
#endif
        auto once =
            reinterpret_cast<void*>(loader.symbol(module, "admission_dynamic_once").address);
        require(invoke_admitted(domain, once, {}).result_integers[0] == 1 &&
                    invoke_admitted(domain, once, {}).result_integers[0] == 1,
                "nested pthread_once callback returns through admitted boundary once");
        auto threaded =
            reinterpret_cast<void*>(loader.symbol(module, "admission_dynamic_thread").address);
        require(invoke_admitted(domain, threaded, {}).result_integers[0] == 1,
                "guest-created admitted thread and key destructor complete");
        auto walk =
            reinterpret_cast<void*>(loader.symbol(module, "admission_dynamic_walk").address);
        require(int(invoke_admitted(domain, walk, {Argument::integer(0)}).result_integers[0]) == 23,
                "dynamically resolved iterator and admitted callback");
        require(int(int32_t(
                    invoke_admitted(domain, walk, {Argument::integer(1)}).result_integers[0])) ==
                    -1,
                "non-guest callback is rejected");
        require(transfers.load() > 0, "admission validator exercised");
        auto site = loader.symbol(module, "admission_dynamic_transfer").address;
        auto unrelated = loader.global_symbol("dladdr").address;
        uintptr_t sp;
        __asm__ volatile("mov %0, sp" : "=r"(sp));
        bool refused = false;
        {
            AdmissionDomain::Invocation invocation(domain);
            try {
                (void)domain->route(site, unrelated, sp);
            } catch (const ElfError&) {
                refused = true;
            }
        }
        require(refused, "unrelated dynamic closure remains unadmitted");
        {
            Loader* other_current = nullptr;
            auto other_mutex = std::make_shared<std::recursive_mutex>();
            auto other_providers =
                dynamic_personality(native_personality([&](const void* p) {
                                        return other_current && other_current->contains_code(p);
                                    }),
                                    {[&] { return other_current; }, other_mutex, [] {}});
            Loader other({argv[1]}, std::move(other_providers), Loader::Mode::AdmissionFixture,
                         uintptr_t(&dynamic_validate));
            other_current = &other;
            auto* other_module = other.link("libadmission-dynamic.so");
            auto other_domain = other.admission_domain();
            auto lookup = reinterpret_cast<void*>(loader.global_symbol("dlsym").address);
            auto iterator =
                reinterpret_cast<void*>(loader.global_symbol("dl_iterate_phdr").address);
            const char* name = "dl_iterate_phdr";
            {
                AdmissionDomain::Invocation invocation(other_domain);
                other_domain->admit_entry(
                    other.symbol(other_module, "admission_dynamic_walk").address);
                auto result = invoke(lookup, {Argument::integer(0, ArgumentType::Int64),
                                              Argument::integer(reinterpret_cast<uintptr_t>(name),
                                                                ArgumentType::Int64)});
                require(result.result_integers[0] == 0, "cross-namespace lookup rejected");
                result = invoke(iterator, {Argument::integer(reinterpret_cast<uintptr_t>(walk),
                                                             ArgumentType::Int64),
                                           Argument::integer(0, ArgumentType::Int64)});
                require(int(int32_t(result.result_integers[0])) == -1,
                        "cross-namespace callback rejected");
                auto other_site = other.symbol(other_module, "admission_dynamic_transfer").address;
                bool foreign_refused = false;
                try {
                    (void)other_domain->route(other_site, reinterpret_cast<uintptr_t>(iterator),
                                              sp);
                } catch (const ElfError&) {
                    foreign_refused = true;
                }
                require(foreign_refused, "foreign iterator remains unadmitted");
            }
            other_current = nullptr;
        }
        current = nullptr;
        std::cout << "M5_ADMISSION_DYNAMIC_PASS dlsym=1 callback=1 invalid_callback_refused=1 "
                     "unrelated_refused=1 foreign_refused=1\n";
    } catch (const std::exception& error) {
        std::cerr << "FAIL " << error.what() << '\n';
        return 1;
    }
}
