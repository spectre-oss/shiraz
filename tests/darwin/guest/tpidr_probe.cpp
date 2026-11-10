// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/instruction.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/jit.hpp"
#include <atomic>
#include <chrono>
#include <poll.h>
#include <string>
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
  uint64_t integers[31], sp, nzcv, fpcr, fpsr, retired_scratch_nonzero;
  uint8_t vectors[32][16];
  uint64_t expected_sp, original_x18;
};
static_assert(sizeof(Result) == 816 && offsetof(Result, vectors) == 288 &&
              offsetof(Result, retired_scratch_nonzero) == 280);
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
  // A discarded XZR MRS has no trampoline footprint to inspect.
  if (target != 31) {
    require(result.retired_scratch_nonzero == 0, "retired trampoline frame is fully cleared");
  }
  require(result.sp == result.expected_sp && !(result.sp & 15), "SP restored exactly");
  require(result.nzcv == 0xa0000000 && result.fpcr == 0x02c00000 && result.fpsr == 0x0800001f,
          "NZCV/FPCR/FPSR preserved");
  for (unsigned reg = 0; reg < 32; ++reg)
    for (unsigned byte = 0; byte < 16; ++byte)
      require(result.vectors[reg][byte] == 0x40 + reg, "all 128 vector bits preserved");
}
class MrsChildDisposition {
  struct sigaction previous{};
public:
  MrsChildDisposition() {
    require(!sigaction(SIGCHLD, nullptr, &previous), "snapshot MRS child disposition");
    struct sigaction action{};
    sigemptyset(&action.sa_mask);
    action.sa_handler = SIG_DFL;
    require(!sigaction(SIGCHLD, &action, nullptr), "keep MRS child waitable");
  }
  ~MrsChildDisposition() { if (sigaction(SIGCHLD, &previous, nullptr)) _exit(95); }
};
static void mrs_refusal(const char *executable, const char *mode, const char *expected) {
  MrsChildDisposition disposition;
  int descriptors[2];
  require(!pipe(descriptors), "MRS refusal pipe");
  char *arguments[] = {const_cast<char *>(executable), const_cast<char *>(mode), nullptr};
  const auto child = fork();
  require(child >= 0, "MRS refusal fork");
  if (!child) {
    close(descriptors[0]);
    if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
    close(descriptors[1]);
    execv(executable, arguments);
    _exit(96);
  }
  close(descriptors[1]);
  const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
  std::string diagnostic;
  char buffer[512];
  ssize_t count = -1;
  bool failed = false;
  while (std::chrono::steady_clock::now() < deadline) {
    pollfd input{descriptors[0], POLLIN | POLLHUP, 0};
    const auto ready = poll(&input, 1, 100);
    if (ready < 0 && errno == EINTR) continue;
    if (ready < 0) { failed = true; break; }
    if (!ready) continue;
    count = read(descriptors[0], buffer, sizeof(buffer));
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0) break;
    diagnostic.append(buffer, size_t(count));
    if (diagnostic.size() > 8192) { failed = true; break; }
  }
  close(descriptors[0]);
  int status = -1, wait_error = 0;
  pid_t waited = 0;
  while (std::chrono::steady_clock::now() < deadline) {
    waited = waitpid(child, &status, WNOHANG);
    wait_error = waited < 0 ? errno : 0;
    if (waited == child || (waited < 0 && wait_error != EINTR)) break;
    std::this_thread::sleep_for(std::chrono::milliseconds(1));
  }
  if (waited != child) {
    failed = true;
    if (waited == 0 || (waited < 0 && wait_error == EINTR)) {
      kill(child, SIGKILL);
      do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    }
  }
  const bool refused = !failed && waited == child && WIFEXITED(status) &&
      WEXITSTATUS(status) == 97 && count == 0 && diagnostic == expected;
  if (!refused) std::cerr << "MRS refusal mode=" << mode << " status=" << status
      << " read=" << count << " failed=" << failed << " wait_errno=" << wait_error
      << " diagnostic=" << diagnostic << std::endl;
  require(refused, "fresh MRS refusal child reaches exact fatal diagnostic");
}
static void check_return_branch() {
  constexpr uintptr_t site = 0x20000000;
  for (auto island : {site - (uintptr_t(1) << 27), site - 64, site + 64,
                     site + (uintptr_t(1) << 27) - 32}) {
    const auto patch = plan_thread_pointer(site, 0xd53bd040, island);
    uint32_t instruction, clear;
    std::memcpy(&instruction, patch.island.data() + 24, 4);
    std::memcpy(&clear, patch.island.data() + 20, 4);
    require((instruction & 0xfc000000u) == 0x14000000u && clear == 0xa93f7fffu,
            "new continuation/retired outer clear shape");
    int64_t delta = instruction & 0x03ffffffu;
    if (delta & (int64_t(1) << 25)) delta -= int64_t(1) << 26;
    const auto pc = island + 24;
    const auto target = delta < 0 ? pc - uintptr_t(-delta * 4) : pc + uintptr_t(delta * 4);
    require(target == site + 4, "signed return branch resolves exact continuation");
  }
  bool refused = false;
  try { (void)plan_thread_pointer(site, 0xd53bd040, site + (uintptr_t(1) << 27) - 16); }
  catch (const std::exception &) { refused = true; }
  require(refused, "new return-PC offset rejects old-only boundary");
  std::cout << "M5_MRS_ISLAND_RETURN_PASS signed_directions=2 new_edge_refused=1\n";
}
static void check_mrs_window(const char *executable) {
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
  mrs_refusal(executable, "--mrs-window-low",
              "SHIRAZ_GUEST_FATAL MRS spill window outside prepared native stack extent\n");
  mrs_refusal(executable, "--mrs-window-high",
              "SHIRAZ_GUEST_FATAL MRS spill window outside prepared native stack extent\n");
  std::cout << "M5_MRS_WINDOW_PASS profile=" << kMrsStackWindowProfile
            << " before=" << kMrsStackBytesBefore << " after=" << kMrsStackBytesAfter
            << " exact_boundaries=1 lower_refusal=1 upper_refusal=1 overflow=1\n";
}
int main(int argc, char **argv) {
  try {
    require(argc == 1 || (argc == 2 && !std::strncmp(argv[1], "--mrs-", 6)), "TPIDR probe mode");
    constexpr size_t size = 256 * 1024;
    auto *memory = static_cast<uint8_t *>(mmap(nullptr, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                                               MAP_PRIVATE | MAP_ANON | MAP_JIT, -1, 0));
    require(memory != MAP_FAILED, "TPIDR test MAP_JIT allocation");
    size_t length = uintptr_t(shiraz_tpidr_test_end) - uintptr_t(shiraz_tpidr_test_start);
    size_t site_offset = uintptr_t(shiraz_tpidr_test_site) - uintptr_t(shiraz_tpidr_test_start);
    require(length < 4096 && site_offset + 4 < length, "bounded native test template");
    using Function = void (*)(Result *);
    struct Program { Function function; unsigned target; bool production; };
    std::vector<Program> programs;
    using Protected = int (*)(int);
    Protected protected_function = nullptr;
    size_t used = 0;
    {
      JitWriteScope writable;
      for (int placement = 0; placement < 2; ++placement)
      for (int stress = 0; stress < 3; ++stress) for (unsigned target = 0; target < 32; ++target) {
        if (target == 18) continue;
        if (stress == 2 && target != 0) continue;
        auto *block = memory + used;
        auto *start = block + (placement ? 64 : 0);
        auto *site = start + site_offset;
        auto *island = placement ? block : start + ((length + 15) & ~size_t(15));
        used = size_t((placement ? start + ((length + 15) & ~size_t(15)) : island + 64) - memory);
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
          uint32_t accessor_kind = 0;
          uint32_t continuation_branch = 0;
          std::memcpy(&continuation_branch, patch.island.data() + 24, sizeof(continuation_branch));
          require((continuation_branch & 0xfc000000u) == 0x14000000u,
                  "continuation is an exact B instruction");
          int64_t displacement = continuation_branch & 0x03ffffffu;
          if (displacement & (int64_t(1) << 25)) displacement -= int64_t(1) << 26;
          const auto branch_pc = uintptr_t(island) + 24;
          unwind_pc = displacement < 0 ? branch_pc - uintptr_t(-displacement * 4)
                                       : branch_pc + uintptr_t(displacement * 4);

          std::memcpy(&accessor_kind, patch.island.data() + 52, sizeof(accessor_kind));
          require(unwind_pc == uintptr_t(site) + 4,
                  "island unwind PC must be the exact guest continuation");
          require(accessor_kind == (stress == 0 ? 1u : 0u),
                  "only production MRS islands select register quarantine");
          std::memcpy(island, patch.island.data(), patch.island.size());
        }
        programs.push_back({reinterpret_cast<Function>(start), target, stress == 0});
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
    auto kind = [](const ThreadPointerPatch &patch) {
      uint32_t result = 0;
      std::memcpy(&result, patch.island.data() + 52, sizeof(result));
      return result;
    };
    const auto default_accessor = reinterpret_cast<ThreadPointerAccessor>(
        reinterpret_cast<uintptr_t>(&shiraz_guest_tls_access));
    require(kind(plan_thread_pointer(0x100000000, 0xd53bd040u, 0x100001000, default_accessor)) == 1,
            "explicit production accessor selects the same quarantined path");
    require(kind(plan_system_call(0x100000000, 0xd4000001u, 0x100001000,
                                 reinterpret_cast<uintptr_t>(&shiraz_guest_tls_access))) == 0,
            "syscall kind stays generic even when dispatcher address equals TLS accessor");
    prepare_guest_tls();
    uintptr_t tls = reinterpret_cast<uintptr_t>(guest_tls_base());
    if (argc == 2) {
      require(std::signal(SIGABRT, [](int) { _exit(97); }) != SIG_ERR, "MRS child abort observer");
      const std::string mode = argv[1];
      if (mode == "--mrs-window-low" || mode == "--mrs-window-high") {
        const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
        const auto low = high - pthread_get_stacksize_np(pthread_self());
        const auto anchor = mode == "--mrs-window-low" ? low + kMrsStackBytesBefore - 16
                                                       : high - kMrsStackBytesAfter + 16;
        require(!(anchor & 15) && prepared_guest_stack_contains(anchor), "fresh window anchor is in native extent");
        (void)shiraz_guest_tls_access(0, 0, 0, 0, 0, 0, 0, anchor);
      } else if (mode == "--mrs-corrupt") {
        protected_function(1);
      } else if (mode == "--mrs-released") {
        { GuestTlsCleanupScope cleanup; }
        require(!guest_tls_prepared(), "fresh child really releases prepared cache epoch");
        Result result{};
        programs.front().function(&result);
      } else if (mode == "--mrs-foreign") {
        constexpr size_t bytes = 256 * 1024;
        void *stack = mmap(nullptr, bytes, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
        require(stack != MAP_FAILED, "fresh foreign MRS stack allocation");
        Result result{};
        ForeignCall call{programs.front().function, &result};
#ifdef SHIRAZ_ASAN_FIBER_TEST
        void *suspended_fake_stack = nullptr;
        __sanitizer_start_switch_fiber(&suspended_fake_stack, stack, bytes);
#endif
        shiraz_test_other_stack(static_cast<uint8_t *>(stack) + bytes - 16, &foreign_mrs, &call);
      } else require(false, "known MRS child mode");
      return 98;
    }
    check_return_branch();
    check_mrs_window(argv[0]);
    for (const auto &program : programs) {
      Result result{};
      errno = ELOOP;
      program.function(&result);
      verify(result, program.target, tls);
      require(errno == ELOOP, "TLS accessor preserves host errno");
    }
    std::atomic<int> passed{0}, ready{0}, epochs{0};
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
        // Each owned scope drains its state. Re-read the actual rewritten
        // production MRS after publication in the next epoch, without assuming
        // whether the native allocator reuses the previous TLS address.
        for (int epoch = 0; epoch < 4; ++epoch) {
          {
            GuestTlsCleanupScope cleanup;
            prepare_guest_tls();
            const auto current = reinterpret_cast<uintptr_t>(guest_tls_base());
            for (const auto &program : programs) if (program.production) {
              Result result{};
              errno = ELOOP;
              program.function(&result);
              verify(result, program.target, current);
              require(errno == ELOOP, "prepared MRS cache preserves errno across owned epochs");
            }
          }
          require(!guest_tls_prepared(), "owned epoch removes prepared MRS readiness");
          ++epochs;
        }
        ++passed;
      } catch (...) { if (!arrived) ++ready; }
    });
    for (auto &thread : threads) thread.join();
    require(passed == 4 && epochs == 16, "concurrent state-preserving TPIDR reads and owned cache epochs");
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
    mrs_refusal(argv[0], "--mrs-corrupt", "SHIRAZ_GUEST_FATAL stack guard check failed\n");
    mrs_refusal(argv[0], "--mrs-foreign", "SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n");
    mrs_refusal(argv[0], "--mrs-released", "SHIRAZ_GUEST_FATAL MRS SP outside prepared native stack extent\n");
    require(munmap(memory, size) == 0, "TPIDR test cleanup");
    std::cout << "M5_MRS_SCRATCH_CLEAR_PASS shared_bytes=832 island_bytes=16 words=106 "
                 "island_placements=2 actual_default_targets=30 generic_stress=1 syscall=1 "
                 "deeper_native_frames=unclaimed async_proof=0\n";
    std::cout << "M5_TPIDR_THUNK_PASS island_placements=2 targets=31 accessors=2 gpr=31 vector_bits=4096 "
                 "flags=NZCV,FPCR,FPSR sp=1 lr=1 pac=1 threads=4 malformed=6 guard=1 corruption_abort=1 "
                 "host_tpidr_written=0 elf_auto_rewrite=0 unwind_verified=0\n";
    std::cout << "M5_TPIDR_STACK_PASS foreign_stack_rejected=1 original_sp=1 syscall_arguments=7\n";
    std::cout << "M5_MRS_PREPARED_CACHE_PASS actual_rewritten_mrs=1 threads=4 owned_epochs=16 "
                 "all_default_targets=31 errno=1 kind_flag=1 syscall_same_address_generic=1 "
                 "released_epoch_refused=1 fresh_refusals=5 native_memory_purity=0 lifetime_lease=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
