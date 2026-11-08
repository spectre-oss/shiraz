// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <array>
#include <cstdint>
namespace shiraz::guest {
struct ThreadPointerPatch {
  uint32_t replacement;
  std::array<uint8_t, 64> island{};
  bool uses_island = true;
};
using ThreadPointerAccessor = void *(*)() noexcept;
// Pure bounded planning. Caller owns and validates the code site/island ranges,
// applies the returned bytes under JitWriteScope and invalidates the I-cache.
// A non-null accessor is an explicit nonthrowing native test/integration
// callback returning the real thread pointer. Null selects production access.
ThreadPointerPatch plan_thread_pointer(uintptr_t site, uint32_t original,
                                      uintptr_t island, ThreadPointerAccessor accessor = nullptr);
ThreadPointerPatch plan_system_call(uintptr_t site, uint32_t original,
                                    uintptr_t island, uintptr_t dispatcher);
} // namespace shiraz::guest
