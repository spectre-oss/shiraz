// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cstring>
#include <cstdlib>
#include <unistd.h>
namespace shiraz::guest {
extern "C" void shiraz_guest_tpidr_entry();
// The shared assembly forwards the original guest SP in the otherwise unused
// eighth integer argument. x0..x6 retain their Linux syscall dispatch meaning.
extern "C" void *shiraz_guest_tls_access(uint64_t, uint64_t, uint64_t, uint64_t,
                                        uint64_t, uint64_t, uint64_t, uintptr_t original_sp) noexcept {
  try {
    HostErrno preserve;
    if ((original_sp & 15) || !prepared_guest_stack_contains(original_sp)) {
      constexpr char message[] = "SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n";
      (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
      std::abort();
    }
    if (!prepared_guest_stack_window(original_sp, kMrsStackBytesBefore, kMrsStackBytesAfter)) {
      constexpr char message[] = "SHIRAZ_GUEST_FATAL MRS spill window outside prepared native stack extent\n";
      (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
      std::abort();
    }
    return guest_tls_base();
  } catch (...) { std::abort(); }
}
namespace {
uint32_t branch(uintptr_t from, uintptr_t to) {
  if ((from | to) & 3) throw ElfError("unaligned guest TLS branch");
  int64_t words;
  if (to >= from) {
    uintptr_t delta = to - from;
    if (delta > (uintptr_t(1) << 27) - 4) throw ElfError("guest TLS island beyond branch reach");
    words = int64_t(delta / 4);
  } else {
    uintptr_t delta = from - to;
    if (delta > (uintptr_t(1) << 27)) throw ElfError("guest TLS island beyond branch reach");
    words = -int64_t(delta / 4);
  }
  return 0x14000000u | (uint32_t(words) & 0x03ffffffu);
}
template <class T> void put(ThreadPointerPatch &patch, size_t offset, T value) {
  std::memcpy(patch.island.data() + offset, &value, sizeof(value));
}
} // namespace
ThreadPointerPatch plan_thread_pointer(uintptr_t site, uint32_t original,
                                      uintptr_t island, ThreadPointerAccessor accessor) {
  if ((original & 0xffffffe0u) != 0xd53bd040u)
    throw ElfError("guest TLS patch requires MRS TPIDR_EL0");
  if (site > UINTPTR_MAX - 4 || island > UINTPTR_MAX - 64 || (site & 3) || (island & 15))
    throw ElfError("invalid guest TLS site/island address");
  uint32_t target = original & 31;
  if (target == 18) throw ElfError("guest TLS destination x18 is reserved by Darwin");
  ThreadPointerPatch result{};
  if (target == 31) {
    result.replacement = 0xd503201fu; // MRS XZR has no visible result.
    result.uses_island = false;
    return result;
  }
  result.replacement = branch(site, island);
  put(result, 0, uint32_t(0xa9bf47f0u)); // stp x16,x17,[sp,#-16]!
  put(result, 4, uint32_t(0x10000130u)); // adr x16, island+40 (context)
  put(result, 8, uint32_t(0x580000d1u)); // ldr x17, [pc,#24] (entry at +32)
  put(result, 12, uint32_t(0xd61f0220u)); // br x17
  put(result, 16, uint32_t(0xa8c147f0u)); // ldp x16,x17,[sp],#16
  put(result, 20, branch(island + 20, site + 4));
  // Exact interrupted guest continuation for the shared entry's DWARF PC
  // return column. Execution branches at +20; bytes +24..31 are literal data.
  put(result, 24, site + 4);
  put(result, 32, reinterpret_cast<uintptr_t>(&shiraz_guest_tpidr_entry));
  put(result, 40, island + 16);
  put(result, 48, target);
  put(result, 56, accessor ? reinterpret_cast<uintptr_t>(accessor)
                          : reinterpret_cast<uintptr_t>(&shiraz_guest_tls_access));
  return result;
}
ThreadPointerPatch plan_system_call(uintptr_t site, uint32_t original,
                                    uintptr_t island, uintptr_t dispatcher) {
  if (original != 0xd4000001u || !dispatcher)
    throw ElfError("guest SVC #0 requires a Linux syscall dispatcher");
  auto result = plan_thread_pointer(site, 0xd53bd040u, island);
  put(result, 56, dispatcher);
  return result;
}
} // namespace shiraz::guest
