// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <array>
#include <cstdint>
namespace shiraz::guest {
// Production MRS admission profile v1. The complete half-open window must
// lie in the prepared native stack. Later spill addresses still require an
// independent per-site proof; this does not establish continuous SP residence.
inline constexpr uint32_t kMrsStackWindowProfile = 1;
inline constexpr uint64_t kMrsStackBytesBefore = 4096;
inline constexpr uint64_t kMrsStackBytesAfter = 256;
struct ThreadPointerPatch {
    uint32_t replacement;
    std::array<uint8_t, 64> island{};
    bool uses_island = true;
};
using ThreadPointerAccessor = void* (*)() noexcept;
// Pure bounded planning. Caller owns and validates the code site/island ranges,
// applies the returned bytes under JitWriteScope and invalidates the I-cache.
// A non-null accessor is an explicit nonthrowing native test/integration
// callback returning the real thread pointer. Null selects production access.
ThreadPointerPatch plan_thread_pointer(uintptr_t site, uint32_t original, uintptr_t island,
                                       ThreadPointerAccessor accessor = nullptr);
ThreadPointerPatch plan_system_call(uintptr_t site, uint32_t original, uintptr_t island,
                                    uintptr_t dispatcher);
// Isolated interception primitive. The validator receives (actual target,
// original instruction address, original guest SP) and must return exactly
// the same target. It must not return on admission failure. The caller must
// prove safe target admission and synchronization before executing this plan.
struct IndirectTransferPatch {
    uint32_t replacement;
    std::array<uint8_t, 80> island{};
};
IndirectTransferPatch plan_indirect_transfer(uintptr_t site, uint32_t original, uintptr_t island,
                                             uintptr_t validator);
} // namespace shiraz::guest
