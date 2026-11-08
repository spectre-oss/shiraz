// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include <atomic>
#include <cstring>
#include <iostream>
#include <libkern/OSCacheControl.h>
#include <sys/mman.h>
#include <thread>
#include <vector>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
extern "C" char shiraz_tpidr_test_start[], shiraz_tpidr_test_site[], shiraz_tpidr_test_end[];
extern "C" void *shiraz_tpidr_stress_access() noexcept;
extern "C" char shiraz_tpidr_guard_start[], shiraz_tpidr_guard_first[],
    shiraz_tpidr_guard_second[], shiraz_tpidr_guard_failure[], shiraz_tpidr_guard_end[];
extern "C" void shiraz_guest_stack_check_fail();
struct alignas(16) Result {
  uint64_t integers[31], sp, nzcv, fpcr, fpsr, padding;
  uint8_t vectors[32][16];
  uint64_t expected_sp, original_x18;
};
static_assert(sizeof(Result) == 816 && offsetof(Result, vectors) == 288);
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
extern "C" uintptr_t syscall_probe(uint64_t a0, uint64_t a1, uint64_t a2,
                                   uint64_t a3, uint64_t a4, uint64_t a5, uint64_t number) noexcept {
  const uint64_t values[] = {a0,a1,a2,a3,a4,a5,number};
  for (unsigned i=0;i<7;++i)
    if(values[i] != 0x6677445522331000ULL + (i==6?8:i)) std::abort();
  return reinterpret_cast<uintptr_t>(guest_tls_base());
}
static void verify(const Result &result, unsigned target, uintptr_t tls) {
  for (unsigned reg = 0; reg < 31; ++reg) {
    uint64_t expected = reg == 18 ? result.original_x18
                      : reg == target ? tls : 0x6677445522331000ULL + reg;
    if (result.integers[reg] != expected)
      throw std::runtime_error("TPIDR register mismatch target=" + std::to_string(target) +
                               " reg=" + std::to_string(reg));
  }
  require(result.sp == result.expected_sp && !(result.sp & 15), "SP restored exactly");
  require(result.nzcv == 0xa0000000 && result.fpcr == 0x02c00000 && result.fpsr == 0x0800001f,
          "NZCV/FPCR/FPSR preserved");
  for (unsigned reg = 0; reg < 32; ++reg)
    for (unsigned byte = 0; byte < 16; ++byte)
      require(result.vectors[reg][byte] == 0x40 + reg, "all 128 vector bits preserved");
}
int main() {
  try {
    constexpr size_t size = 256 * 1024;
    auto *memory = static_cast<uint8_t *>(mmap(nullptr, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                                               MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
    require(memory != MAP_FAILED, "TPIDR test MAP_JIT allocation");
    size_t length = uintptr_t(shiraz_tpidr_test_end) - uintptr_t(shiraz_tpidr_test_start);
    size_t site_offset = uintptr_t(shiraz_tpidr_test_site) - uintptr_t(shiraz_tpidr_test_start);
    require(length < 4096 && site_offset + 4 < length, "bounded native test template");
    using Function = void (*)(Result *);
    struct Program { Function function; unsigned target; };
    std::vector<Program> programs;
    using Protected = int (*)(int);
    Protected protected_function = nullptr;
    size_t used = 0;
    {
      JitWriteScope writable;
      for (int stress = 0; stress < 3; ++stress) for (unsigned target = 0; target < 32; ++target) {
        if (target == 18) continue;
        if (stress == 2 && target != 0) continue;
        auto *start = memory + used;
        auto *site = start + site_offset;
        auto *island = start + ((length + 15) & ~size_t(15));
        used = size_t(island + 64 - memory);
        require(used <= size, "bounded TPIDR test programs");
        std::memcpy(start, shiraz_tpidr_test_start, length);
        uint32_t original = stress == 2 ? 0xd4000001u : 0xd53bd040u | target;
        std::memcpy(site, &original, 4);
        uint32_t observed;
        std::memcpy(&observed, site, 4);
        auto patch = stress == 2
            ? plan_system_call(uintptr_t(site), observed, uintptr_t(island), uintptr_t(&syscall_probe))
            : plan_thread_pointer(uintptr_t(site), observed, uintptr_t(island),
                stress ? &shiraz_tpidr_stress_access : nullptr);
        std::memcpy(site, &patch.replacement, 4);
        if (patch.uses_island) {
          uintptr_t unwind_pc = 0;
          std::memcpy(&unwind_pc, patch.island.data() + 24, sizeof(unwind_pc));
          require(unwind_pc == uintptr_t(site) + 4,
                  "island unwind PC must be the exact guest continuation");
          std::memcpy(island, patch.island.data(), patch.island.size());
        }
        programs.push_back({reinterpret_cast<Function>(start), target});
      }
      auto *start = memory + used;
      size_t guard_length = uintptr_t(shiraz_tpidr_guard_end) - uintptr_t(shiraz_tpidr_guard_start);
      require(used + guard_length + 160 < size, "bounded stack-guard test");
      std::memcpy(start, shiraz_tpidr_guard_start, guard_length);
      auto *island = start + ((guard_length + 15) & ~size_t(15));
      for (auto *source : {shiraz_tpidr_guard_first, shiraz_tpidr_guard_second}) {
        auto *site = start + (uintptr_t(source) - uintptr_t(shiraz_tpidr_guard_start));
        uint32_t original;
        std::memcpy(&original, site, 4);
        auto patch = plan_thread_pointer(uintptr_t(site), original, uintptr_t(island));
        std::memcpy(site, &patch.replacement, 4);
        std::memcpy(island, patch.island.data(), patch.island.size());
        island += 64;
      }
      uintptr_t failure = reinterpret_cast<uintptr_t>(&shiraz_guest_stack_check_fail);
      std::memcpy(start + (uintptr_t(shiraz_tpidr_guard_failure) - uintptr_t(shiraz_tpidr_guard_start)),
                  &failure, sizeof(failure));
      protected_function = reinterpret_cast<Protected>(start);
      used = size_t(island - memory);
    }
    sys_icache_invalidate(memory, used);
    prepare_guest_tls();
    uintptr_t tls = reinterpret_cast<uintptr_t>(guest_tls_base());
    for (const auto &program : programs) {
      Result result{};
      errno = ELOOP;
      program.function(&result);
      verify(result, program.target, tls);
      require(errno == ELOOP, "TLS accessor preserves host errno");
    }
    std::atomic<int> passed{0}, ready{0};
    uintptr_t bases[4]{};
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i) threads.emplace_back([&, i] {
      bool arrived = false;
      try {
        prepare_guest_tls();
        uintptr_t own = reinterpret_cast<uintptr_t>(guest_tls_base());
        bases[i] = own;
        ++ready;
        arrived = true;
        while (ready != 4) std::this_thread::yield();
        require(own != tls, "worker TLS differs from main");
        for (int repeat = 0; repeat < 16; ++repeat) for (const auto &program : programs) {
          Result result{};
          program.function(&result);
          verify(result, program.target, own);
        }
        ++passed;
      } catch (...) { if (!arrived) ++ready; }
    });
    for (auto &thread : threads) thread.join();
    require(passed == 4, "concurrent state-preserving TPIDR reads");
    for (int i = 0; i < 4; ++i) for (int j = 0; j < i; ++j)
      require(bases[i] != bases[j], "concurrent guest TLS blocks are distinct");
    unsigned rejected = 0;
    auto reject = [&](uintptr_t site, uint32_t instruction, uintptr_t island) {
      try { plan_thread_pointer(site, instruction, island); }
      catch (const ElfError &) { ++rejected; return; }
      throw std::runtime_error("accepted malformed TPIDR patch plan");
    };
    reject(0x100000000, 0xd53bd052, 0x100001000); // reserved x18
    reject(0x100000000, 0xd503201f, 0x100001000); // not MRS
    reject(0x100000001, 0xd53bd040, 0x100001000);
    reject(0x100000000, 0xd53bd040, 0x100001004);
    reject(0x100000000, 0xd53bd040, 0x108000000); // positive range
    reject(0x108000010, 0xd53bd040, 0x100000000); // negative range
    require(rejected == 6, "bounded TPIDR plan rejection cases");
    require(protected_function(0) == 73, "actual rewritten stack-guard check returns normally");
    pid_t child = fork();
    require(child >= 0, "stack-guard corruption child");
    if (!child) {
      std::signal(SIGABRT, [](int) { _exit(97); });
      protected_function(1);
      _exit(98);
    }
    int status;
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
            "corrupting the saved stack guard triggers actual failure");
    require(munmap(memory, size) == 0, "TPIDR test cleanup");
    std::cout << "M5_TPIDR_THUNK_PASS targets=31 accessors=2 gpr=31 vector_bits=4096 "
                 "flags=NZCV,FPCR,FPSR sp=1 lr=1 pac=1 threads=4 malformed=6 guard=1 corruption_abort=1 "
                 "host_tpidr_written=0 elf_auto_rewrite=0 unwind_verified=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
