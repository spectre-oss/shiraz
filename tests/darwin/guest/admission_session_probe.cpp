// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_session.hpp"
#include "shiraz/darwin/guest/admission_return.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/jni.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <libkern/OSCacheControl.h>
#include <stdexcept>
#include <sys/mman.h>
#include <thread>
#include <vector>
#include <unistd.h>
using namespace shiraz::guest;
extern "C" long shiraz_admission_fixture_call_asm(void*, AdmissionReturnScope*);
extern "C" char shiraz_admission_fixture_return[];
static std::shared_ptr<AdmissionDomain> fixture_domain;
extern "C" void shiraz_admission_fixture_arm(AdmissionReturnScope* scope, uintptr_t pc,
                                             uintptr_t sp) noexcept {
    try {
        scope->arm(pc, sp);
    } catch (...) {
        std::abort();
    }
}
static long fixture_call(void* entry) {
    auto domain = fixture_domain;
    AdmissionDomain::Invocation invocation(domain);
    domain->admit_entry(uintptr_t(entry));
    AdmissionReturnScope scope(domain.get());
    return shiraz_admission_fixture_call_asm(entry, &scope);
}
static uintptr_t base, end;
static uintptr_t cross_base, cross_end;
static const Elf* fixture_elf;
namespace shiraz::guest {
bool is_guest_code(const void* pointer) {
    uintptr_t value = reinterpret_cast<uintptr_t>(pointer);
    if (!fixture_elf || (value & 3))
        return false;
    for (uintptr_t image : {base, cross_base}) {
        if (!image || value < image || value - image >= fixture_elf->span)
            continue;
        uint64_t guest = fixture_elf->first_page + uint64_t(value - image);
        for (const auto& segment : fixture_elf->segments)
            if ((segment.flags & 1) && guest >= segment.address &&
                guest - segment.address <= segment.memory_size &&
                4 <= segment.memory_size - (guest - segment.address))
                return true;
    }
    return false;
}
// Path aliases are outside this JNI/admission fixture's scope.
bool resolve_path_field_alias(JNIEnv*, jclass, const char*, const char*, const void*, jfieldID&) {
    return false;
}
bool prepare_path_field(JNIEnv*, jobject, jfieldID, const void*) {
    return false;
}
} // namespace shiraz::guest
static void* registered_native;
static void* ordinary_native;
static JNIEnv fake_host;
static const char* fixture_shorty(JNIEnv*, jmethodID) {
    return "I";
}
static jint JNICALL fixture_register_natives(JNIEnv*, jclass, const JNINativeMethod* methods,
                                             jint count) {
    if (count != 1 || !methods || std::strcmp(methods[0].name, "nativeFixture") ||
        std::strcmp(methods[0].signature, "()I") || !methods[0].fnPtr)
        std::abort();
    registered_native = methods[0].fnPtr;
    return JNI_OK;
}
static std::atomic<unsigned> admissions{0}, transfers{0}, syscall_count{0}, host_calls{0};
static std::atomic<bool> hold_host{false}, host_entered{false}, release_host{false};
static void* host_entry;
extern "C" uintptr_t fixture_validate(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    if (!prepared_guest_stack_contains(sp))
        std::abort();
    ++transfers;
    try {
        auto decision = fixture_domain->route(site, target, sp);
        if (decision.newly_admitted)
            ++admissions;
        return decision.target;
    } catch (...) {
        std::abort();
    }
}
extern "C" uintptr_t fixture_syscall(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t, uint64_t,
                                     uint64_t number, uintptr_t sp) noexcept {
    if (number != 172 || !prepared_guest_stack_contains(sp))
        std::abort();
    ++syscall_count;
    return 77;
}
static void require(bool value, const char* what) {
    if (!value)
        throw std::runtime_error(what);
}
int main(int argc, char** argv) {
    try {
        require(argc == 2, "fixture path");
        std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
        auto size = file.tellg();
        require(bool(file) && size > 0 && size < (1 << 20), "fixture size");
        std::vector<uint8_t> bytes(static_cast<size_t>(size));
        file.seekg(0);
        require(bool(file.read(reinterpret_cast<char*>(bytes.data()), size)), "fixture read");
        auto elf = parse_elf(bytes, 16384);
        fixture_elf = &elf;
        require(elf.span <= (1 << 20) && !elf.needed.size(), "fixture bounds and dependencies");
        host_entry = scalar_closure([](Capture& capture) {
            ++host_calls;
            if (hold_host.load(std::memory_order_acquire)) {
                host_entered.store(true, std::memory_order_release);
                while (!release_host.load(std::memory_order_acquire))
                    std::this_thread::yield();
                using Native = jint (*)(JNIEnv*, jobject);
                if (!ordinary_native || reinterpret_cast<Native>(ordinary_native)(
                                            &fake_host, nullptr) != JNI_VERSION_1_6)
                    std::abort();
            }
            capture.frame.result_integers[0] = 101;
        });
        require(known_guest_closure(host_entry) &&
                    !known_guest_closure(static_cast<uint8_t*>(host_entry) + 4),
                "fixture host closure identity");
        constexpr size_t pool_size = 1 << 18;
        size_t mapping_size = elf.span + pool_size;
        auto* memory =
            static_cast<uint8_t*>(mmap(nullptr, mapping_size, PROT_READ | PROT_WRITE | PROT_EXEC,
                                       MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
        require(memory != MAP_FAILED, "fixture mapping");
        std::shared_ptr<void> memory_lease(memory, [mapping_size](void* p) {
            if (munmap(p, mapping_size))
                std::abort();
        });
        base = uintptr_t(memory);
        end = base + elf.span;
        GuestImageReservation reservation(base, mapping_size);
        auto address = [&](uint64_t guest) -> uintptr_t {
            require(guest >= elf.first_page && guest - elf.first_page < elf.span, "guest address");
            return base + uintptr_t(guest - elf.first_page);
        };
        auto populate = [&](uint8_t* mapping) {
            uintptr_t mapping_base = uintptr_t(mapping);
            JitWriteScope writable;
            for (const auto& segment : elf.segments)
                std::memcpy(mapping + (segment.address - elf.first_page),
                            bytes.data() + segment.offset, segment.file_size);
            for (const auto& relocation : elf.relocations) {
                require(relocation.type == 1027, "fixture only uses relative relocations");
                uintptr_t value = mapping_base - elf.first_page + uint64_t(relocation.addend);
                std::memcpy(mapping + (relocation.address - elf.first_page), &value, sizeof(value));
            }
        };
        populate(memory);
        sys_icache_invalidate(memory, elf.span);
        auto symbol = [&](const char* name) -> uintptr_t {
            for (const auto& item : elf.symbols)
                if (item.name == name && item.section)
                    return address(item.address);
            throw std::runtime_error("fixture symbol missing");
        };
        auto entry = symbol("fixture_entry"), setter = symbol("fixture_set_table");
        auto table = symbol("fixture_table"), second = symbol("fixture_second");
        auto third = symbol("fixture_third"), fourth = symbol("fixture_fourth"),
             host_slot = symbol("fixture_host_slot");
        auto fifth = symbol("fixture_fifth"), sixth = symbol("fixture_sixth"),
             seventh = symbol("fixture_seventh");
        auto eighth = symbol("fixture_eighth"), ninth = symbol("fixture_ninth");
        uintptr_t first_target, second_target, third_target, fourth_target, fifth_target,
            sixth_target, seventh_target;
        uintptr_t eighth_target, ninth_target;
        std::memcpy(&first_target, reinterpret_cast<void*>(table), sizeof(first_target));
        std::memcpy(&second_target, reinterpret_cast<void*>(second), sizeof(second_target));
        std::memcpy(&third_target, reinterpret_cast<void*>(third), sizeof(third_target));
        std::memcpy(&fourth_target, reinterpret_cast<void*>(fourth), sizeof(fourth_target));
        std::memcpy(&fifth_target, reinterpret_cast<void*>(fifth), sizeof(fifth_target));
        std::memcpy(&sixth_target, reinterpret_cast<void*>(sixth), sizeof(sixth_target));
        std::memcpy(&seventh_target, reinterpret_cast<void*>(seventh), sizeof(seventh_target));
        std::memcpy(&eighth_target, reinterpret_cast<void*>(eighth), sizeof(eighth_target));
        std::memcpy(&ninth_target, reinterpret_cast<void*>(ninth), sizeof(ninth_target));
        {
            uint32_t original_entry, original_setter_return, original_target;
            std::memcpy(&original_entry, reinterpret_cast<void*>(entry + 12), 4);
            std::memcpy(&original_setter_return, reinterpret_cast<void*>(setter + 12), 4);
            std::memcpy(&original_target, reinterpret_cast<void*>(first_target), 4);
            {
                // Initial admission installs fixture_entry's branch and the exported
                // guest table setter's return. Leave no capacity for a new target.
                AdmissionSession limited(elf, bytes, base, end, 160, uintptr_t(&fixture_syscall),
                                         uintptr_t(&fixture_validate));
                limited.initialize();
                for (unsigned i = 0; i < 2; ++i) {
                    bool refused = false;
                    try {
                        (void)limited.admit(first_target);
                    } catch (const ElfError&) {
                        refused = true;
                    }
                    uint32_t current_target;
                    std::memcpy(&current_target, reinterpret_cast<void*>(first_target), 4);
                    require(refused && current_target == original_target,
                            "exhausted admission leaves target untouched");
                }
            }
            {
                JitWriteScope writable;
                std::memcpy(reinterpret_cast<void*>(entry + 12), &original_entry, 4);
                std::memcpy(reinterpret_cast<void*>(setter + 12), &original_setter_return, 4);
            }
            sys_icache_invalidate(reinterpret_cast<void*>(entry + 12), 4);
            sys_icache_invalidate(reinterpret_cast<void*>(setter + 12), 4);
        }
        {
            auto owner_ptr = std::make_shared<AdmissionSession>(elf, bytes, base, end, pool_size,
                                                                uintptr_t(&fixture_syscall),
                                                                uintptr_t(&fixture_validate));
            auto& owner = *owner_ptr;
            bool refused = false;
            try {
                (void)owner.admit(first_target);
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused, "admission before initialization refused");
            owner.initialize();
            fixture_domain = std::make_shared<AdmissionDomain>();
            bool domain_refused = false;
            try {
                AdmissionDomain::Invocation early(fixture_domain);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "unsealed domain refuses entry");
            std::shared_ptr<void> wrong_lease(memory_lease, memory + 16);
            domain_refused = false;
            try {
                fixture_domain->add_image(owner_ptr, wrong_lease);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "image lease must identify the mapping base");
            wrong_lease.reset();
            fixture_domain->add_image(owner_ptr, memory_lease);
            domain_refused = false;
            try {
                fixture_domain->add_image(owner_ptr, memory_lease);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "overlapping domain image refused");
            domain_refused = false;
            try {
                fixture_domain->allow_closure(static_cast<uint8_t*>(host_entry) + 4);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "interior host closure refused");
            domain_refused = false;
            try {
                fixture_domain->allow_provider_entry(host_entry);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused,
                    "non-provider entry cannot be authorized as an audited provider");
            fixture_domain->seal();
            domain_refused = false;
            try {
                fixture_domain->admit_entry(entry);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "domain requires invocation lease");
            domain_refused = false;
            try {
                (void)fixture_domain->register_closure(host_entry);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "dynamic closure registration requires invocation");
            domain_refused = false;
            try {
                fixture_domain->allow_closure(host_entry);
            } catch (const ElfError&) {
                domain_refused = true;
            }
            require(domain_refused, "sealed image policy is immutable");
            {
                GuestTlsCleanupScope cleanup;
                prepare_guest_tls();
                uintptr_t sp;
                __asm__ volatile("mov %0, sp" : "=r"(sp));
                AdmissionDomain::Invocation invocation(fixture_domain);
                domain_refused = false;
                try {
                    fixture_domain->close();
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain cannot close from its own invocation");
                domain_refused = false;
                try {
                    fixture_domain->admit_entry(table);
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain rejects data entry");
                domain_refused = false;
                try {
                    (void)fixture_domain->route(entry + 8, uintptr_t(host_entry), sp);
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain rejects uninstalled source");
                domain_refused = false;
                try {
                    (void)fixture_domain->route(entry + 12, uintptr_t(host_entry) + 4, sp);
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain rejects interior host target");
                domain_refused = false;
                try {
                    (void)fixture_domain->route(entry + 12, uintptr_t(host_entry), sp);
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain rejects unregistered exact host closure");
                require(fixture_domain->register_closure(host_entry) &&
                            !fixture_domain->register_closure(host_entry),
                        "trusted dynamic closure registration is exact and idempotent");
                require(fixture_domain->route(entry + 12, uintptr_t(host_entry), sp).target ==
                            uintptr_t(host_entry),
                        "registered exact host closure admitted");
                domain_refused = false;
                try {
                    (void)fixture_domain->route(entry + 12, uintptr_t(host_entry), sp + 1);
                } catch (const ElfError&) {
                    domain_refused = true;
                }
                require(domain_refused, "domain rejects unaligned guest stack");
            }
            {
                std::atomic<unsigned> registered{0};
                std::vector<std::thread> registrars;
                for (unsigned i = 0; i < 4; ++i)
                    registrars.emplace_back([&] {
                        try {
                            GuestTlsCleanupScope cleanup;
                            prepare_guest_tls();
                            AdmissionDomain::Invocation invocation(fixture_domain);
                            uintptr_t sp;
                            __asm__ volatile("mov %0, sp" : "=r"(sp));
                            for (unsigned j = 0; j < 16; ++j) {
                                void* closure_entry = scalar_closure([](Capture&) {});
                                if (fixture_domain->register_closure(closure_entry) &&
                                    fixture_domain->route(entry + 12, uintptr_t(closure_entry), sp)
                                            .target == uintptr_t(closure_entry))
                                    ++registered;
                            }
                        } catch (...) {
                        }
                    });
                for (auto& registrar : registrars)
                    registrar.join();
                require(registered == 64, "concurrent dynamic closure registration and routing");
            }
            refused = false;
            try {
                (void)owner.admit(table);
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused, "data target refused");
            refused = false;
            try {
                (void)owner.transfer_site(table);
            } catch (const ElfError&) {
                refused = true;
            }
            require(refused, "uninstalled transfer site refused");
            {
                GuestTlsCleanupScope cleanup;
                prepare_guest_tls();
                uintptr_t sp;
                __asm__ volatile("mov %0, sp" : "=r"(sp));
                const uintptr_t pc = uintptr_t(shiraz_admission_fixture_return);
                auto ret = classify_indirect_transfer(0, 0xd65f03c0u);
                auto tail_return = classify_indirect_transfer(0, 0xd61f03c0u);
                auto jump = classify_indirect_transfer(0, 0xd61f0000u);
                auto call = classify_indirect_transfer(0, 0xd63f0000u);
                require(!AdmissionReturnScope::allows(&owner, ret, pc, sp),
                        "unscoped host return refused");
                {
                    AdmissionReturnScope invalid(&owner);
                    bool bad_pc = false, bad_sp = false;
                    try {
                        invalid.arm(pc + 1, sp);
                    } catch (const ElfError&) {
                        bad_pc = true;
                    }
                    try {
                        invalid.arm(pc, sp + 1);
                    } catch (const ElfError&) {
                        bad_sp = true;
                    }
                    require(bad_pc && bad_sp && !AdmissionReturnScope::allows(&owner, ret, pc, sp),
                            "invalid arm leaves return boundary closed");
                }
                {
                    AdmissionReturnScope no_owner(nullptr);
                    bool denied = false;
                    try {
                        no_owner.arm(pc, sp);
                    } catch (const ElfError&) {
                        denied = true;
                    }
                    require(denied && !AdmissionReturnScope::allows(nullptr, ret, pc, sp),
                            "owner is required");
                }
                {
                    AdmissionReturnScope outer(&owner);
                    outer.arm(pc, sp);
                    require(AdmissionReturnScope::allows(&owner, ret, pc, sp) &&
                                AdmissionReturnScope::allows(&owner, tail_return, pc, sp),
                            "exact host return allowed");
                    require(!AdmissionReturnScope::allows(&owner, ret, pc + 4, sp) &&
                                !AdmissionReturnScope::allows(&owner, ret, pc, sp - 16) &&
                                !AdmissionReturnScope::allows(&owner, jump, pc, sp) &&
                                !AdmissionReturnScope::allows(&owner, call, pc, sp) &&
                                !AdmissionReturnScope::allows(&outer, ret, pc, sp),
                            "wrong target, stack, owner or transfer kind refused");
                    bool duplicate_arm = false;
                    try {
                        outer.arm(pc, sp);
                    } catch (const ElfError&) {
                        duplicate_arm = true;
                    }
                    require(duplicate_arm, "return boundary cannot be rearmed");
                    std::atomic<bool> cross_thread_refused{false};
                    std::thread other([&] {
                        cross_thread_refused = !AdmissionReturnScope::allows(&owner, ret, pc, sp);
                    });
                    other.join();
                    require(cross_thread_refused, "host return boundary is thread local");
                    {
                        AdmissionReturnScope inner(&owner);
                        require(!AdmissionReturnScope::allows(&owner, ret, pc, sp),
                                "unarmed nested scope shadows outer");
                        inner.arm(pc + 4, sp);
                        require(AdmissionReturnScope::allows(&owner, ret, pc + 4, sp) &&
                                    !AdmissionReturnScope::allows(&owner, ret, pc, sp),
                                "nested host return uses innermost boundary");
                    }
                    require(AdmissionReturnScope::allows(&owner, ret, pc, sp),
                            "outer host return restored");
                }
                require(!AdmissionReturnScope::allows(&owner, ret, pc, sp),
                        "expired host return refused");
            }
            std::atomic<unsigned> ready{0}, passed{0};
            std::atomic<bool> start{false};
            std::vector<std::thread> workers;
            for (unsigned i = 0; i < 8; ++i)
                workers.emplace_back([&] {
                    // Keep the fixture's guest SP away from pthread's upper stack edge so
                    // the existing TPIDR spill-window admission has its required margin.
                    volatile uint8_t stack_margin[2048]{};
                    GuestTlsCleanupScope cleanup;
                    prepare_guest_tls();
                    ++ready;
                    while (!start.load(std::memory_order_acquire))
                        std::this_thread::yield();
                    bool ok = true;
                    for (unsigned repeat = 0; repeat < 32; ++repeat)
                        if (fixture_call(reinterpret_cast<void*>(entry)) != 41)
                            ok = false;
                    if (ok && stack_margin[0] == 0)
                        ++passed;
                });
            while (ready != 8)
                std::this_thread::yield();
            start.store(true, std::memory_order_release);
            for (auto& worker : workers)
                worker.join();
            require(passed == 8 && admissions == 1 && transfers == 512,
                    "concurrent first target admission");
            {
                auto site = entry + 12;
                uint32_t installed =
                    __atomic_load_n(reinterpret_cast<const uint32_t*>(site), __ATOMIC_ACQUIRE);
                uint32_t original = 0xd61f0100u; // br x8
                {
                    JitWriteScope writable;
                    std::memcpy(reinterpret_cast<void*>(site), &original, 4);
                }
                refused = false;
                try {
                    (void)owner.transfer_site(site);
                } catch (const ElfError&) {
                    refused = true;
                }
                {
                    JitWriteScope writable;
                    std::memcpy(reinterpret_cast<void*>(site), &installed, 4);
                }
                sys_icache_invalidate(reinterpret_cast<void*>(site), 4);
                require(refused, "tampered transfer site refused");
            }
            {
                JitWriteScope writable;
                std::memcpy(reinterpret_cast<void*>(table), &second_target, sizeof(second_target));
            }
            {
                GuestTlsCleanupScope cleanup;
                prepare_guest_tls();
                require(fixture_call(reinterpret_cast<void*>(entry)) == 77,
                        "switched target syscall");
            }
            require(admissions == 2 && syscall_count == 1 && transfers == 514,
                    "second target admitted exactly once");
            require(!owner.admit(second_target), "repeat admission is idempotent");
            {
                JitWriteScope writable;
                std::memcpy(reinterpret_cast<void*>(table), &third_target, sizeof(third_target));
            }
            {
                GuestTlsCleanupScope cleanup;
                prepare_guest_tls();
                require(fixture_call(reinterpret_cast<void*>(entry)) == 91,
                        "nested indirect call and return");
            }
            require(admissions == 4 && transfers == 518,
                    "nested target and guest continuation admitted");
            {
                static android::NativeBridgeRuntimeCallbacks callbacks{};
                static JNINativeInterface host_functions{};
                static JavaVM host_vm{};
                callbacks.getMethodShorty = &fixture_shorty;
                host_functions.RegisterNatives = &fixture_register_natives;
                fake_host.functions = &host_functions;
                initialize_jni(&callbacks);
                GuestTlsCleanupScope cleanup;
                prepare_guest_tls();
                auto* shadow = shadow_env(&fake_host);
                void* register_entry = reinterpret_cast<void*>(shadow->functions->RegisterNatives);
                require(known_guest_closure(register_entry),
                        "JNI registration wrapper is an exact closure");
                {
                    AdmissionDomain::Invocation invocation(fixture_domain);
                    (void)fixture_domain->register_closure(register_entry);
                    require(AdmissionDomain::current_invocation() == fixture_domain,
                            "current admitted JNI domain");
                }
                require(!AdmissionDomain::current_invocation(),
                        "JNI domain absent outside invocation");
                using Native = jint (*)(JNIEnv*, jobject);
                auto register_trampoline = reinterpret_cast<Native>(
                    native_trampoline(reinterpret_cast<void*>(ninth_target), "I", fixture_domain));
                require(register_trampoline(&fake_host, nullptr) == JNI_OK && registered_native,
                        "admitted RegisterNatives wraps callback");
                bool noncode_refused = false;
                try {
                    (void)native_trampoline(reinterpret_cast<void*>(table), "I", fixture_domain);
                } catch (const std::runtime_error&) {
                    noncode_refused = true;
                }
                require(noncode_refused, "JNI trampoline refuses data pointer");
                void* same =
                    native_trampoline(reinterpret_cast<void*>(eighth_target), "I", fixture_domain);
                ordinary_native = native_trampoline(reinterpret_cast<void*>(eighth_target), "I");
                require(same == registered_native && same != ordinary_native,
                        "registered JNI trampoline retains domain and cache isolation");
                auto alternate = std::make_shared<AdmissionDomain>();
                alternate->add_image(owner_ptr, memory_lease);
                alternate->seal();
                {
                    AdmissionDomain::Invocation outer(fixture_domain);
                    require(AdmissionDomain::current_invocation() == fixture_domain,
                            "outer JNI domain current");
                    {
                        AdmissionDomain::Invocation inner(alternate);
                        require(AdmissionDomain::current_invocation() == alternate,
                                "nested JNI domain current");
                    }
                    require(AdmissionDomain::current_invocation() == fixture_domain,
                            "outer JNI domain restored");
                }
                require(!AdmissionDomain::current_invocation(), "nested JNI domains fully drained");
                require(same != native_trampoline(reinterpret_cast<void*>(eighth_target), "I",
                                                  alternate),
                        "distinct admission domains have distinct JNI trampolines");
                alternate->close();
                bool closed_cache_refused = false;
                try {
                    (void)native_trampoline(reinterpret_cast<void*>(eighth_target), "I", alternate);
                } catch (const ElfError&) {
                    closed_cache_refused = true;
                }
                require(closed_cache_refused,
                        "closed admission domain cannot reuse a cached trampoline");
                auto registered = reinterpret_cast<Native>(registered_native);
                require(registered(&fake_host, nullptr) == JNI_VERSION_1_6,
                        "registered JNI callback uses admitted entry");
                using OnLoad = jint (*)(JavaVM*, void*);
                auto onload = reinterpret_cast<OnLoad>(
                    onload_trampoline(reinterpret_cast<void*>(eighth_target), false,
                                      "admission-fixture", fixture_domain));
                auto* vm_shadow = shadow_vm(&host_vm);
                auto* writable_vm_table = const_cast<JNIInvokeInterface*>(vm_shadow->functions);
                auto saved_get_env = writable_vm_table->GetEnv;
                void* spoof = closure([](Capture&) {});
                writable_vm_table->GetEnv =
                    reinterpret_cast<decltype(writable_vm_table->GetEnv)>(spoof);
                jint onload_result = onload(&host_vm, nullptr);
                writable_vm_table->GetEnv = saved_get_env;
                require(onload_result == JNI_VERSION_1_6, "admitted JNI_OnLoad trampoline");
                {
                    AdmissionDomain::Invocation invocation(fixture_domain);
                    uintptr_t sp;
                    __asm__ volatile("mov %0, sp" : "=r"(sp));
                    bool rejected = false;
                    try {
                        (void)fixture_domain->route(entry + 12, reinterpret_cast<uintptr_t>(spoof),
                                                    sp);
                    } catch (const ElfError&) {
                        rejected = true;
                    }
                    require(rejected,
                            "guest-modified JNI table cannot authorize an unrelated closure");
                }
            }
            require(admissions == 4 && transfers == 522, "admitted JNI entry and callbacks");
            {
                auto* staged_memory = static_cast<uint8_t*>(
                    mmap(nullptr, mapping_size, PROT_READ | PROT_WRITE | PROT_EXEC,
                         MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
                require(staged_memory != MAP_FAILED, "staged fixture mapping");
                std::shared_ptr<void> staged_lease(staged_memory, [mapping_size](void* p) {
                    if (munmap(p, mapping_size))
                        std::abort();
                });
                uintptr_t staged_base = uintptr_t(staged_memory),
                          staged_end = staged_base + elf.span;
                GuestImageReservation staged_reservation(staged_base, mapping_size);
                populate(staged_memory);
                sys_icache_invalidate(staged_memory, elf.span);
                auto staged_owner = std::make_shared<AdmissionSession>(
                    elf, bytes, staged_base, staged_end, pool_size, uintptr_t(&fixture_syscall),
                    uintptr_t(&fixture_validate));
                staged_owner->initialize();
                auto staged_domain = std::make_shared<AdmissionDomain>();
                staged_domain->add_image(owner_ptr, memory_lease);
                staged_domain->seal();
                uintptr_t staged_target = staged_base + (first_target - base);
                void* staged_entry = closure([](Capture&) {});
                void* private_entry = closure([](Capture&) {});
                void* public_entry = closure([](Capture&) {});
                {
                    AdmissionDomain::StagedExtension stage(
                        staged_domain, {{staged_owner, staged_lease}}, {staged_entry}, {});
                    require(staged_domain->owns_session(staged_owner.get()),
                            "staging thread sees private image");
                    bool refused = false;
                    try {
                        AdmissionDomain::StagedExtension nested(
                            staged_domain, {{staged_owner, staged_lease}}, {}, {});
                    } catch (const ElfError&) {
                        refused = true;
                    }
                    require(refused, "nested staged extension refused");
                    refused = false;
                    try {
                        staged_domain->close();
                    } catch (const ElfError&) {
                        refused = true;
                    }
                    require(refused, "staging owner cannot close its domain");
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        uintptr_t sp;
                        __asm__ volatile("mov %0, sp" : "=r"(sp));
                        AdmissionDomain::Invocation invocation(staged_domain);
                        require(!AdmissionDomain::reserve_current(),
                                "staged private policy cannot escape to worker");
                        staged_domain->admit_entry(staged_target);
                        require(
                            staged_domain->route(entry + 12, uintptr_t(staged_entry), sp).target ==
                                    uintptr_t(staged_entry) &&
                                staged_domain->register_closure(private_entry) &&
                                staged_domain->route(entry + 12, uintptr_t(private_entry), sp)
                                        .target == uintptr_t(private_entry),
                            "private stage admits image and private host entries");
                    }
                    std::atomic<bool> observer_ok{false};
                    std::thread observer([&] {
                        try {
                            GuestTlsCleanupScope cleanup;
                            prepare_guest_tls();
                            uintptr_t sp;
                            __asm__ volatile("mov %0, sp" : "=r"(sp));
                            AdmissionDomain::Invocation invocation(staged_domain);
                            bool image_refused = false, closure_refused = false,
                                 batch_refused = false;
                            try {
                                staged_domain->admit_entry(staged_target);
                            } catch (const ElfError&) {
                                image_refused = true;
                            }
                            try {
                                (void)staged_domain->route(entry + 12, uintptr_t(private_entry),
                                                           sp);
                            } catch (const ElfError&) {
                                closure_refused = true;
                            }
                            try {
                                staged_domain->extend({{staged_owner, staged_lease}}, {}, {});
                            } catch (const ElfError&) {
                                batch_refused = true;
                            }
                            observer_ok = !staged_domain->owns_session(staged_owner.get()) &&
                                          image_refused && closure_refused && batch_refused &&
                                          staged_domain->register_closure(public_entry);
                        } catch (...) {
                        }
                    });
                    observer.join();
                    require(observer_ok, "other thread cannot observe private policy and can "
                                         "register public closure");
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        uintptr_t sp;
                        __asm__ volatile("mov %0, sp" : "=r"(sp));
                        AdmissionDomain::Invocation invocation(staged_domain);
                        require(
                            staged_domain->route(entry + 12, uintptr_t(public_entry), sp).target ==
                                uintptr_t(public_entry),
                            "staging thread sees concurrently published closure");
                    }
                }
                require(!staged_domain->owns_session(staged_owner.get()),
                        "rollback hides staged image");
                {
                    GuestTlsCleanupScope cleanup;
                    prepare_guest_tls();
                    uintptr_t sp;
                    __asm__ volatile("mov %0, sp" : "=r"(sp));
                    AdmissionDomain::Invocation invocation(staged_domain);
                    bool refused = false;
                    try {
                        (void)staged_domain->route(entry + 12, uintptr_t(private_entry), sp);
                    } catch (const ElfError&) {
                        refused = true;
                    }
                    require(
                        refused &&
                            staged_domain->route(entry + 12, uintptr_t(public_entry), sp).target ==
                                uintptr_t(public_entry),
                        "rollback discards private closure and preserves public registration");
                }
                {
                    AdmissionDomain::StagedExtension stage(
                        staged_domain, {{staged_owner, staged_lease}}, {staged_entry}, {});
                    {
                        AdmissionDomain::Invocation invocation(staged_domain);
                        require(staged_domain->register_closure(private_entry),
                                "new private closure registered");
                    }
                    stage.commit();
                    bool refused = false;
                    try {
                        stage.commit();
                    } catch (const ElfError&) {
                        refused = true;
                    }
                    require(refused, "staging commit is single use");
                }
                require(staged_domain->owns_session(staged_owner.get()),
                        "commit publishes staged image");
                {
                    GuestTlsCleanupScope cleanup;
                    prepare_guest_tls();
                    uintptr_t sp;
                    __asm__ volatile("mov %0, sp" : "=r"(sp));
                    AdmissionDomain::Invocation invocation(staged_domain);
                    staged_domain->admit_entry(staged_target);
                    require(
                        staged_domain->route(entry + 12, uintptr_t(private_entry), sp).target ==
                                uintptr_t(private_entry) &&
                            staged_domain->route(entry + 12, uintptr_t(public_entry), sp).target ==
                                uintptr_t(public_entry),
                        "commit merges staged and concurrently published closures");
                }
                {
                    auto closing_domain = std::make_shared<AdmissionDomain>();
                    closing_domain->add_image(owner_ptr, memory_lease);
                    closing_domain->seal();
                    std::atomic<bool> closed{false};
                    std::thread closer;
                    bool refused_entry = false, refused_commit = false, waited = false;
                    {
                        AdmissionDomain::StagedExtension stage(
                            closing_domain, {{staged_owner, staged_lease}}, {}, {});
                        closer = std::thread([&] {
                            closing_domain->close();
                            closed = true;
                        });
                        auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
                        while (!refused_entry && std::chrono::steady_clock::now() < deadline) {
                            try {
                                AdmissionDomain::Invocation probe(closing_domain);
                            } catch (const ElfError&) {
                                refused_entry = true;
                            }
                            std::this_thread::yield();
                        }
                        waited = !closed.load();
                        try {
                            stage.commit();
                        } catch (const ElfError&) {
                            refused_commit = true;
                        }
                    }
                    closer.join();
                    require(refused_entry && refused_commit && waited && closed,
                            "close drains staged transaction and prevents publication");
                }
                staged_domain->close();
            }
            auto* other = static_cast<uint8_t*>(mmap(nullptr, mapping_size,
                                                     PROT_READ | PROT_WRITE | PROT_EXEC,
                                                     MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
            require(other != MAP_FAILED, "second fixture mapping");
            std::shared_ptr<void> other_lease(other, [mapping_size](void* p) {
                if (munmap(p, mapping_size))
                    std::abort();
            });
            cross_base = uintptr_t(other);
            cross_end = cross_base + elf.span;
            {
                GuestImageReservation other_reservation(cross_base, mapping_size);
                populate(other);
                sys_icache_invalidate(other, elf.span);
                {
                    auto other_owner = std::make_shared<AdmissionSession>(
                        elf, bytes, cross_base, cross_end, pool_size, uintptr_t(&fixture_syscall),
                        uintptr_t(&fixture_validate));
                    other_owner->initialize();
                    fixture_domain->close();
                    fixture_domain = std::make_shared<AdmissionDomain>();
                    fixture_domain->add_image(owner_ptr, memory_lease);
                    bool extension_refused = false;
                    try {
                        fixture_domain->extend({{other_owner, other_lease}}, {}, {});
                    } catch (const ElfError&) {
                        extension_refused = true;
                    }
                    require(extension_refused, "unsealed domain refuses extension");
                    fixture_domain->seal();
                    using Registration = AdmissionDomain::ImageRegistration;
                    Registration additional{other_owner, other_lease};
                    extension_refused = false;
                    try {
                        fixture_domain->extend({additional},
                                               {static_cast<uint8_t*>(host_entry) + 4}, {});
                    } catch (const ElfError&) {
                        extension_refused = true;
                    }
                    require(extension_refused && !fixture_domain->owns_session(other_owner.get()),
                            "invalid extension entry publishes no image");
                    extension_refused = false;
                    try {
                        fixture_domain->extend({additional, {owner_ptr, memory_lease}},
                                               {host_entry}, {});
                    } catch (const ElfError&) {
                        extension_refused = true;
                    }
                    require(extension_refused && !fixture_domain->owns_session(other_owner.get()),
                            "overlapping extension rolls back whole batch");
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        uintptr_t sp;
                        __asm__ volatile("mov %0, sp" : "=r"(sp));
                        AdmissionDomain::Invocation invocation(fixture_domain);
                        extension_refused = false;
                        try {
                            (void)fixture_domain->route(entry + 12, uintptr_t(host_entry), sp);
                        } catch (const ElfError&) {
                            extension_refused = true;
                        }
                        require(extension_refused, "failed extension publishes no host entry");
                    }
                    std::atomic<unsigned> readers{0}, read_passes{0};
                    std::atomic<bool> finish_readers{false}, reader_failed{false};
                    std::atomic<bool> start_register{false}, register_ok{false};
                    void* concurrent_entry = closure([](Capture&) {});
                    std::thread registrar([&] {
                        try {
                            AdmissionDomain::Invocation invocation(fixture_domain);
                            while (!start_register.load(std::memory_order_acquire))
                                std::this_thread::yield();
                            register_ok = fixture_domain->register_closure(concurrent_entry);
                        } catch (...) {
                            reader_failed = true;
                        }
                    });
                    std::vector<std::thread> readers_threads;
                    for (int i = 0; i < 4; ++i)
                        readers_threads.emplace_back([&] {
                            ++readers;
                            try {
                                AdmissionDomain::Invocation invocation(fixture_domain);
                                while (!finish_readers.load(std::memory_order_acquire)) {
                                    fixture_domain->admit_entry(entry);
                                    ++read_passes;
                                }
                            } catch (...) {
                                reader_failed = true;
                            }
                        });
                    auto reader_deadline =
                        std::chrono::steady_clock::now() + std::chrono::seconds(3);
                    while ((readers.load(std::memory_order_acquire) != 4 || !read_passes.load()) &&
                           !reader_failed.load() &&
                           std::chrono::steady_clock::now() < reader_deadline)
                        std::this_thread::yield();
                    if (readers != 4 || !read_passes || reader_failed) {
                        finish_readers = true;
                        start_register = true;
                        for (auto& thread : readers_threads)
                            thread.join();
                        registrar.join();
                        throw std::runtime_error("concurrent extension readers did not start");
                    }
                    start_register.store(true, std::memory_order_release);
                    try {
                        fixture_domain->extend({additional}, {host_entry}, {});
                    } catch (...) {
                        finish_readers = true;
                        for (auto& thread : readers_threads)
                            thread.join();
                        registrar.join();
                        throw;
                    }
                    finish_readers.store(true, std::memory_order_release);
                    for (auto& thread : readers_threads)
                        thread.join();
                    registrar.join();
                    require(!reader_failed && register_ok && read_passes > 0 &&
                                fixture_domain->owns_session(other_owner.get()),
                            "extension publishes image while existing invocations continue");
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        uintptr_t sp;
                        __asm__ volatile("mov %0, sp" : "=r"(sp));
                        AdmissionDomain::Invocation invocation(fixture_domain);
                        require(
                            fixture_domain->route(entry + 12, uintptr_t(host_entry), sp).target ==
                                uintptr_t(host_entry),
                            "extension publishes authorized host entry with image");
                        require(fixture_domain->route(entry + 12, uintptr_t(concurrent_entry), sp)
                                        .target == uintptr_t(concurrent_entry),
                                "concurrent closure registration survives extension publication");
                    }
                    std::weak_ptr<void> retained_other = other_lease;
                    additional.session.reset();
                    additional.mapping_lease.reset();
                    other_lease.reset();
                    other_owner.reset();
                    require(!retained_other.expired(), "domain retains second mapping lease");
                    uintptr_t cross_target = cross_base + (first_target - base);
                    {
                        JitWriteScope writable;
                        std::memcpy(reinterpret_cast<void*>(table), &cross_target,
                                    sizeof(cross_target));
                    }
                    {
                        volatile uint8_t stack_margin[2048]{};
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        require(fixture_call(reinterpret_cast<void*>(entry)) == 41 &&
                                    !stack_margin[0],
                                "cross-image indirect transfer");
                    }
                    require(admissions == 5 && transfers == 524,
                            "cross-image target admitted once");
                    {
                        uintptr_t host = uintptr_t(host_entry);
                        JitWriteScope writable;
                        std::memcpy(reinterpret_cast<void*>(table), &fourth_target,
                                    sizeof(fourth_target));
                        std::memcpy(reinterpret_cast<void*>(host_slot), &host, sizeof(host));
                    }
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        require(fixture_call(reinterpret_cast<void*>(entry)) == 102,
                                "exact host callback and guest continuation");
                    }
                    require(admissions == 6 && transfers == 527 && host_calls == 1,
                            "exact host call admitted and returned");
                    {
                        JitWriteScope writable;
                        std::memcpy(reinterpret_cast<void*>(table), &fifth_target,
                                    sizeof(fifth_target));
                    }
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        std::vector<Argument> arguments;
                        for (int i = 1; i <= 10; ++i)
                            arguments.push_back(Argument::integer(i));
                        auto result = invoke_admitted(fixture_domain,
                                                      reinterpret_cast<void*>(entry), arguments);
                        require(result.result_integers[0] == 55,
                                "admitted invoke preserves register and stack arguments");
                    }
                    {
                        JitWriteScope writable;
                        std::memcpy(reinterpret_cast<void*>(table), &sixth_target,
                                    sizeof(sixth_target));
                    }
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        auto result =
                            invoke_admitted(fixture_domain, reinterpret_cast<void*>(entry),
                                            {Argument::real(2.25), Argument::real(3.5)});
                        require(result.double_result() == 5.75,
                                "admitted invoke preserves floating-point arguments and result");
                    }
                    require(admissions == 8 && transfers == 531,
                            "admitted CallFrame targets and returns");
                    {
                        GuestTlsCleanupScope cleanup;
                        prepare_guest_tls();
                        auto result = invoke_admitted(fixture_domain,
                                                      reinterpret_cast<void*>(seventh_target), {});
                        require(
                            result.result_integers[0] == 7 && transfers == 532 &&
                                !owner.admit(seventh_target),
                            "previously undiscovered direct guest entry admitted before invoke");
                    }
                    bool invalid_invoke = false;
                    try {
                        (void)invoke_admitted(fixture_domain, host_entry, {});
                    } catch (const ElfError&) {
                        invalid_invoke = true;
                    }
                    require(invalid_invoke, "admitted invoke refuses host entry");
                    {
                        JitWriteScope writable;
                        std::memcpy(reinterpret_cast<void*>(table), &fourth_target,
                                    sizeof(fourth_target));
                    }
                    // A reservation made by a live parent must bridge the gap before a
                    // worker installs its invocation, even if close starts meanwhile.
                    {
                        auto handoff = std::make_shared<AdmissionDomain>();
                        handoff->add_image(owner_ptr, memory_lease);
                        handoff->seal();
                        std::unique_ptr<AdmissionDomain::Reservation> reservation;
                        {
                            AdmissionDomain::Invocation parent(handoff);
                            reservation = AdmissionDomain::reserve_current();
                            require(bool(reservation), "live parent reserves worker admission");
                        }
                        std::atomic<bool> closed_handoff{false}, worker_started{false},
                            release_worker{false};
                        std::atomic<bool> worker_ok{false};
                        std::thread closer([&] {
                            handoff->close();
                            closed_handoff = true;
                        });
                        bool closing = false;
                        auto limit = std::chrono::steady_clock::now() + std::chrono::seconds(3);
                        while (!closing && std::chrono::steady_clock::now() < limit) {
                            try {
                                AdmissionDomain::Invocation probe(handoff);
                            } catch (const ElfError&) {
                                closing = true;
                            }
                            std::this_thread::yield();
                        }
                        const bool held_before_start = !closed_handoff.load();
                        std::thread worker([&] {
                            try {
                                AdmissionDomain::Invocation adopted(std::move(reservation));
                                worker_ok = AdmissionDomain::current_invocation() == handoff &&
                                            !AdmissionDomain::reserve_current();
                                worker_started = true;
                                while (!release_worker)
                                    std::this_thread::yield();
                            } catch (...) {
                                worker_started = true;
                            }
                        });
                        while (!worker_started && std::chrono::steady_clock::now() < limit)
                            std::this_thread::yield();
                        const bool held_in_worker = !closed_handoff.load();
                        release_worker = true;
                        worker.join();
                        closer.join();
                        require(closing && held_before_start && worker_started && held_in_worker &&
                                    worker_ok && closed_handoff,
                                "reservation transfers into worker while close drains and refuses "
                                "descendants");
                    }
                    {
                        require(!AdmissionDomain::reserve_current(),
                                "unowned thread cannot reserve admission");
                        auto cancelled = std::make_shared<AdmissionDomain>();
                        cancelled->add_image(owner_ptr, memory_lease);
                        cancelled->seal();
                        std::unique_ptr<AdmissionDomain::Reservation> reservation;
                        {
                            AdmissionDomain::Invocation parent(cancelled);
                            reservation = AdmissionDomain::reserve_current();
                        }
                        require(bool(reservation), "create cancellable admission reservation");
                        std::atomic<bool> completed{false};
                        std::thread closer([&] {
                            cancelled->close();
                            completed = true;
                        });
                        bool closing = false;
                        auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
                        while (!closing && std::chrono::steady_clock::now() < deadline) {
                            try {
                                AdmissionDomain::Invocation probe(cancelled);
                            } catch (const ElfError&) {
                                closing = true;
                            }
                            std::this_thread::yield();
                        }
                        const bool held = !completed.load();
                        reservation.reset();
                        closer.join();
                        require(closing && held && completed,
                                "discarded reservation releases domain close");
                    }
                    auto draining = fixture_domain;
                    hold_host.store(true, std::memory_order_release);
                    std::atomic<bool> holder_passed{false};
                    std::atomic<unsigned> closing_started{0}, closed{0};
                    std::thread holder([&] {
                        try {
                            GuestTlsCleanupScope cleanup;
                            prepare_guest_tls();
                            auto result =
                                invoke_admitted(draining, reinterpret_cast<void*>(entry), {});
                            holder_passed = result.result_integers[0] == 102;
                        } catch (...) {
                        }
                    });
                    auto enter_deadline =
                        std::chrono::steady_clock::now() + std::chrono::seconds(3);
                    while (!host_entered.load(std::memory_order_acquire) &&
                           std::chrono::steady_clock::now() < enter_deadline)
                        std::this_thread::yield();
                    if (!host_entered.load(std::memory_order_acquire)) {
                        release_host.store(true, std::memory_order_release);
                        holder.join();
                        throw std::runtime_error("guest did not enter held host closure");
                    }
                    std::vector<std::thread> closers;
                    for (unsigned i = 0; i < 2; ++i)
                        closers.emplace_back([&] {
                            ++closing_started;
                            draining->close();
                            ++closed;
                        });
                    while (closing_started != 2)
                        std::this_thread::yield();
                    bool new_entry_refused = false;
                    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
                    while (!new_entry_refused && std::chrono::steady_clock::now() < deadline) {
                        try {
                            AdmissionDomain::Invocation probe(draining);
                        } catch (const ElfError&) {
                            new_entry_refused = true;
                        }
                        std::this_thread::yield();
                    }
                    bool waited_for_holder = closed == 0;
                    bool extension_during_close_refused = false;
                    try {
                        draining->extend({{owner_ptr, memory_lease}}, {}, {});
                    } catch (const ElfError& error) {
                        extension_during_close_refused =
                            std::string(error.what()) == "guest admission extension unavailable";
                    }
                    release_host.store(true, std::memory_order_release);
                    holder.join();
                    for (auto& closer : closers)
                        closer.join();
                    require(
                        new_entry_refused && extension_during_close_refused && waited_for_holder &&
                            closed == 2 && holder_passed && admissions == 8 && transfers == 536 &&
                            host_calls == 2,
                        "domain close drains guest and inherited JNI reentry inside host closure");
                    domain_refused = false;
                    try {
                        AdmissionDomain::Invocation after_close(draining);
                    } catch (const ElfError&) {
                        domain_refused = true;
                    }
                    require(domain_refused, "closed domain refuses new invocation");
                    require(!retained_other.expired(),
                            "closed domain retains mapping until destruction");
                    draining.reset();
                    fixture_domain.reset();
                    require(retained_other.expired(),
                            "mapping lease released after domain destruction");
                }
            }
            other_lease.reset();
            cross_base = cross_end = 0;
        }
        memory_lease.reset();
        std::cout << "M5_ADMISSION_SESSION_PASS writable_dispatch=1 tls=1 syscall=1 concurrent=8 "
                     "repeats=32 dynamic_targets=8 nested_call=1 "
                     "cross_image=1 host_call=1 callframe_integer=10 callframe_float=1 "
                     "direct_entry=1 dynamic_closures=64 exhaustion_rollback=1 site_checked=1 "
                     "return_scope=1 domain_drain_during_host=1 inherited_jni_reentry=1 "
                     "admitted_jni_register=1 admitted_onload=1 "
                     "jni_cache_domains=1 external_exact=1 fixture_only=1\n";
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
