// SPDX-License-Identifier: GPL-3.0-only
// Instrumented default-body boundary. Link unchanged production instruction.cpp
// and tpidr.S, plus private tls.cpp with only its accessor symbol renamed. This
// fixture observes raw entry values; it is not the production binary or a native
// memory/async/lifetime proof. Guest test templates remain first-party sources.
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/image.hpp"
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <array>
#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <stdexcept>
#include <string>
#include <sys/mman.h>
#include <libkern/OSCacheControl.h>
#include <unwind.h>
using namespace shiraz::guest;
namespace {
struct alignas(16) Snapshot {
  std::array<uint64_t, 31> x;
  uint64_t sp;
  std::array<std::array<uint8_t, 16>, 32> q;
};
struct alignas(16) Result {
  uint64_t x[31], sp, nzcv, fpcr, fpsr, padding;
  uint8_t q[32][16];
  uint64_t expected_sp, original_x18;
};
static_assert(sizeof(Snapshot) == 768 && offsetof(Snapshot, q) == 256);
static_assert(sizeof(Result) == 816 && offsetof(Result, q) == 288);
Snapshot observed;
unsigned calls = 0;
bool body_ok = true, trace_enabled = false;
std::array<uintptr_t, 128> trace{};
size_t trace_count = 0;
uintptr_t expected_guest_pc = 0;
std::array<uint64_t, 10> saved_guest_callees{};
bool recovered_guest_callees = false;
void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
bool check(bool value, const char *message) {
  if (!value) std::fprintf(stderr, "M5_MRS_QUARANTINE_FAIL %s\n", message);
  return value;
}
_Unwind_Reason_Code visit(_Unwind_Context *context, void *) {
  if (trace_count == trace.size()) return _URC_END_OF_STACK;
  const auto pc = _Unwind_GetIP(context);
  trace[trace_count++] = pc;
  if (pc == expected_guest_pc) {
    recovered_guest_callees = true;
    for (unsigned i = 0; i < saved_guest_callees.size(); ++i)
      recovered_guest_callees &= _Unwind_GetGR(context, 19 + i) == saved_guest_callees[i];
  }
  return _URC_NO_REASON;
}
void check_snapshot(uintptr_t native_x18) {
  for (unsigned reg = 0; reg < 31; ++reg) {
    if (reg == 7 || reg == 18 || reg == 29 || reg == 30) continue;
    require(observed.x[reg] == 0, "raw GPR zero before C++");
  }
  for (const auto &vector : observed.q)
    for (auto byte : vector) require(byte == 0, "all raw SIMD bytes zero before C++");
  require(observed.x[7] == observed.sp + 848 && !(observed.sp & 15),
          "original SP synthesized from production shared frame");
  require(observed.x[29] == observed.sp + 800, "native FP uses production frame record");
  require(observed.x[18] == native_x18, "Darwin x18 preserved");
}
std::vector<uint8_t> read_fixture(const char *path) {
  std::ifstream file(path, std::ios::binary | std::ios::ate);
  const auto length = file.tellg();
  require(bool(file) && length > 0 && length <= (256 << 20), "bounded ELF fixture");
  std::vector<uint8_t> bytes(static_cast<size_t>(length));
  file.seekg(0);
  require(bool(file.read(reinterpret_cast<char *>(bytes.data()), length)), "ELF fixture read");
  return bytes;
}
}
extern "C" char shiraz_tpidr_test_start[], shiraz_tpidr_test_site[], shiraz_tpidr_test_end[];
extern "C" void *shiraz_test_actual_tls_access(uint64_t, uint64_t, uint64_t, uint64_t,
                                              uint64_t, uint64_t, uint64_t, uintptr_t) noexcept;
