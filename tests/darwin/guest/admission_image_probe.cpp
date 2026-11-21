// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/loader.hpp"
#include "shiraz/darwin/guest/properties.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <thread>
#include <vector>
using namespace shiraz::guest;
static std::shared_ptr<AdmissionDomain> domain;
static std::atomic<unsigned> transfers{0}, admitted{0}, syscalls{0};
extern "C" uintptr_t image_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    try {
        ++transfers;
        auto current = AdmissionDomain::current_invocation();
        if (!current)
            std::abort();
        auto result = current->route(site, target, sp);
        if (result.newly_admitted)
            ++admitted;
        return result.target;
    } catch (...) {
        std::abort();
    }
}
extern "C" uintptr_t image_syscall(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t, uint64_t,
                                   uint64_t number, uintptr_t sp) noexcept {
    if (number != 172 || !prepared_guest_stack_contains(sp))
        std::abort();
    ++syscalls;
    return 77;
}
static void require(bool value, const char* what) {
    if (!value)
        throw std::runtime_error(what);
}
int main(int argc, char** argv) {
    try {
        bool map_only = argc == 3 && std::strcmp(argv[1], "--map-only") == 0;
        require(argc == 3, "fixture paths or --map-only library path");
        std::ifstream file(argv[map_only ? 2 : 1], std::ios::binary | std::ios::ate);
        auto size = file.tellg();
        require(bool(file) && size > 0 && size < (128 << 20), "input size");
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        file.seekg(0);
        require(bool(file.read(reinterpret_cast<char*>(bytes.data()), size)), "fixture read");
        if (map_only) {
            Image image(bytes, Image::Mapping::AdmissionDeferred, uintptr_t(&image_syscall),
                        uintptr_t(&image_validate));
            std::cout << "M5_ADMISSION_IMAGE_MAPPED_ONLY relocated=0 sealed=0 executed=0\n";
            return 0;
        }
        {
            auto path = std::filesystem::canonical(argv[2]);
            auto core = core_personality();
            Providers providers;
            Provider libc = core.modules().at("libc.so");
            const ProviderExport *copy = nullptr, *move = nullptr;
            for (const auto& item : libc.exports) {
                if (item.name == "memcpy")
                    copy = &item;
                if (item.name == "memmove")
                    move = &item;
            }
            require(copy && move &&
                        known_guest_provider_binding(copy->name, copy->abi,
                                                     reinterpret_cast<void*>(copy->address)) &&
                        known_guest_provider_binding(move->name, move->abi,
                                                     reinterpret_cast<void*>(move->address)),
                    "exact audited byte provider bindings admitted");
            require(!known_guest_provider_binding("memcpy", copy->abi,
                                                  reinterpret_cast<void*>(move->address)) &&
                        !known_guest_provider_binding("memmove", copy->abi,
                                                      reinterpret_cast<void*>(move->address)) &&
                        !known_guest_provider_binding("memmove", move->abi,
                                                      reinterpret_cast<void*>(copy->address)) &&
                        !known_guest_provider_binding("arbitrary", move->abi,
                                                      reinterpret_cast<void*>(move->address)),
                    "mismatched provider name, ABI and entry refused");
            libc.exports.push_back(fixed_export("getpid", "LIBC", +[]() { return 19; }));
            libc.exports.push_back({"__shiraz_linux_syscall",
                                    {},
                                    uintptr_t(&image_syscall),
                                    SymbolKind::Function,
                                    "fixture"});
            providers.add(std::move(libc));
            std::shared_ptr<AdmissionDomain> retained;
            {
                Loader loader({path.parent_path()}, std::move(providers),
                              Loader::Mode::AdmissionFixture, uintptr_t(&image_validate));
                auto* module = loader.link(path.filename());
                retained = loader.admission_domain();
                auto* lifecycle =
                    reinterpret_cast<unsigned*>(loader.symbol(module, "loader_lifecycle").address);
                require(*lifecycle == 0, "link does not execute constructors");
                require(loader.open(path.filename()) == module && *lifecycle == 1 &&
                            loader.constructor_count() == 1,
                        "loader executes admitted constructor once");
                require(loader.open(path.filename()) == module && *lifecycle == 1,
                        "loader initialization is idempotent");
                auto entry = reinterpret_cast<void*>(loader.symbol(module, "loader_entry").address);
                require(invoke_admitted(retained, entry, {}).result_integers[0] == 20,
                        "loader admits guest PLT call to bound provider closure");
                std::atomic<bool> running{true};
                std::atomic<unsigned> old_calls{0}, old_failures{0};
                std::thread old_caller([&] {
                    while (running.load()) {
                        try {
                            if (invoke_admitted(retained, entry, {}).result_integers[0] != 20)
                                ++old_failures;
                            ++old_calls;
                        } catch (...) {
                            ++old_failures;
                        }
                    }
                });
                while (!old_calls.load() && !old_failures.load())
                    std::this_thread::yield();
                Loader::Module* second = nullptr;
                try {
                    second = loader.link(std::filesystem::path(argv[1]).filename());
                } catch (...) {
                    running = false;
                    old_caller.join();
                    throw;
                }
                running = false;
                old_caller.join();
                require(old_calls && !old_failures,
                        "old guest invocations survive concurrent extension");
                require(second && loader.image_count() == 2 &&
                            loader.admission_domain() == retained,
                        "sealed domain atomically admits a second linked image");
                require(loader.open(std::filesystem::path(argv[1]).filename()) == second &&
                            invoke_admitted(retained,
                                            reinterpret_cast<void*>(
                                                loader.symbol(second, "fixture_entry").address),
                                            {})
                                    .result_integers[0] == 41,
                        "new image executes through the original admission domain");
                require(invoke_admitted(retained, entry, {}).result_integers[0] == 20,
                        "existing image remains usable after extension");
                bool refused = false;
                try {
                    (void)loader.link("missing-extension.so");
                } catch (const ElfError&) {
                    refused = true;
                }
                require(refused && loader.image_count() == 2 &&
                            invoke_admitted(retained, entry, {}).result_integers[0] == 20,
                        "failed extension leaves the existing namespace usable");
                loader.finalize_images();
                require(*lifecycle == 11, "loader executes admitted finalizer");
            }
            bool closed = false;
            try {
                AdmissionDomain::Invocation invocation(retained);
            } catch (const ElfError&) {
                closed = true;
            }
            require(closed, "loader destruction closes retained domain");
            {
                auto extension_providers = core_personality();
                Providers next_providers;
                auto next_libc = extension_providers.modules().at("libc.so");
                next_libc.exports.push_back(fixed_export("getpid", "LIBC", +[]() { return 19; }));
                next_libc.exports.push_back({"__shiraz_linux_syscall",
                                             {},
                                             uintptr_t(&image_syscall),
                                             SymbolKind::Function,
                                             "fixture"});
                next_providers.add(std::move(next_libc));
                Loader loader({path.parent_path()}, std::move(next_providers),
                              Loader::Mode::AdmissionFixture, uintptr_t(&image_validate));
                auto first_path = std::filesystem::path(argv[1]).filename();
                loader.open(first_path);
                auto* second = loader.link(path.filename());
                auto* lifecycle =
                    reinterpret_cast<unsigned*>(loader.symbol(second, "loader_lifecycle").address);
                require(*lifecycle == 0, "extended image remains uninitialized after link");
                require(loader.open(path.filename()) == second && *lifecycle == 1 &&
                            loader.constructor_count() == 1,
                        "extended image constructor executes exactly once");
                require(loader.open(path.filename()) == second && *lifecycle == 1,
                        "extended image initialization is idempotent");
                loader.finalize_images();
                require(*lifecycle == 11, "extended image finalizer executes");
            }
            {
                auto base_providers = core_personality();
                Providers incomplete;
                auto libc_without_getpid = base_providers.modules().at("libc.so");
                libc_without_getpid.exports.push_back({"__shiraz_linux_syscall",
                                                       {},
                                                       uintptr_t(&image_syscall),
                                                       SymbolKind::Function,
                                                       "fixture"});
                incomplete.add(std::move(libc_without_getpid));
                Loader loader({path.parent_path()}, std::move(incomplete),
                              Loader::Mode::AdmissionFixture, uintptr_t(&image_validate));
                auto* first = loader.open(std::filesystem::path(argv[1]).filename());
                auto stable_domain = loader.admission_domain();
                auto first_entry =
                    reinterpret_cast<void*>(loader.symbol(first, "fixture_entry").address);
                auto old_records = loader.bindings().size();
                for (unsigned attempt = 0; attempt != 2; ++attempt) {
                    bool rejected = false;
                    try {
                        (void)loader.link(path.filename());
                    } catch (const ElfError&) {
                        rejected = true;
                    }
                    require(
                        rejected && loader.image_count() == 1 &&
                            loader.bindings().size() == old_records &&
                            !loader.loaded(path.filename()) &&
                            loader.admission_domain() == stable_domain &&
                            invoke_admitted(stable_domain, first_entry, {}).result_integers[0] ==
                                41,
                        "unresolved extension rolls back its namespace and preserves admitted "
                        "code");
                }
            }
            transfers = 0;
            admitted = 0;
            syscalls = 0;
        }
        {
            Providers providers;
            providers.add({"libc.so",
                           {},
                           {{"__shiraz_linux_syscall",
                             {},
                             uintptr_t(&image_syscall),
                             SymbolKind::Function,
                             "fixture"}},
                           false});
            auto path = std::filesystem::canonical(argv[1]);
            Loader loader({path.parent_path()}, std::move(providers),
                          Loader::Mode::AdmissionLinkOnly, uintptr_t(&image_validate));
            auto* module = loader.link(path.filename());
            require(module && loader.image_count() == 1 && loader.constructor_count() == 0,
                    "admission link-only fixture");
            require(loader.symbol(module, "fixture_entry").found, "linked fixture symbol");
            bool refused = false;
            try {
                (void)loader.open(path.filename());
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused && loader.constructor_count() == 0, "link-only refuses execution");
        }
        bool strict_refused = false;
        try {
            Image strict(bytes, Image::Mapping::Deferred, uintptr_t(&image_syscall));
        } catch (const ElfError&) {
            strict_refused = true;
        }
        require(strict_refused, "ordinary loader refuses ambiguous code");
        bool no_validator_refused = false;
        try {
            Image invalid(bytes, Image::Mapping::AdmissionDeferred, uintptr_t(&image_syscall));
        } catch (const ElfError&) {
            no_validator_refused = true;
        }
        require(no_validator_refused, "admission image requires validator");
        auto image = std::make_shared<Image>(bytes, Image::Mapping::AdmissionDeferred,
                                             uintptr_t(&image_syscall), uintptr_t(&image_validate));
        bool premature = false;
        try {
            (void)image->admission_session();
        } catch (const ElfError&) {
            premature = true;
        }
        require(premature, "unsealed image cannot expose admission session");
        image->relocate([&](size_t index) { return image->address(index); });
        image->seal();
        auto session = image->admission_session();
        auto base = image->load_bias() + image->elf().first_page;
        std::shared_ptr<void> lease(image, reinterpret_cast<void*>(base));
        domain = std::make_shared<AdmissionDomain>();
        domain->add_image(session, lease);
        uintptr_t property_callback;
        std::memcpy(&property_callback, image->symbol("fixture_tenth"), sizeof(property_callback));
        auto properties =
            property_exports(api27_properties(), [property_callback](const void* pointer) {
                return reinterpret_cast<uintptr_t>(pointer) == property_callback;
            });
        void* foreach_entry = reinterpret_cast<void*>(properties.at(3).address);
        domain->allow_closure(foreach_entry);
        domain->seal();
        bool missing_domain = false;
        try {
            (void)image->initialize();
        } catch (const ElfError&) {
            missing_domain = true;
        }
        require(missing_domain, "admitted image cannot initialize without domain");
        require(image->initialize(domain) == 0, "admitted image initializes");
        bool unavailable_proof = false;
        try {
            (void)image->verified_mrs_window_sites();
        } catch (const ElfError&) {
            unavailable_proof = true;
        }
        require(unavailable_proof, "unimplemented MRS evidence is not claimed");
        bool unavailable_count = false;
        try {
            (void)image->rewritten_thread_pointers();
        } catch (const ElfError&) {
            unavailable_count = true;
        }
        require(unavailable_count, "unimplemented dynamic MRS count is not claimed");
        auto* entry = image->symbol("fixture_entry");
        auto* table = image->symbol("fixture_table");
        auto* second = image->symbol("fixture_second");
        auto* third = image->symbol("fixture_third");
        uintptr_t second_target, third_target;
        std::memcpy(&second_target, second, sizeof(second_target));
        std::memcpy(&third_target, third, sizeof(third_target));
        std::atomic<unsigned> ready{0}, passed{0};
        std::atomic<bool> start{false};
        std::vector<std::thread> workers;
        for (unsigned i = 0; i < 8; ++i)
            workers.emplace_back([&] {
                bool announced = false;
                try {
                    volatile uint8_t stack_margin[2048]{};
                    GuestTlsCleanupScope cleanup;
                    prepare_guest_tls();
                    ++ready;
                    announced = true;
                    while (!start.load(std::memory_order_acquire))
                        std::this_thread::yield();
                    bool ok = true;
                    for (unsigned j = 0; j < 16; ++j)
                        if (invoke_admitted(domain, entry, {}).result_integers[0] != 41)
                            ok = false;
                    if (ok && !stack_margin[0])
                        ++passed;
                } catch (...) {
                    if (!announced)
                        ++ready;
                }
            });
        while (ready < 8)
            std::this_thread::yield();
        start.store(true, std::memory_order_release);
        for (auto& worker : workers)
            worker.join();
        require(passed == 8 && admitted == 1 && transfers == 256,
                "admitted Image concurrent TLS execution");
        std::memcpy(table, &second_target, sizeof(second_target));
        {
            GuestTlsCleanupScope cleanup;
            prepare_guest_tls();
            require(invoke_admitted(domain, entry, {}).result_integers[0] == 77,
                    "admitted Image syscall");
        }
        std::memcpy(table, &third_target, sizeof(third_target));
        {
            GuestTlsCleanupScope cleanup;
            prepare_guest_tls();
            require(invoke_admitted(domain, entry, {}).result_integers[0] == 91,
                    "admitted Image nested call");
        }
        require(admitted == 4 && transfers == 262 && syscalls == 1,
                "admitted Image target accounting");
        uintptr_t host_target = reinterpret_cast<uintptr_t>(foreach_entry), fourth_target;
        std::memcpy(&fourth_target, image->symbol("fixture_fourth"), sizeof(fourth_target));
        std::memcpy(image->symbol("fixture_host_slot"), &host_target, sizeof(host_target));
        std::memcpy(table, &fourth_target, sizeof(fourth_target));
        unsigned visits = 0;
        {
            GuestTlsCleanupScope cleanup;
            prepare_guest_tls();
            auto result = invoke_admitted(
                domain, entry,
                {Argument::integer(property_callback, ArgumentType::Int64),
                 Argument::integer(reinterpret_cast<uintptr_t>(&visits), ArgumentType::Int64)});
            require(result.result_integers[0] == 1 && visits == api27_properties().size(),
                    "admitted property enumeration uses guest callback");
        }
        {
            std::ifstream second_file(argv[2], std::ios::binary | std::ios::ate);
            auto second_size = second_file.tellg();
            require(bool(second_file) && second_size > 0 && second_size < (1 << 20),
                    "staged constructor fixture size");
            std::vector<uint8_t> second_bytes(static_cast<size_t>(second_size));
            second_file.seekg(0);
            require(
                bool(second_file.read(reinterpret_cast<char*>(second_bytes.data()), second_size)),
                "staged constructor fixture read");
            auto staged_image =
                std::make_shared<Image>(second_bytes, Image::Mapping::AdmissionDeferred,
                                        uintptr_t(&image_syscall), uintptr_t(&image_validate));
            auto core = core_personality();
            const auto& libc = core.modules().at("libc.so");
            auto getpid = fixed_export("getpid", "LIBC", +[]() { return 19; });
            staged_image->relocate([&](size_t index) -> uintptr_t {
                const auto& symbol = staged_image->elf().symbols.at(index);
                if (symbol.section)
                    return staged_image->address(index);
                if (symbol.name == getpid.name)
                    return getpid.address;
                for (const auto& entry : libc.exports)
                    if (entry.name == symbol.name)
                        return entry.address;
                if (symbol.name.empty())
                    return 0;
                throw std::runtime_error("staged fixture unresolved import");
            });
            staged_image->seal();
            auto staged_session = staged_image->admission_session();
            auto staged_base = staged_image->load_bias() + staged_image->elf().first_page;
            std::shared_ptr<void> staged_lease(staged_image, reinterpret_cast<void*>(staged_base));
            auto* lifecycle = reinterpret_cast<unsigned*>(staged_image->symbol("loader_lifecycle"));
            require(*lifecycle == 0 && !domain->owns_session(staged_session.get()),
                    "constructor image starts private");
            bool refused = false;
            try {
                (void)staged_image->initialize(domain);
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused && *lifecycle == 0, "unstaged constructor is refused");
            {
                AdmissionDomain::StagedExtension stage(domain, {{staged_session, staged_lease}}, {},
                                                       {});
                require(staged_image->initialize(domain) == 1 && *lifecycle == 1,
                        "private stage executes real admitted guest constructor");
                std::atomic<bool> hidden{false};
                std::thread observer([&] { hidden = !domain->owns_session(staged_session.get()); });
                observer.join();
                require(hidden, "constructor image remains private to other threads");
            }
            require(!domain->owns_session(staged_session.get()) && *lifecycle == 1,
                    "admission rollback does not claim to undo constructor effects");
            {
                AdmissionDomain::StagedExtension stage(domain, {{staged_session, staged_lease}}, {},
                                                       {});
                require(staged_image->initialize(domain) == 0 && *lifecycle == 1,
                        "initialized image does not rerun constructor");
                stage.commit();
            }
            require(domain->owns_session(staged_session.get()) &&
                        staged_image->finalize(domain) == 1 && *lifecycle == 11,
                    "published image runs its admitted finalizer");
        }
        // Exercise the same writable-table dispatch shape observed in the target
        // libraries: guest code changes the slot after initial image admission,
        // and the next indirect branch must admit the actual new target.
        {
            uintptr_t target = 0, observed = 0;
            std::memcpy(&target, image->symbol("fixture_seventh"), sizeof(target));
            unsigned before = admitted.load();
            GuestTlsCleanupScope cleanup;
            prepare_guest_tls();
            invoke_admitted(domain, image->symbol("fixture_set_table"),
                            {Argument::integer(target, ArgumentType::Int64)});
            std::memcpy(&observed, table, sizeof(observed));
            require(observed == target, "guest wrote the dispatch slot");
            require(invoke_admitted(domain, entry, {}).result_integers[0] == 7 &&
                        admitted == before + 1,
                    "guest-updated dispatch target admitted before execution");
        }
        bool finalize_refused = false;
        try {
            (void)image->finalize();
        } catch (const ElfError&) {
            finalize_refused = true;
        }
        require(finalize_refused && image->finalize(domain) == 0,
                "admitted finalization requires domain");
        std::weak_ptr<Image> weak = image;
        image.reset();
        session.reset();
        lease.reset();
        require(!weak.expired(), "domain retains Image mapping");
        domain->close();
        require(!weak.expired(), "closed domain retains Image mapping");
        domain.reset();
        require(weak.expired(), "Image mapping released after domain destruction");
        std::cout << "M5_ADMISSION_IMAGE_PASS strict_refusal=1 mapping=1 relocations=1 seal=1 "
                     "tls=1 syscall=1 "
                     "nested_call=1 guest_table_update=1 concurrent=8 repeats=16 lifetime=1 "
                     "fixture_only=1\n";
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
