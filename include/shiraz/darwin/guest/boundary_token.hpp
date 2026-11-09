// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <atomic>
#include <cstdint>
#include <limits>

namespace shiraz::guest::detail {
static_assert(std::atomic<uint64_t>::is_always_lock_free);

// Zero denotes failure. MAX is a permanent exhausted state, never a token:
// neither failure nor a signal interrupt can wrap the sequence back to one.
inline uint64_t reserve_boundary_token(std::atomic<uint64_t> &next) noexcept {
  auto candidate = next.load(std::memory_order_relaxed);
  while (candidate && candidate != std::numeric_limits<uint64_t>::max()) {
    if (next.compare_exchange_weak(candidate, candidate + 1,
                                   std::memory_order_relaxed, std::memory_order_relaxed))
      return candidate;
  }
  return 0;
}
} // namespace shiraz::guest::detail
