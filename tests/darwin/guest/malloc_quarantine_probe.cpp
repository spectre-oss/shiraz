// SPDX-License-Identifier: GPL-3.0-only
// Link the exact production invoke.S entry. Only its body is interposed by the
// raw assembly snapshot shim; a private providers.cpp object renames the real
// typed body to shiraz_test_actual_managed_malloc_body. The recorder calls that
// body unchanged. This instrumentation is not the normal production call path.
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "guest_errno.hpp"
#include "guest_tls_layout.hpp"
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <initializer_list>
#include <limits>
#include <unwind.h>

using namespace shiraz::guest;
namespace {
struct alignas(16) BodySnapshot {
  std::array<uint64_t, 31> x;
  uint64_t sp;
  std::array<std::array<uint8_t, 16>, 32> q;
};
struct alignas(16) Probe {
  uint64_t expected_sp, expected_x18, result;
  std::array<uint64_t, 11> preserved_x;
  std::array<uint64_t, 8> preserved_d;
  uint64_t upper_canary;
  std::array<uint64_t, 17> scratch_x;
  std::array<std::array<uint8_t, 16>, 32> scratch_q;
  uint64_t after_x18;
  uint64_t retired_frame_nonzero;
};
static_assert(offsetof(BodySnapshot, sp) == 248 && offsetof(BodySnapshot, q) == 256);
static_assert(sizeof(BodySnapshot) == 768);
static_assert(offsetof(Probe, expected_x18) == 8 && offsetof(Probe, result) == 16);
static_assert(offsetof(Probe, preserved_x) == 24 && offsetof(Probe, preserved_d) == 112);
static_assert(offsetof(Probe, upper_canary) == 176 && offsetof(Probe, scratch_x) == 184);
static_assert(offsetof(Probe, scratch_q) == 320 && offsetof(Probe, after_x18) == 832);
static_assert(offsetof(Probe, retired_frame_nonzero) == 840 && sizeof(Probe) == 848);
BodySnapshot observed;
uint64_t observed_lower_canary;
unsigned bodies = 0, unwind_passes = 0, unwind_recoveries = 0;
uintptr_t expected_seed = 0;
bool body_ok = true;
bool require(bool condition, const char *description) {
  if (!condition) std::fprintf(stderr, "M5_MALLOC_QUARANTINE_FAIL %s\n", description);
  return condition;
}
struct PreserveErrno {
  int value;
  ~PreserveErrno() { errno = value; }
};
struct UnwindSeen {
  unsigned frames = 0;
  bool wrapper = false, entry = false, caller = false, recovered_tp = false;
};
} // namespace
extern "C" void *shiraz_guest_managed_malloc_entry(size_t);
extern "C" void *shiraz_test_actual_managed_malloc_body(size_t, uintptr_t) noexcept;
extern "C" void shiraz_test_malloc_probe_invoke(uint64_t, Probe *, uintptr_t);
extern "C" const uintptr_t shiraz_test_malloc_return_from_record;
extern "C" const uintptr_t shiraz_test_malloc_return_from_entry;

static _Unwind_Reason_Code unwind_frame(_Unwind_Context *context, void *opaque) {
  auto &seen = *static_cast<UnwindSeen *>(opaque);
  const auto pc = _Unwind_GetIP(context);
  ++seen.frames;
  seen.wrapper |= pc == shiraz_test_malloc_return_from_record;
  // Captured before any C++ instruction: this is the actual BL continuation
  // inside the production entry, not a fabricated fixture entry address.
  seen.entry |= pc == observed.x[30];
  seen.caller |= pc == shiraz_test_malloc_return_from_entry;
  if (pc == shiraz_test_malloc_return_from_entry)
    seen.recovered_tp |= _Unwind_GetGR(context, 19) == expected_seed;
  return seen.frames >= 64 ? _URC_END_OF_STACK : _URC_NO_REASON;
}
extern "C" uint64_t shiraz_test_malloc_record_body(const BodySnapshot *snapshot,
                                                   uint64_t lower_canary) {
  const int incoming_errno = errno;
  // Exercise the unchanged typed body before the explicit unwind/TP-recovery
  // instrumentation below. The fixture deliberately receives a raw snapshot;
  // the production body receives only size and synthesized original SP.
  void *result = shiraz_test_actual_managed_malloc_body(snapshot->x[0], snapshot->x[1]);
  const int returned_errno = errno;
  PreserveErrno restore{returned_errno};
  body_ok &= require(returned_errno == incoming_errno, "typed body preserves host errno");
  std::memcpy(&observed, snapshot, sizeof(observed));
  observed_lower_canary = lower_canary;
  ++bodies;
  UnwindSeen seen;
  (void)_Unwind_Backtrace(unwind_frame, &seen);
  const bool unwound = seen.wrapper && seen.entry && seen.caller;
  body_ok &= require(unwound, "DWARF unwind crosses shim and production entry to exact caller PC");
  unwind_passes += unwound;
  // CFI can recover saved guest state. Passing this test must never authorize
  // a native-memory purity/async guarantee or erase the analyzer spill fence.
  body_ok &= require(seen.recovered_tp, "native unwinder can recover saved guest TP");
  unwind_recoveries += seen.recovered_tp;
  return reinterpret_cast<uintptr_t>(result);
}

