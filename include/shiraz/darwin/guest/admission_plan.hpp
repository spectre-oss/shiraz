// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/decode.hpp"
#include <array>
#include <cstddef>
#include <cstdint>
#include <vector>
namespace shiraz::guest {
struct AdmissionPatch {
    uint64_t guest_address;
    uintptr_t host_address, island_address;
    uint32_t original, replacement;
    std::array<uint8_t, 80> island{};
    size_t island_size = 0;
};
// Preflight a complete decoder result into a bounded, immutable patch batch.
// This function does not write or publish code and does not authorize guest
// execution: entry interception, mapped-byte validation, synchronization,
// cache publication, external targets and lifetime remain caller duties.
std::vector<AdmissionPatch>
plan_guest_admission(const Elf& elf, const std::vector<uint8_t>& original,
                     const ThreadPointerAnalysis& analysis, uintptr_t image_base, uintptr_t islands,
                     size_t island_capacity, uintptr_t syscall_dispatcher,
                     uintptr_t transfer_validator);
} // namespace shiraz::guest