extern "C" const uintptr_t shiraz_test_mrs_return_from_record;
extern "C" uintptr_t shiraz_test_mrs_record_body(const Snapshot *snapshot) {
  const int incoming_errno = errno;
  // Exercise the real cached body before explicit trace instrumentation.
  auto *result = shiraz_test_actual_tls_access(0, 0, 0, 0, 0, 0, 0, snapshot->x[7]);
  body_ok &= check(errno == incoming_errno, "real cached body preserves errno");
  std::memcpy(&observed, snapshot, sizeof(observed));
  ++calls;
  if (trace_enabled) {
    // Synchronous instrumentation while the production shared frame is live.
    // Its x19..x28 saves are contiguous from shared SP+152. This deliberately
    // demonstrates that unwinding can recover saved guest register copies.
    std::memcpy(saved_guest_callees.data(),
                reinterpret_cast<const void *>(snapshot->sp + 152), sizeof(saved_guest_callees));
    recovered_guest_callees = false;
    trace_count = 0;
    (void)_Unwind_Backtrace(visit, nullptr);
  }
  errno = incoming_errno;
  return reinterpret_cast<uintptr_t>(result);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: mrs-quarantine-probe libunwind-fixture.so");
    prepare_guest_tls();
    const auto tls = reinterpret_cast<uintptr_t>(guest_tls_base());
    constexpr size_t allocation = 256 * 1024;
    auto *memory = static_cast<uint8_t *>(mmap(nullptr, allocation,
        PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
    require(memory != MAP_FAILED, "JIT allocation");
    const auto length = uintptr_t(shiraz_tpidr_test_end) - uintptr_t(shiraz_tpidr_test_start);
    const auto offset = uintptr_t(shiraz_tpidr_test_site) - uintptr_t(shiraz_tpidr_test_start);
    require(length < 4096 && offset + 4 < length, "bounded first-party template");
    size_t used = 0;
    unsigned raw_cases = 0;
    for (unsigned target = 0; target < 32; ++target) {
      if (target == 18) continue;
      auto *start = memory + used;
      auto *site = start + offset;
      auto *island = start + ((length + 15) & ~size_t(15));
      used = size_t(island + 64 - memory);
      require(used <= allocation, "bounded generated programs");
      auto patch = plan_thread_pointer(uintptr_t(site), 0xd53bd040u | target, uintptr_t(island));
      {
        JitWriteScope write;
        std::memcpy(start, shiraz_tpidr_test_start, length);
        std::memcpy(site, &patch.replacement, 4);
        if (patch.uses_island) {
          uint32_t kind = 0;
          std::memcpy(&kind, patch.island.data() + 52, sizeof(kind));
          require(kind == 1, "actual default accessor kind");
          std::memcpy(island, patch.island.data(), patch.island.size());
        }
      }
      sys_icache_invalidate(start, size_t(island + 64 - start));
      Result result{};
      const auto before = calls;
      errno = ELOOP;
      reinterpret_cast<void (*)(Result *)>(start)(&result);
      require(errno == ELOOP, "host errno after instrumented default return");
      require(calls == before + unsigned(patch.uses_island), "default body observed exactly once");
      if (patch.uses_island) {
        check_snapshot(result.original_x18);
        require(observed.x[7] == result.expected_sp, "raw original SP matches template");
        ++raw_cases;
      }
      for (unsigned reg = 0; reg < 31; ++reg) {
        const auto expected = reg == 18 ? result.original_x18 : reg == target ? tls
                             : 0x6677445522331000ULL + reg;
        require(result.x[reg] == expected, "guest GPR restored after default quarantine");
      }
      require(result.sp == result.expected_sp && result.nzcv == 0xa0000000 &&
              result.fpcr == 0x02c00000 && result.fpsr == 0x0800001f, "guest SP/flags restored");
      for (unsigned reg = 0; reg < 32; ++reg)
        for (auto byte : result.q[reg]) require(byte == 0x40 + reg, "guest SIMD restored");
    }
    require(munmap(memory, allocation) == 0, "JIT cleanup");
    Image image(read_fixture(argv[1]));
    require(image.rewritten_thread_pointers() == 2, "two real default ELF MRS sites");
    uintptr_t native_x18;
    __asm__ volatile("mov %0, x18" : "=r"(native_x18));
    unsigned unwind_cases = 0;
    trace_enabled = true;
    for (const char *kind : {"framed", "leaf"}) {
      const auto name = std::string("guest_island_") + kind;
      const auto outer = std::string("guest_island_outer_") + kind;
      const auto resumed = reinterpret_cast<uintptr_t>(image.symbol(name + "_resume"));
      const auto caller = reinterpret_cast<uintptr_t>(image.symbol(outer + "_resume"));
      require(resumed && caller, "exact ELF unwind labels");
      expected_guest_pc = resumed;
      const auto before = calls;
      errno = ELOOP;
      invoke(image.symbol(outer), {});
      require(calls == before + 1, "unchanged default ELF accessor observed");
      check_snapshot(native_x18);
      size_t wrapper_index = trace_count, entry_index = trace_count;
      size_t guest_index = trace_count, caller_index = trace_count;
      for (size_t i = 0; i < trace_count; ++i) {
        if (trace[i] == shiraz_test_mrs_return_from_record) wrapper_index = i;
        if (trace[i] == observed.x[30]) entry_index = i;
        if (trace[i] == resumed) guest_index = i;
        if (trace[i] == caller) caller_index = i;
      }
      require(wrapper_index < entry_index && entry_index < guest_index &&
              guest_index < caller_index && caller_index < trace_count,
              "default unwind crosses shim/shared entry/exact guest continuation/caller in order");
      require(saved_guest_callees[0] != 0 && recovered_guest_callees,
              "unwinder recovers saved x19 through x28 at exact guest continuation");
      ++unwind_cases;
    }
    require(body_ok && raw_cases == 30 && unwind_cases == 2, "complete instrumented case counts");
    std::printf("M5_MRS_QUARANTINE_PASS targets=31 raw_default_entries=30 default_elf_entries=2 "
                "gpr_zero=27 simd_zero_bits=4096 original_sp=1 native_fp=1 x18=preserved "
                "guest_state_restored=1 real_cached_body=1 body_interposed_for_snapshot=1 "
                "production_entry_source=1 unwind_framed=1 unwind_leaf=1 exact_pc_order=1 recovered_gpr_cfi=10 "
                "native_memory_purity=0 async_proof=0 lifetime_lease=0 art=0\n");
  } catch (const std::exception &error) {
    std::fprintf(stderr, "M5_MRS_QUARANTINE_FAIL %s\n", error.what());
    return 1;
  }
}