int main() {
  prepare_guest_tls();
  const uintptr_t seed = reinterpret_cast<uintptr_t>(guest_tls_base());
  expected_seed = seed;
  const auto *tls = reinterpret_cast<const uintptr_t *>(seed);
  const uintptr_t guard_before = tls[bionic::kTlsStackGuardSlot];
  if (!require(guard_before != 0, "actual per-thread guest guard nonzero")) return 1;
  unsigned cases = 0, allocations = 0;
  bool ok = true;
  for (const uint64_t size : {uint64_t(0), uint64_t(1), uint64_t(4096),
                              std::numeric_limits<uint64_t>::max()}) {
    Probe probe{};
    const unsigned before = bodies;
    *guest_errno_location() = 73;
    errno = 61;
    shiraz_test_malloc_probe_invoke(size, &probe, seed);
    const int returned_errno = errno;
    ok &= require(tls[bionic::kTlsStackGuardSlot] == guard_before, "actual guest guard unchanged");
    ok &= require(returned_errno == 61, "host errno preserved through instrumented typed entry");
    ok &= require(bodies == before + 1, "one actual typed body call");
    ok &= require(observed.x[0] == size, "clean size unchanged");
    ok &= require(observed.x[1] == probe.expected_sp, "body x1 is synthesized original SP");
    for (unsigned i = 2; i <= 17; ++i)
      ok &= require(observed.x[i] == 0, "caller-saved GPR quarantined before C++");
    for (unsigned i = 19; i <= 29; ++i)
      ok &= require(observed.x[i] == 0, "callee-saved GPR copies quarantined before C++");
    ok &= require(observed.x[18] == probe.expected_x18 && probe.after_x18 == probe.expected_x18,
                  "Darwin x18 preserved before body and after return");
    const auto entry = reinterpret_cast<uintptr_t>(&shiraz_guest_managed_malloc_entry);
    ok &= require(observed.x[30] > entry && observed.x[30] - entry < 4096,
                  "body LR is a bounded native entry continuation");
    for (const auto &vector : observed.q)
      for (const uint8_t byte : vector)
        ok &= require(byte == 0, "all input SIMD bytes quarantined");
    ok &= require((observed.sp & 15) == 0 && observed.sp + 160 == probe.expected_sp,
                  "exact aligned production entry frame");
    for (const uint64_t value : probe.preserved_x)
      ok &= require(value == seed, "guest x19-x29 restored");
    for (const uint64_t value : probe.preserved_d)
      ok &= require(value == seed, "guest low d8-d15 restored");
    for (const uint64_t value : probe.scratch_x)
      ok &= require(value == 0, "normal-return x1-x17 cleared");
    for (unsigned index = 0; index < 32; ++index) {
      const uint64_t expected_low = index >= 8 && index <= 15 ? seed : 0;
      uint64_t low = 0, high = 0;
      std::memcpy(&low, probe.scratch_q[index].data(), 8);
      std::memcpy(&high, probe.scratch_q[index].data() + 8, 8);
      ok &= require(low == expected_low && high == 0,
                    "normal-return SIMD scratch cleared with low d8-d15 preserved");
    }
    ok &= require(observed_lower_canary == 0x5a5a, "lower footprint canary at body admission");
    ok &= require(probe.upper_canary == 0x6b6b, "upper caller canary after return");
    ok &= require(probe.retired_frame_nonzero == 0,
                  "all 160 retired production entry frame bytes cleared before any subsequent call");
    auto *pointer = reinterpret_cast<void *>(probe.result);
    if (size == UINT64_MAX) {
      ok &= require(!pointer && *guest_errno_location() == bionic::kENOMEM,
                    "oversized failure translates guest ENOMEM");
    } else if (pointer) {
      ok &= require(*guest_errno_location() == 73, "successful allocation retains guest errno");
      ok &= require(registered_guest_heap_contains(probe.result, size ? size : 1),
                    "actual backend registers returned allocation");
      if (size) std::memset(pointer, 0xA5, size);
      release_guest_allocation(pointer);
      ok &= require(!registered_guest_heap_contains(probe.result, 1),
                    "actual backend retires freed allocation");
      ++allocations;
    } else {
      ok &= require(size == 0 && *guest_errno_location() != 73,
                    "only implementation-defined zero allocation may return null");
    }
    ++cases;
  }
  if (!ok || !body_ok || unwind_passes != cases || unwind_recoveries != cases) return 1;
  std::printf("M5_MALLOC_QUARANTINE_PASS cases=%u allocations=%u production_entry_source=1 "
              "body_interposed_for_snapshot=1 real_typed_body=1 entry_frame=160 "
              "snapshot_before_cpp=1 errno=preserved domains=registered "
              "unwind_passes=%u recovered_tp=%u actual_guest_tp_seed=1 guard_preserved=1 "
              "scratch_return_clear=1 retired_frame_clear=160 guest_mrs=0 art=0 "
              "native_memory_purity=0 async_proof=0\n",
              cases, allocations, unwind_passes, unwind_recoveries);
}
