// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/thread.hpp"
#include <atomic>
#include <cerrno>
#include <iostream>
using namespace shiraz::guest;
extern "C" uintptr_t thread_admission_validate(uintptr_t target, uintptr_t site,
                                               uintptr_t sp) noexcept {
    try {
        return AdmissionDomain::current_invocation()->route(site, target, sp).target;
    } catch (...) {
        std::abort();
    }
}
static Argument ptr(const void* p) {
    return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64);
}
static void require(bool ok, const char* why) {
    if (!ok)
        throw std::runtime_error(why);
}
struct Observation {
    AdmissionDomain* expected;
    std::atomic<int> calls{0};
    std::atomic<bool> matched{false};
};
static void observe(void* p) {
    auto& result = *static_cast<Observation*>(p);
    result.matched = AdmissionDomain::current_invocation().get() == result.expected;
    ++result.calls;
}
int main(int argc, char** argv) {
    try {
        require(argc == 3, "usage: thread-admission-probe fixture-directory mode");
        std::string mode = argv[2];
        require(mode == "foreign-normal" || mode == "foreign-admitted" || mode == "host-admitted" ||
                    mode == "same-admitted",
                "mode");
        bool admitted = mode != "foreign-normal";
        Loader* current = nullptr;
        auto validate = [&](const void* p) { return current && current->contains_code(p); };
        auto lifecycle = std::make_shared<Lifecycle>(validate);
        auto loader = admitted
                          ? std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]},
                                                     native_personality(validate, lifecycle),
                                                     Loader::Mode::AdmissionFixture,
                                                     uintptr_t(&thread_admission_validate))
                          : std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]},
                                                     native_personality(validate, lifecycle));
        current = loader.get();
        lifecycle->attach(*loader);
        auto* module = loader->open("libthread-retention.so");
        auto observer = fixed_export("thread_admission_observe", "LIBC", &observe);
        auto setter = reinterpret_cast<void*>(
            loader->symbol(module, "thread_retention_set_observer").address);
        if (admitted) {
            AdmissionDomain::Invocation invocation(loader->admission_domain());
            require(loader->admission_domain()->register_closure(
                        reinterpret_cast<void*>(observer.address)),
                    "authorize observer");
            invoke_admitted(loader->admission_domain(), setter,
                            {ptr(reinterpret_cast<void*>(observer.address))});
        } else
            invoke(setter, {ptr(reinterpret_cast<void*>(observer.address))});
        auto exports = thread_exports(validate, lifecycle);
        void *create = nullptr, *join = nullptr;
        for (auto& entry : exports) {
            if (entry.name == "pthread_create")
                create = reinterpret_cast<void*>(entry.address);
            if (entry.name == "pthread_join")
                join = reinterpret_cast<void*>(entry.address);
        }
        require(create && join, "thread exports");
        auto function =
            reinterpret_cast<void*>(loader->symbol(module, "thread_retention_entry").address);
        Observation observation{admitted ? loader->admission_domain().get() : nullptr};
        uint64_t handle = 0;
        auto start = [&] {
            return int(int32_t(
                invoke(create, {ptr(&handle), ptr(nullptr), ptr(function), ptr(&observation)})
                    .result_integers[0]));
        };
        if (mode == "host-admitted") {
            require(start() == 0, "host-origin admitted thread failed");
        } else if (mode == "same-admitted") {
            AdmissionDomain::Invocation owner(loader->admission_domain());
            require(start() == 0, "same-domain admitted thread failed");
        } else {
            Loader other({argv[1]}, native_personality(validate, lifecycle),
                         Loader::Mode::AdmissionFixture, uintptr_t(&thread_admission_validate));
            other.link("libthread-retention.so");
            AdmissionDomain::Invocation foreign(other.admission_domain());
            int result = start();
            if (admitted)
                require(result == EINVAL && handle == 0 && !lifecycle->retained_thread_callbacks(),
                        "foreign admitted thread accepted");
            else
                require(result == 0, "normal thread inherited unrelated domain");
        }
        if (mode != "foreign-admitted") {
            require(int(int32_t(
                        invoke(join, {Argument::integer(handle, ArgumentType::Int64), ptr(nullptr)})
                            .result_integers[0])) == 0,
                    "join failed");
            require(observation.calls == 1 && observation.matched &&
                        !lifecycle->retained_thread_callbacks(),
                    "wrong callback domain or retention leak");
        }
        lifecycle->shutdown();
        std::cout << "M5_THREAD_ADMISSION_PASS mode=" << mode << '\n';
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
