// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/elf.hpp"
#include "shiraz/darwin/guest/admission_publish.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include <atomic>
#include <cstring>
#include <cstdlib>
#include <iostream>
#include <libkern/OSCacheControl.h>
#include <stdexcept>
#include <sys/mman.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
extern "C" char shiraz_tpidr_test_start[], shiraz_tpidr_test_site[], shiraz_tpidr_test_end[];
extern "C" uintptr_t shiraz_indirect_test_validator(uintptr_t, uintptr_t, uintptr_t) noexcept;
struct alignas(16) Result {
    uint64_t integers[31], sp, nzcv, fpcr, fpsr, retired_scratch_nonzero;
    uint8_t vectors[32][16];
    uint64_t expected_sp, original_x18;
};
static_assert(sizeof(Result) == 816 && offsetof(Result, vectors) == 288);
static uintptr_t expected_target, expected_site;
static unsigned observed;
static bool full_snapshot;
extern "C" uintptr_t validate_transfer(uintptr_t target, uintptr_t site, uintptr_t sp) noexcept {
    if (target != expected_target || site != expected_site || (sp & 15))
        std::abort();
    if (full_snapshot) {
        uintptr_t expected_sp;
        std::memcpy(&expected_sp, reinterpret_cast<const void*>(sp + 800), sizeof(expected_sp));
        if (sp != expected_sp)
            std::abort();
    }
    ++observed;
    return target;
}
extern "C" uintptr_t redirect_transfer(uintptr_t target, uintptr_t, uintptr_t) noexcept {
    return target + 4;
}
static void require(bool value, const char* what) {
    if (!value)
        throw std::runtime_error(what);
}
static void put(uint8_t* p, uint32_t word) {
    std::memcpy(p, &word, 4);
}
int main() {
    try {
        constexpr size_t page = 16384;
        auto* memory = static_cast<uint8_t*>(mmap(nullptr, page, PROT_READ | PROT_WRITE | PROT_EXEC,
                                                  MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
        require(memory != MAP_FAILED, "JIT allocation");
        {
            auto* site = memory + 16;
            auto* second = memory + 64;
            auto* island = memory + 8192;
            auto* target = memory + 512;
            constexpr uint32_t original = 0xd61f0000u; // br x0
            expected_target = uintptr_t(target);
            expected_site = uintptr_t(site);
            observed = 0;
            auto first = plan_indirect_transfer(uintptr_t(site), original, uintptr_t(island),
                                                uintptr_t(&shiraz_indirect_test_validator));
            auto next = plan_indirect_transfer(uintptr_t(second), original, uintptr_t(island + 80),
                                               uintptr_t(&shiraz_indirect_test_validator));
            AdmissionPatch a{16,       uintptr_t(site),   uintptr_t(island),
                             original, first.replacement, first.island,
                             80};
            AdmissionPatch b{64,       uintptr_t(second), uintptr_t(island + 80),
                             original, next.replacement,  next.island,
                             80};
            {
                JitWriteScope writable;
                put(memory, 0xa9be7bfdu);
                put(memory + 4, 0xf9000bf3u);
                put(memory + 8, 0x910003fdu);
                put(memory + 12, 0xaa0003e0u);
                put(site, original);
                put(second, original);
                put(target, 0xf9400bf3u);
                put(target + 4, 0xa8c27bfdu);
                put(target + 8, 0xd2800540u);
                put(target + 12, 0xd65f03c0u);
                std::memset(island, 0xa5, 160);
            }
            sys_icache_invalidate(memory, 8192);
            AdmissionPublisher publisher(uintptr_t(memory), 8192, uintptr_t(island), 8192);
            auto expect_refusal = [&](std::vector<AdmissionPatch> batch) {
                bool refused = false;
                try {
                    publisher.publish(batch);
                } catch (const ElfError&) {
                    refused = true;
                }
                require(refused, "unsafe publication must fail");
                uint32_t one, two;
                std::memcpy(&one, site, 4);
                std::memcpy(&two, second, 4);
                require(one == original && two == original && island[0] == 0xa5,
                        "failed preflight leaves all sites and islands untouched");
            };
            auto stale = b;
            stale.original ^= 1;
            expect_refusal({a, stale});
            auto wrong_branch = b;
            wrong_branch.replacement ^= 1;
            expect_refusal({a, wrong_branch});
            auto overlap = b;
            overlap.island_address = a.island_address;
            overlap.replacement =
                plan_indirect_transfer(uintptr_t(second), original, uintptr_t(island),
                                       uintptr_t(&shiraz_indirect_test_validator))
                    .replacement;
            expect_refusal({a, overlap});
            expect_refusal({a, a});
            std::atomic<unsigned> published{0}, refused{0};
            std::vector<std::thread> writers;
            for (unsigned i = 0; i < 8; ++i)
                writers.emplace_back([&] {
                    try {
                        publisher.publish({a, b});
                        ++published;
                    } catch (const ElfError&) {
                        ++refused;
                    }
                });
            for (auto& writer : writers)
                writer.join();
            require(published == 1 && refused == 7, "serialized competing publications");
            require(reinterpret_cast<long (*)(void*)>(memory)(target) == 42 && observed == 1,
                    "published transfer executes after writer synchronization");
            auto* third = memory + 128;
            {
                JitWriteScope writable;
                put(third, original);
            }
            auto reused_plan = plan_indirect_transfer(uintptr_t(third), original, uintptr_t(island),
                                                      uintptr_t(&shiraz_indirect_test_validator));
            AdmissionPatch reused{128,      uintptr_t(third),        uintptr_t(island),
                                  original, reused_plan.replacement, reused_plan.island,
                                  80};
            bool refused_reuse = false;
            try {
                publisher.publish({reused});
            } catch (const ElfError&) {
                refused_reuse = true;
            }
            uint32_t unchanged;
            std::memcpy(&unchanged, third, sizeof(unchanged));
            require(refused_reuse && unchanged == original, "published islands cannot be reused");
            std::cout << "M5_ADMISSION_PUBLICATION_PASS refusals_untouched=1 competing_writers=8 "
                         "island_reuse_refused=1 published_execution=1 runtime_gate=not_tested\n";
        }
        for (uint32_t opcode : {0xd61f0000u, 0xd63f0000u, 0xd65f0000u}) {
            for (uint32_t reg : {0u, 8u, 16u, 17u, 19u, 29u, 30u}) {
                if (opcode == 0xd63f0000u && reg == 30)
                    continue;
                auto* site = memory + 16;
                auto* island = memory + 256;
                auto* target = memory + 512;
                expected_target = uintptr_t(target);
                expected_site = uintptr_t(site);
                observed = 0;
                {
                    JitWriteScope writable;
                    put(memory, 0xa9be7bfdu);            // stp x29,x30,[sp,#-32]!
                    put(memory + 4, 0xf9000bf3u);        // str x19,[sp,#16]
                    put(memory + 8, 0x910003fdu);        // mov x29,sp
                    put(memory + 12, 0xaa0003e0u | reg); // mov Xn,x0
                    auto plan = plan_indirect_transfer(uintptr_t(site), opcode | (reg << 5),
                                                       uintptr_t(island),
                                                       uintptr_t(&shiraz_indirect_test_validator));
                    put(site, plan.replacement);
                    put(memory + 20, 0xf9400bf3u); // ldr x19,[sp,#16]
                    put(memory + 24, 0xa8c27bfdu); // ldp x29,x30,[sp],#32
                    put(memory + 28, 0xd65f03c0u); // ret
                    std::memcpy(island, plan.island.data(), plan.island.size());
                    if (opcode == 0xd63f0000u) {
                        put(target, 0xd2800540u); // mov x0,#42
                        put(target + 4, 0xd65f03c0u);
                    } else {
                        put(target, 0xf9400bf3u); // tail transfer restores original caller
                        put(target + 4, 0xa8c27bfdu);
                        put(target + 8, 0xd2800540u);
                        put(target + 12, 0xd65f03c0u);
                    }
                }
                sys_icache_invalidate(memory, page);
                auto execute = reinterpret_cast<long (*)(void*)>(memory);
                require(execute(target) == 42 && observed == 1, "transfer execution and validator");
            }
        }
        // Reuse the complete register/vector snapshot fixture. ADR replaces its
        // reserved NOP, setting Xn to the instruction immediately after the site.
        const size_t length = uintptr_t(shiraz_tpidr_test_end) - uintptr_t(shiraz_tpidr_test_start);
        const size_t site_offset =
            uintptr_t(shiraz_tpidr_test_site) - uintptr_t(shiraz_tpidr_test_start);
        require(length < 4096 && site_offset >= 4 && site_offset + 4 < length,
                "bounded snapshot fixture");
        unsigned full_cases = 0;
        full_snapshot = true;
        for (uint32_t opcode : {0xd61f0000u, 0xd63f0000u, 0xd65f0000u}) {
            for (uint32_t reg = 0; reg < 31; ++reg) {
                if (reg == 18 || (opcode == 0xd63f0000u && reg == 30))
                    continue;
                auto* start = memory + 1024;
                auto* site = start + site_offset;
                auto* island = memory + 8192;
                expected_target = uintptr_t(site + 4);
                expected_site = uintptr_t(site);
                observed = 0;
                {
                    JitWriteScope writable;
                    std::memcpy(start, shiraz_tpidr_test_start, length);
                    uint32_t previous;
                    std::memcpy(&previous, site - 4, 4);
                    require(previous == 0xd503201fu, "snapshot ADR slot");
                    put(site - 4, 0x10000040u | reg); // adr Xn, site+4
                    auto plan = plan_indirect_transfer(uintptr_t(site), opcode | (reg << 5),
                                                       uintptr_t(island),
                                                       uintptr_t(&shiraz_indirect_test_validator));
                    put(site, plan.replacement);
                    std::memcpy(island, plan.island.data(), plan.island.size());
                }
                sys_icache_invalidate(memory, page);
                Result result{};
                reinterpret_cast<void (*)(Result*)>(start)(&result);
                require(observed == 1, "snapshot validator invocation");
                for (unsigned i = 0; i < 31; ++i) {
                    uint64_t expected = i == 18 ? result.original_x18
                                        : (i == reg || (opcode == 0xd63f0000u && i == 30))
                                            ? expected_target
                                            : 0x6677445522331000ULL + i;
                    require(result.integers[i] == expected, "snapshot guest register");
                }
                require(result.sp == result.expected_sp && !(result.sp & 15) &&
                            !result.retired_scratch_nonzero,
                        "snapshot stack and scratch");
                require(result.nzcv == 0xa0000000 && result.fpcr == 0x02c00000 &&
                            result.fpsr == 0x0800001f,
                        "snapshot flags and floating-point state");
                for (unsigned i = 0; i < 32; ++i)
                    for (unsigned j = 0; j < 16; ++j)
                        require(result.vectors[i][j] == 0x40 + i, "snapshot vector register");
                ++full_cases;
            }
        }
        require(full_cases == 89, "all supported transfer/register pairs");
        for (uint32_t word : {0xd63f03c0u, 0xd61f0240u, 0xd61f03e0u, 0xd71f0800u}) {
            bool rejected = false;
            try {
                (void)plan_indirect_transfer(uintptr_t(memory), word, uintptr_t(memory + 256),
                                             uintptr_t(&validate_transfer));
            } catch (const ElfError&) {
                rejected = true;
            }
            require(rejected, "unsupported transfer must fail");
        }
        // Redirection violates the validator contract and must trap before branching.
        {
            JitWriteScope writable;
            auto plan =
                plan_indirect_transfer(uintptr_t(memory + 16), 0xd61f0000u, uintptr_t(memory + 256),
                                       uintptr_t(&redirect_transfer));
            put(memory + 12, 0xaa0003e0u);
            put(memory + 16, plan.replacement);
            std::memcpy(memory + 256, plan.island.data(), plan.island.size());
        }
        sys_icache_invalidate(memory, page);
        pid_t child = fork();
        require(child >= 0, "fork");
        if (!child) {
            reinterpret_cast<long (*)(void*)>(memory)(memory + 512);
            _exit(0);
        }
        int status = 0;
        require(waitpid(child, &status, 0) == child && WIFSIGNALED(status), "redirect must trap");
        require(munmap(memory, page) == 0, "unmap");
        std::cout << "M5_INDIRECT_FIXTURE_PASS jumps=7 calls=6 returns=7 full_state_cases=89 "
                     "redirect_trapped=1 execution_claim=fixture_only\n";
    } catch (const std::exception& e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
