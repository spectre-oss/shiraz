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
#include <pthread.h>
#include <vector>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#include <sanitizer/common_interface_defs.h>
#define SHIRAZ_ASAN_FIBER_TEST 1
#endif
#endif
using namespace shiraz::guest;
extern "C" char shiraz_tpidr_test_start[], shiraz_tpidr_test_site[], shiraz_tpidr_test_end[];
extern "C" void *shiraz_tpidr_stress_access() noexcept;
extern "C" char shiraz_tpidr_guard_start[], shiraz_tpidr_guard_first[],
    shiraz_tpidr_guard_second[], shiraz_tpidr_guard_failure[], shiraz_tpidr_guard_end[];
extern "C" void shiraz_guest_stack_check_fail();
extern "C" void *shiraz_guest_tls_access(uint64_t, uint64_t, uint64_t, uint64_t,
                                        uint64_t, uint64_t, uint64_t, uintptr_t) noexcept;
struct alignas(16) Result {
  uint64_t integers[31], sp, nzcv, fpcr, fpsr, padding;
  uint8_t vectors[32][16];
  uint64_t expected_sp, original_x18;
};
static_assert(sizeof(Result) == 816 && offsetof(Result, vectors) == 288);
struct ForeignCall { void (*entry)(Result *); Result *result; };
extern "C" int shiraz_test_other_stack(void *, int (*)(void *), void *);
static int foreign_mrs(void *opaque) {
#ifdef SHIRAZ_ASAN_FIBER_TEST
  __sanitizer_finish_switch_fiber(nullptr, nullptr, nullptr);
#endif
  auto *call = static_cast<ForeignCall *>(opaque);
  call->entry(call->result);
  return 0;
}
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
extern "C" uintptr_t syscall_probe(uint64_t a0, uint64_t a1, uint64_t a2,
                                   uint64_t a3, uint64_t a4, uint64_t a5, uint64_t number,
                                   uintptr_t original_sp) noexcept {
  const uint64_t values[] = {a0,a1,a2,a3,a4,a5,number};
  for (unsigned i=0;i<7;++i)
    if(values[i] != 0x6677445522331000ULL + (i==6?8:i)) std::abort();
  static_assert(offsetof(Result, expected_sp) == 800);
  if ((original_sp & 15) || !prepared_guest_stack_contains(original_sp)) std::abort();
  uintptr_t expected_sp;
  std::memcpy(&expected_sp, reinterpret_cast<const void *>(original_sp + 800), sizeof(expected_sp));
  if (expected_sp != original_sp) std::abort();
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
static void check_mrs_window() {
  const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
  const auto low = high - pthread_get_stacksize_np(pthread_self());
  require(prepared_guest_stack_window(low + kMrsStackBytesBefore, kMrsStackBytesBefore, kMrsStackBytesAfter) &&
              prepared_guest_stack_window(high - kMrsStackBytesAfter, kMrsStackBytesBefore, kMrsStackBytesAfter),
          "complete MRS window admits exact native extent boundaries");
  require(!prepared_guest_stack_window(low + kMrsStackBytesBefore - 1, kMrsStackBytesBefore, 1) &&
              !prepared_guest_stack_window(high - kMrsStackBytesAfter + 1, 0, kMrsStackBytesAfter) &&
              !prepared_guest_stack_window(high, 0, 0) &&
              !prepared_guest_stack_window(low + kMrsStackBytesBefore, UINT64_MAX, 1) &&
              !prepared_guest_stack_window(high - kMrsStackBytesAfter, 0, UINT64_MAX) &&
              !prepared_guest_stack_window(UINTPTR_MAX, UINT64_MAX, UINT64_MAX),
          "MRS window rejects one-byte boundary crossing and overflow without reading memory");
  for (const auto anchor : {low + kMrsStackBytesBefore - 16, high - kMrsStackBytesAfter + 16}) {
    require(!(anchor & 15) && prepared_guest_stack_contains(anchor), "negative window anchor is an aligned native-stack member");
    int descriptors[2];
    require(pipe(descriptors) == 0, "MRS window diagnostic pipe");
    const pid_t child = fork();
    require(child >= 0, "MRS window child");
    if (!child) {
      close(descriptors[0]);
      if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
      close(descriptors[1]);
      std::signal(SIGABRT, [](int) { _exit(97); });
      (void)shiraz_guest_tls_access(0, 0, 0, 0, 0, 0, 0, anchor);
      _exit(98);
    }
    close(descriptors[1]);
    std::string diagnostic;
    char buffer[256]; ssize_t count;
    while ((count = read(descriptors[0], buffer, sizeof(buffer))) > 0) {
      diagnostic.append(buffer, size_t(count));
      if (diagnostic.size() > 8192) { kill(child, SIGKILL); break; }
    }
    close(descriptors[0]);
    int status;
    require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97 &&
                count == 0 && diagnostic == "SHIRAZ_GUEST_FATAL MRS spill window outside prepared native stack extent\n",
            "production MRS accessor refuses incomplete windows even when anchor itself is valid");
  }
  std::cout << "M5_MRS_WINDOW_PASS profile=" << kMrsStackWindowProfile
            << " before=" << kMrsStackBytesBefore << " after=" << kMrsStackBytesAfter
            << " exact_boundaries=1 lower_refusal=1 upper_refusal=1 overflow=1\n";
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
    check_mrs_window();
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
        uintptr_t native_sp;
        __asm__ volatile("mov %0, sp" : "=r"(native_sp));
        require(!prepared_guest_stack_window(native_sp, 0, 1) && !guest_tls_prepared(),
                "unprepared window query refuses without initializing TLS");
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
    constexpr size_t foreign_size = 256 * 1024;
    void *foreign_stack = mmap(nullptr, foreign_size, PROT_READ | PROT_WRITE,
                                MAP_PRIVATE | MAP_ANON, -1, 0);
    require(foreign_stack != MAP_FAILED, "foreign MRS stack allocation");
    Result foreign_result{};
    ForeignCall call{programs.front().function, &foreign_result};
    int descriptors[2];
    require(pipe(descriptors) == 0, "foreign MRS diagnostic pipe");
    child = fork();
    require(child >= 0, "foreign MRS child");
    if (!child) {
      close(descriptors[0]);
      if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
      close(descriptors[1]);
      std::signal(SIGABRT, [](int) { _exit(97); });
#ifdef SHIRAZ_ASAN_FIBER_TEST
      // Preserve the suspended native fake stack: call/result may reside in
      // it. This child must abort on the new stack and never resumes it.
      void *suspended_fake_stack = nullptr;
      __sanitizer_start_switch_fiber(&suspended_fake_stack, foreign_stack, foreign_size);
#endif
      shiraz_test_other_stack(static_cast<uint8_t *>(foreign_stack) + foreign_size - 16,
                             &foreign_mrs, &call);
      _exit(98);
    }
    close(descriptors[1]);
    std::string diagnostic;
    char buffer[512];
    ssize_t count;
    while ((count = read(descriptors[0], buffer, sizeof(buffer))) > 0) {
      diagnostic.append(buffer, size_t(count));
      if (diagnostic.size() > 8192) { kill(child, SIGKILL); break; }
    }
    close(descriptors[0]);
    require(waitpid(child, &status, 0) == child, "collect foreign MRS child");
    if (diagnostic != "SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n")
      std::cerr << "foreign MRS status=" << status << " diagnostic=" << diagnostic;
    require(WIFEXITED(status) && WEXITSTATUS(status) == 97 &&
                count == 0 && diagnostic == "SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n",
            "real rewritten MRS on a foreign stack reaches the exact extent refusal");
    require(munmap(foreign_stack, foreign_size) == 0, "foreign MRS stack cleanup");
    require(munmap(memory, size) == 0, "TPIDR test cleanup");
    std::cout << "M5_TPIDR_THUNK_PASS targets=31 accessors=2 gpr=31 vector_bits=4096 "
                 "flags=NZCV,FPCR,FPSR sp=1 lr=1 pac=1 threads=4 malformed=6 guard=1 corruption_abort=1 "
                 "host_tpidr_written=0 elf_auto_rewrite=0 unwind_verified=0\n";
    std::cout << "M5_TPIDR_STACK_PASS foreign_stack_rejected=1 original_sp=1 syscall_arguments=7\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
