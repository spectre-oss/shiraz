// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/admission_publish.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include <algorithm>
#include <cstring>
#include <libkern/OSCacheControl.h>
namespace shiraz::guest {
namespace {
bool contained(uintptr_t base, size_t length, uintptr_t address, size_t width) {
    return address >= base && address - base <= length && width <= length - (address - base);
}
} // namespace
AdmissionPublisher::AdmissionPublisher(uintptr_t code, size_t code_size, uintptr_t islands,
                                       size_t island_size)
    : code_(code), islands_(islands), code_size_(code_size), island_size_(island_size) {
    if (!code || !islands || (code & 3) || (islands & 15) || code_size < 4 || !island_size ||
        code_size > (512ull << 20) || island_size > (512ull << 20) ||
        code > UINTPTR_MAX - code_size || islands > UINTPTR_MAX - island_size ||
        (code < islands + island_size && islands < code + code_size))
        throw ElfError("invalid admission publication regions");
}
void AdmissionPublisher::publish(const std::vector<AdmissionPatch>& patches) {
    std::lock_guard lock(mutex_);
    constexpr size_t limit = 1u << 20;
    if (used_islands_.size() > limit || patches.size() > limit - used_islands_.size())
        throw ElfError("admission publication patch limit");
    auto next_islands = used_islands_;
    std::vector<uintptr_t> sites;
    sites.reserve(patches.size());
    next_islands.reserve(next_islands.size() + patches.size());
    for (const auto& p : patches) {
        if ((p.host_address & 3) || !contained(code_, code_size_, p.host_address, 4))
            throw ElfError("admission publication site outside code");
        if (__atomic_load_n(reinterpret_cast<const uint32_t*>(p.host_address), __ATOMIC_ACQUIRE) !=
            p.original)
            throw ElfError("admission publication source changed");
        sites.push_back(p.host_address);
        if (!p.island_size) {
            if (p.original != 0xd53bd05fu || p.replacement != 0xd503201fu)
                throw ElfError("invalid island-free admission patch");
            continue;
        }
        if ((p.island_size != 64 && p.island_size != 80) || (p.island_address & 15) ||
            !contained(islands_, island_size_, p.island_address, p.island_size) ||
            (p.replacement & 0xfc000000u) != 0x14000000u)
            throw ElfError("invalid admission island or branch");
        int64_t words = p.replacement & 0x03ffffffu;
        if (words & (int64_t(1) << 25))
            words -= int64_t(1) << 26;
        int64_t displacement = words * 4;
        uintptr_t target;
        if (displacement < 0) {
            if (uintptr_t(-displacement) > p.host_address)
                throw ElfError("admission branch underflow");
            target = p.host_address - uintptr_t(-displacement);
        } else {
            if (uintptr_t(displacement) > UINTPTR_MAX - p.host_address)
                throw ElfError("admission branch overflow");
            target = p.host_address + uintptr_t(displacement);
        }
        if (target != p.island_address)
            throw ElfError("admission branch misses island");
        next_islands.emplace_back(p.island_address, p.island_address + p.island_size);
    }
    std::sort(sites.begin(), sites.end());
    if (std::adjacent_find(sites.begin(), sites.end()) != sites.end())
        throw ElfError("duplicate admission publication site");
    std::sort(next_islands.begin(), next_islands.end());
    for (size_t i = 1; i < next_islands.size(); ++i)
        if (next_islands[i].first < next_islands[i - 1].second)
            throw ElfError("overlapping admission islands");
    // All allocations and checks precede mutation. The caller must keep the
    // sites unreachable until the complete batch has been installed.
    {
        JitWriteScope writable;
        for (const auto& p : patches)
            if (p.island_size)
                std::memcpy(reinterpret_cast<void*>(p.island_address), p.island.data(),
                            p.island_size);
        for (const auto& p : patches)
            if (p.island_size)
                sys_icache_invalidate(reinterpret_cast<void*>(p.island_address), p.island_size);
        for (const auto& p : patches)
            __atomic_store_n(reinterpret_cast<uint32_t*>(p.host_address), p.replacement,
                             __ATOMIC_RELEASE);
    }
    for (const auto& p : patches)
        sys_icache_invalidate(reinterpret_cast<void*>(p.host_address), 4);
    used_islands_.swap(next_islands);
}
} // namespace shiraz::guest
