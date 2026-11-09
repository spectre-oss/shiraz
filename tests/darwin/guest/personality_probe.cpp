// SPDX-License-Identifier: GPL-3.0-only
#include "guest_errno.hpp"
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <cstddef>
#include <chrono>
#include <cerrno>
#include <condition_variable>
#include <csignal>
#include <sys/wait.h>
#include <sys/mman.h>
#include <poll.h>
#include <unistd.h>
#include <iostream>
#include <mutex>
#include <malloc/malloc.h>
#include <cstring>
#include <cstdlib>
#include <thread>
#include <string>
#include <unwind.h>
#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#include <sanitizer/common_interface_defs.h>
#define SHIRAZ_MALLOC_ASAN_FIBER 1
#endif
#endif

using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value)
    throw std::runtime_error(message);
}
extern "C" void *shiraz_guest_managed_malloc_entry(size_t);
extern "C" int shiraz_test_malloc_other_stack(void *, int (*)(void *), void *);
asm(R"MALLOC_STACK_ASM(
.text
.p2align 2
.globl _shiraz_test_malloc_other_stack
_shiraz_test_malloc_other_stack:
    stp x29, x30, [sp, #-32]!
    stp x19, x20, [sp, #16]
    mov x19, sp
    mov x20, x1
    mov sp, x0
    mov x0, x2
    blr x20
    mov sp, x19
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
)MALLOC_STACK_ASM");
static int call_direct_malloc(void *foreign) {
  (void)foreign;
#ifdef SHIRAZ_MALLOC_ASAN_FIBER
  if (foreign) __sanitizer_finish_switch_fiber(nullptr, nullptr, nullptr);
#endif
  void *result = shiraz_guest_managed_malloc_entry(64);
  release_guest_allocation(result);
  constexpr char message[] = "UNEXPECTED_MANAGED_MALLOC_RETURN\n";
  (void)::write(STDERR_FILENO, message, sizeof(message) - 1);
  return 98;
}
static int malloc_refusal_child(bool foreign_stack) {
  require(!guest_tls_prepared(), "fresh malloc refusal child starts unprepared");
  require(std::signal(SIGABRT, [](int) { _exit(97); }) != SIG_ERR,
          "malloc refusal abort observer");
  if (!foreign_stack) return call_direct_malloc(nullptr);
  prepare_guest_tls();
  require(guest_tls_prepared(), "foreign malloc stack case has prepared TLS");
  constexpr size_t bytes = 256 * 1024;
  void *memory = mmap(nullptr, bytes, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
  require(memory != MAP_FAILED, "foreign malloc test stack allocation");
  void *top = static_cast<uint8_t *>(memory) + bytes - 16;
  require(!prepared_guest_stack_contains(reinterpret_cast<uintptr_t>(top)),
          "foreign malloc test stack lies outside prepared extent");
  // The called body is noexcept and must abort. No exception unwind across
  // this test-only pivot or asynchronous stack behavior is claimed.
#ifdef SHIRAZ_MALLOC_ASAN_FIBER
  // Notify ASan of the deliberate child-only stack switch. The original fake
  // stack stays suspended; this fatal test never resumes its native frame.
  void *suspended_fake_stack = nullptr;
  __sanitizer_start_switch_fiber(&suspended_fake_stack, memory, bytes);
#endif
  return shiraz_test_malloc_other_stack(top, &call_direct_malloc, reinterpret_cast<void *>(uintptr_t(1)));
}
class MallocChildDisposition {
  struct sigaction previous{};
public:
  MallocChildDisposition() {
    require(!sigaction(SIGCHLD, nullptr, &previous), "snapshot malloc child disposition");
    struct sigaction action{};
    sigemptyset(&action.sa_mask);
    action.sa_handler = SIG_DFL;
    require(!sigaction(SIGCHLD, &action, nullptr), "keep malloc refusal child waitable");
  }
  ~MallocChildDisposition() {
    if (sigaction(SIGCHLD, &previous, nullptr)) _exit(95);
  }
};
static void malloc_refusal_case(const char *executable, const char *mode, const char *expected) {
  MallocChildDisposition disposition;
  int descriptors[2];
  require(!pipe(descriptors), "malloc refusal diagnostic pipe");
  char *child_argv[] = {const_cast<char *>(executable), const_cast<char *>(mode), nullptr};
  const pid_t child = fork();
  require(child >= 0, "malloc refusal fork");
  if (!child) {
    close(descriptors[0]);
    if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
    close(descriptors[1]);
    execv(executable, child_argv);
    _exit(96);
  }
  close(descriptors[1]);
  const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
  std::string diagnostic;
  ssize_t count = -1;
  bool failed = false;
  char buffer[512];
  while (std::chrono::steady_clock::now() < deadline) {
    pollfd input{descriptors[0], POLLIN | POLLHUP, 0};
    const int ready = poll(&input, 1, 100);
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
    // Do not signal a PID after ECHILD has removed our ownership.
    if (waited == 0 || (waited < 0 && wait_error == EINTR)) {
      kill(child, SIGKILL);
      do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    }
  }
  const bool refused = !failed && waited == child && WIFEXITED(status) &&
      WEXITSTATUS(status) == 97 && count == 0 && diagnostic == expected;
  if (!refused)
    std::cerr << "malloc refusal mode=" << mode << " status=" << status << " read=" << count
              << " failed=" << failed << " wait_errno=" << wait_error
              << " diagnostic=" << diagnostic << std::endl;
  require(refused, "actual typed malloc admission refuses before normal return");
}
static void malloc_admission_tests(const char *executable, const Providers &registry) {
  uintptr_t allocate = 0;
  for (const auto &entry : registry.modules().at("libc.so").exports)
    if (entry.name == "malloc") allocate = entry.address;
  require(allocate == reinterpret_cast<uintptr_t>(&shiraz_guest_managed_malloc_entry),
          "managed malloc exports actual static typed entry");
  malloc_refusal_case(executable, "--malloc-unprepared",
                     "SHIRAZ_GUEST_FATAL managed malloc requires prepared guest TLS\n");
  malloc_refusal_case(executable, "--malloc-foreign-stack",
                     "SHIRAZ_GUEST_FATAL managed malloc SP outside prepared native stack extent\n");
  std::cout << "M5_MALLOC_ADMISSION_PASS static_entry=1 unprepared_refused=1 foreign_sp_refused=1 "
               "fresh_processes=2 deadline_seconds=10 native_memory_purity=0\n" << std::flush;
}
// Raw machine observations precede any C++ instruction after the call. This
// fixture checks declared register effects, not hidden native spill contents.
struct alignas(16) ScalarScratchSnapshot {
  uint64_t integers[2];
  uint8_t vectors[2][16];
  uint64_t scratch[16];
  uint8_t simd[30][16];
  uint64_t before_x18, after_x18, before_sp, after_sp;
  uint64_t before_x29, after_x29, after_x20, after_lr;
};
static_assert(offsetof(ScalarScratchSnapshot, vectors) == 16);
static_assert(offsetof(ScalarScratchSnapshot, scratch) == 48);
static_assert(offsetof(ScalarScratchSnapshot, simd) == 176);
static_assert(offsetof(ScalarScratchSnapshot, before_x18) == 656);
static_assert(offsetof(ScalarScratchSnapshot, before_sp) == 672);
static_assert(offsetof(ScalarScratchSnapshot, before_x29) == 688);
static_assert(offsetof(ScalarScratchSnapshot, after_x20) == 704);
static_assert(offsetof(ScalarScratchSnapshot, after_lr) == 712);
static_assert(sizeof(ScalarScratchSnapshot) == 720);
extern "C" void shiraz_test_scalar_scratch(void *, ScalarScratchSnapshot *, uint64_t);
extern "C" uintptr_t shiraz_test_scalar_scratch_return_pc();
asm(R"SHIRAZ_ASM(
.text
.p2align 2
.globl _shiraz_test_scalar_scratch
_shiraz_test_scalar_scratch:
.cfi_startproc
    sub sp, sp, #96
.cfi_def_cfa_offset 96
    stp x19, x20, [sp, #0]
.cfi_offset 19, -96
.cfi_offset 20, -88
    stp x29, x30, [sp, #16]
.cfi_offset 29, -80
.cfi_offset 30, -72
    stp d8, d9, [sp, #32]
.cfi_offset 72, -64
.cfi_offset 73, -56
    stp d10, d11, [sp, #48]
.cfi_offset 74, -48
.cfi_offset 75, -40
    stp d12, d13, [sp, #64]
.cfi_offset 76, -32
.cfi_offset 77, -24
    stp d14, d15, [sp, #80]
.cfi_offset 78, -16
.cfi_offset 79, -8
    mov x20, x0
    mov x19, x1
    mov x0, x2
    str x18, [x19, #656]
    mov x9, sp
    str x9, [x19, #672]
    str x29, [x19, #688]
    mov x2, #0x5a5a
    movk x2, #0x5a5a, lsl #16
    movk x2, #0x5a5a, lsl #32
    movk x2, #0x5a5a, lsl #48
    mov x3, x2
    mov x4, x2
    mov x5, x2
    mov x6, x2
    mov x7, x2
    mov x8, x2
    mov x9, x2
    mov x10, x2
    mov x11, x2
    mov x12, x2
    mov x13, x2
    mov x14, x2
    mov x15, x2
    mov x16, x2
    mov x17, x2
    movi v2.16b, #0x5a
    movi v3.16b, #0x5a
    movi v4.16b, #0x5a
    movi v5.16b, #0x5a
    movi v6.16b, #0x5a
    movi v7.16b, #0x5a
    movi v8.16b, #0x5a
    movi v9.16b, #0x5a
    movi v10.16b, #0x5a
    movi v11.16b, #0x5a
    movi v12.16b, #0x5a
    movi v13.16b, #0x5a
    movi v14.16b, #0x5a
    movi v15.16b, #0x5a
    movi v16.16b, #0x5a
    movi v17.16b, #0x5a
    movi v18.16b, #0x5a
    movi v19.16b, #0x5a
    movi v20.16b, #0x5a
    movi v21.16b, #0x5a
    movi v22.16b, #0x5a
    movi v23.16b, #0x5a
    movi v24.16b, #0x5a
    movi v25.16b, #0x5a
    movi v26.16b, #0x5a
    movi v27.16b, #0x5a
    movi v28.16b, #0x5a
    movi v29.16b, #0x5a
    movi v30.16b, #0x5a
    movi v31.16b, #0x5a
    blr x20
Lshiraz_scalar_scratch_return:
    stp x0, x1, [x19, #0]
    stp q0, q1, [x19, #16]
    stp x2, x3, [x19, #48]
    stp x4, x5, [x19, #64]
    stp x6, x7, [x19, #80]
    stp x8, x9, [x19, #96]
    stp x10, x11, [x19, #112]
    stp x12, x13, [x19, #128]
    stp x14, x15, [x19, #144]
    stp x16, x17, [x19, #160]
    stp q2, q3, [x19, #176]
    stp q4, q5, [x19, #208]
    stp q6, q7, [x19, #240]
    stp q8, q9, [x19, #272]
    stp q10, q11, [x19, #304]
    stp q12, q13, [x19, #336]
    stp q14, q15, [x19, #368]
    stp q16, q17, [x19, #400]
    stp q18, q19, [x19, #432]
    stp q20, q21, [x19, #464]
    stp q22, q23, [x19, #496]
    stp q24, q25, [x19, #528]
    stp q26, q27, [x19, #560]
    stp q28, q29, [x19, #592]
    stp q30, q31, [x19, #624]
    str x18, [x19, #664]
    mov x9, sp
    str x9, [x19, #680]
    str x29, [x19, #696]
    str x20, [x19, #704]
    str x30, [x19, #712]
    ldp d8, d9, [sp, #32]
.cfi_restore 72
.cfi_restore 73
    ldp d10, d11, [sp, #48]
.cfi_restore 74
.cfi_restore 75
    ldp d12, d13, [sp, #64]
.cfi_restore 76
.cfi_restore 77
    ldp d14, d15, [sp, #80]
.cfi_restore 78
.cfi_restore 79
    ldp x19, x20, [sp, #0]
.cfi_restore 19
.cfi_restore 20
    ldp x29, x30, [sp, #16]
.cfi_restore 29
.cfi_restore 30
    add sp, sp, #96
.cfi_def_cfa_offset 0
    ret
.cfi_endproc
.p2align 2
.globl _shiraz_test_scalar_scratch_return_pc
_shiraz_test_scalar_scratch_return_pc:
    adr x0, Lshiraz_scalar_scratch_return
    ret
)SHIRAZ_ASM");

struct ScalarUnwindProbe { unsigned frames = 0; bool caller = false; };
static _Unwind_Reason_Code scalar_unwind_frame(_Unwind_Context *context, void *opaque) {
  auto &probe = *static_cast<ScalarUnwindProbe *>(opaque);
  if (_Unwind_GetIP(context) == shiraz_test_scalar_scratch_return_pc()) {
    probe.caller = true;
    return _URC_END_OF_STACK;
  }
  return ++probe.frames < 64 ? _URC_NO_REASON : _URC_END_OF_STACK;
}

static void scalar_scratch_tests(const Providers &registry) {
  constexpr uint64_t first = 0x13579bdf2468ace0ULL;
  constexpr uint64_t second = 0xfedcba9876543210ULL;
  auto *function = scalar_closure([](Capture &capture) {
    ScalarUnwindProbe unwind;
    (void)_Unwind_Backtrace(scalar_unwind_frame, &unwind);
    require(unwind.caller, "native unwind traverses scalar closure to exact caller continuation");
    capture.frame.result_integers[0] = first;
    capture.frame.result_integers[1] = second;
    for (unsigned byte = 0; byte < 16; ++byte) {
      capture.frame.result_vectors[0][byte] = uint8_t(0xa0 + byte);
      capture.frame.result_vectors[1][byte] = uint8_t(0xb0 + byte);
    }
  });
  auto check = [&](const ScalarScratchSnapshot &snapshot, void *entry) {
    for (auto value : snapshot.scratch)
      require(value == 0, "scalar closure clears x2-x17 at actual return");
    for (unsigned reg = 2; reg < 32; ++reg)
      for (unsigned byte = 0; byte < 16; ++byte) {
        const uint8_t expected = reg >= 8 && reg <= 15 && byte < 8 ? 0x5a : 0;
        require(snapshot.simd[reg - 2][byte] == expected,
                "scalar closure clears caller SIMD while preserving d8-d15 low halves");
      }
    require(snapshot.before_x18 == snapshot.after_x18, "scalar closure preserves Darwin x18");
    require(snapshot.before_sp == snapshot.after_sp && !(snapshot.after_sp & 15),
            "scalar closure restores aligned SP");
    require(snapshot.before_x29 == snapshot.after_x29 &&
                snapshot.after_x20 == reinterpret_cast<uintptr_t>(entry),
            "scalar closure preserves callee frame and function registers");
    require(snapshot.after_lr == shiraz_test_scalar_scratch_return_pc(),
            "scalar closure restores exact native continuation");
  };
  prepare_guest_tls();
  ScalarScratchSnapshot snapshot{};
  shiraz_test_scalar_scratch(function, &snapshot, 64);
  check(snapshot, function);
  require(snapshot.integers[0] == first && snapshot.integers[1] == second,
          "scalar cleanup retains both integer result carriers");
  for (unsigned byte = 0; byte < 16; ++byte)
    require(snapshot.vectors[0][byte] == uint8_t(0xa0 + byte) &&
                snapshot.vectors[1][byte] == uint8_t(0xb0 + byte),
            "scalar cleanup retains both complete vector result carriers");

  uintptr_t allocate = 0, release = 0;
  for (const auto &entry : registry.modules().at("libc.so").exports) {
    if (entry.name == "malloc") allocate = entry.address;
    if (entry.name == "free") release = entry.address;
  }
  require(allocate && release, "actual managed allocator exports available");
  snapshot = {};
  function = reinterpret_cast<void *>(allocate);
  shiraz_test_scalar_scratch(function, &snapshot, 64);
  check(snapshot, function);
  require(snapshot.integers[0] &&
              registered_guest_heap_contains(snapshot.integers[0], 64),
          "actual managed malloc returns registered payload");
  require(snapshot.integers[1] == 0, "managed malloc's unused integer result stays zero");
  for (const auto &vector : snapshot.vectors)
    for (auto byte : vector)
      require(byte == 0, "managed malloc's unused vector result carriers stay zero");
  invoke(reinterpret_cast<void *>(release),
         {Argument::integer(int64_t(snapshot.integers[0]), ArgumentType::Int64)});
  require(!registered_guest_heap_contains(snapshot.integers[0], 1),
          "actual managed scratch-test allocation is released");
  std::cout << "M5_SCALAR_SCRATCH_PASS typed_fixture=1 managed_malloc=1 gprs=16 "
               "simd_full=22 simd_upper=8 result_carriers=4 platform_x18=1 "
               "sp=1 continuation=1 callee_d_low=8 native_unwind=1 native_spill_proof=0\n" << std::flush;
}

static void host_returns() {}
static long host_fail() {
  errno = EAGAIN;
  return -1;
}
static void host_barrier() {
  static std::mutex mutex;
  static std::condition_variable condition;
  static unsigned arrived = 0;
  std::unique_lock<std::mutex> lock(mutex);
  if (++arrived == 4)
    condition.notify_all();
  else if (!condition.wait_for(lock, std::chrono::seconds(5),
                               [&] { return arrived == 4; }))
    throw std::runtime_error("guest errno thread barrier timeout");
}
extern "C" void shiraz_test_byte_copy(void *, void *, const void *, size_t, uintptr_t *);
asm(R"BYTE_COPY_ASM(
.text
.p2align 2
.globl _shiraz_test_byte_copy
_shiraz_test_byte_copy:
    stp x29, x30, [sp, #-96]!
    stp x19, x20, [sp, #16]
    stp d8, d9, [sp, #32]
    stp d10, d11, [sp, #48]
    stp d12, d13, [sp, #64]
    stp d14, d15, [sp, #80]
    mov x29, sp
    mov x20, x0
    mov x19, x4
    mov x0, x1
    mov x1, x2
    mov x2, x3
    mov x3, #0x5a5a
    mov x4, x3
    mov x5, x3
    mov x6, x3
    str x18, [x19, #56]
    mov x9, sp
    str x9, [x19, #72]
    mov x7, #0x5a5a
    mov x8, #0x5a5a
    mov x9, #0x5a5a
    mov x10, #0x5a5a
    mov x11, #0x5a5a
    mov x12, #0x5a5a
    mov x13, #0x5a5a
    mov x14, #0x5a5a
    mov x15, #0x5a5a
    mov x16, #0x5a5a
    mov x17, #0x5a5a
    movi v0.16b, #0x5a
    movi v1.16b, #0x5a
    movi v2.16b, #0x5a
    movi v3.16b, #0x5a
    movi v4.16b, #0x5a
    movi v5.16b, #0x5a
    movi v6.16b, #0x5a
    movi v7.16b, #0x5a
    movi v8.16b, #0x5a
    movi v9.16b, #0x5a
    movi v10.16b, #0x5a
    movi v11.16b, #0x5a
    movi v12.16b, #0x5a
    movi v13.16b, #0x5a
    movi v14.16b, #0x5a
    movi v15.16b, #0x5a
    movi v16.16b, #0x5a
    movi v17.16b, #0x5a
    movi v18.16b, #0x5a
    movi v19.16b, #0x5a
    movi v20.16b, #0x5a
    movi v21.16b, #0x5a
    movi v22.16b, #0x5a
    movi v23.16b, #0x5a
    movi v24.16b, #0x5a
    movi v25.16b, #0x5a
    movi v26.16b, #0x5a
    movi v27.16b, #0x5a
    movi v28.16b, #0x5a
    movi v29.16b, #0x5a
    movi v30.16b, #0x5a
    movi v31.16b, #0x5a
    blr x20
    stp q0, q1, [x19, #96]
    stp q2, q3, [x19, #128]
    stp q4, q5, [x19, #160]
    stp q6, q7, [x19, #192]
    stp q8, q9, [x19, #224]
    stp q10, q11, [x19, #256]
    stp q12, q13, [x19, #288]
    stp q14, q15, [x19, #320]
    stp q16, q17, [x19, #352]
    stp q18, q19, [x19, #384]
    stp q20, q21, [x19, #416]
    stp q22, q23, [x19, #448]
    stp q24, q25, [x19, #480]
    stp q26, q27, [x19, #512]
    stp q28, q29, [x19, #544]
    stp q30, q31, [x19, #576]
    str x7, [x19, #608]
    str x8, [x19, #616]
    str x9, [x19, #624]
    str x10, [x19, #632]
    str x11, [x19, #640]
    str x12, [x19, #648]
    str x13, [x19, #656]
    str x14, [x19, #664]
    str x15, [x19, #672]
    str x16, [x19, #680]
    str x17, [x19, #688]
    stp x0, x1, [x19]
    stp x2, x3, [x19, #16]
    stp x4, x5, [x19, #32]
    str x6, [x19, #48]
    str x18, [x19, #64]
    mov x9, sp
    str x9, [x19, #80]
    ldp d8, d9, [sp, #32]
    ldp d10, d11, [sp, #48]
    ldp d12, d13, [sp, #64]
    ldp d14, d15, [sp, #80]
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #96
    ret
)BYTE_COPY_ASM");
static void byte_copy_tests(const Providers &registry) {
  using Copy = void *(*)(void *, const void *, size_t);
  const auto page = size_t(sysconf(_SC_PAGESIZE));
  require(page >= 4096, "byte copy page size");
  struct Guarded {
    void *mapping;
    size_t page;
    explicit Guarded(size_t bytes) : mapping(mmap(nullptr, bytes * 3, PROT_NONE,
        MAP_PRIVATE | MAP_ANON, -1, 0)), page(bytes) {
      require(mapping != MAP_FAILED, "byte copy guarded mapping");
      require(!mprotect(static_cast<uint8_t *>(mapping) + page, page, PROT_READ | PROT_WRITE),
              "byte copy readable middle page");
    }
    ~Guarded() { munmap(mapping, page * 3); }
    uint8_t *data() { return static_cast<uint8_t *>(mapping) + page; }
  } source(page), destination(page);
  auto observed_copy = [&](void *entry, void *dst, const void *src, size_t length) {
    uintptr_t observed[88]{};
    shiraz_test_byte_copy(entry, dst, src, length, observed);
    require(observed[0] == reinterpret_cast<uintptr_t>(dst) &&
                observed[1] == reinterpret_cast<uintptr_t>(src) && observed[2] == length,
            "byte copy returns destination and preserves source/count registers");
    for (size_t index = 3; index <= 6; ++index)
      require(!observed[index], "byte copy scratch cleared on return");
    require(observed[7] == observed[8] && observed[9] == observed[10],
            "byte copy preserves platform register and SP");
    const auto *vectors = reinterpret_cast<const uint8_t *>(observed) + 96;
    for (size_t index = 0; index < 512; ++index)
      require(vectors[index] == 0x5a, "byte copy preserves full SIMD register contents");
    for (size_t index = 76; index < 87; ++index)
      require(observed[index] == 0x5a5a, "byte copy preserves unused scratch GPRs");
  };
  size_t cases = 0;
  for (const char *name : {"memcpy", "memmove"}) {
    const ProviderExport *selected = nullptr;
    for (const auto &item : registry.modules().at("libc.so").exports)
      if (item.name == name) selected = &item;
    require(selected && selected->abi == (std::strcmp(name, "memcpy") == 0
                ? "memory/byte-copy-v1" : "memory/byte-move-v1"), "explicit byte leaf binding");
    auto function = reinterpret_cast<Copy>(selected->address);
    int *guest_error = guest_errno_location();
    *guest_error = 777;
    errno = ELOOP;
    require(function(nullptr, nullptr, 0) == nullptr, "zero-length byte copy does not touch null");
    require(function(destination.data(), source.data(), 0) == destination.data(), "zero copy return");
    for (size_t length : {size_t(0), size_t(1), size_t(2), size_t(7), size_t(8), size_t(15),
                          size_t(16), size_t(31), size_t(32), size_t(112), size_t(255), page}) {
      for (bool at_end : {false, true}) {
        auto *src = source.data() + (at_end ? page - length : 0);
        auto *dst = destination.data() + (at_end ? page - length : 0);
        for (size_t index = 0; index < length; ++index) src[index] = uint8_t(index * 37 + 19);
        std::memset(destination.data(), 0xa5, page);
        require(!mprotect(source.data(), page, PROT_READ), "source read-only guard");
        observed_copy(reinterpret_cast<void *>(function), dst, src, length);
        require(!std::memcmp(dst, src, length), "byte copy guarded payload");
        if (dst != destination.data()) require(dst[-1] == 0xa5, "no byte before destination");
        if (dst + length != destination.data() + page) require(dst[length] == 0xa5, "no byte after destination");
        require(!mprotect(source.data(), page, PROT_READ | PROT_WRITE), "restore source writes");
        ++cases;
      }
    }
    if (std::strcmp(name, "memmove") == 0) {
      for (size_t length : {size_t(2), size_t(7), size_t(16), size_t(112), size_t(255)}) {
        for (bool at_end : {false, true}) for (bool backward : {false, true}) {
          std::vector<uint8_t> expected(page);
          for (size_t i = 0; i < page; ++i) expected[i] = destination.data()[i] = uint8_t(i * 19 + 3);
          const size_t begin = at_end ? page - length - 1 : 0;
          const size_t src = begin + (backward ? 0 : 1), dst = begin + (backward ? 1 : 0);
          std::memmove(expected.data() + dst, expected.data() + src, length);
          observed_copy(reinterpret_cast<void *>(function), destination.data() + dst,
                        destination.data() + src, length);
          require(!std::memcmp(destination.data(), expected.data(), page),
                  "guarded overlapping byte move matches independent expected page");
          ++cases;
        }
      }
    }
    require(errno == ELOOP && *guest_error == 777, "byte leaves preserve both errno domains");
    for (size_t src = 0; src < 16; ++src) for (size_t dst = 0; dst < 16; ++dst)
      for (size_t length = 0; length <= 64; ++length) {
        if (std::strcmp(name, "memcpy") == 0 && src < dst + length && dst < src + length) continue;
        uint8_t actual[96], expected[96];
        for (size_t i = 0; i < sizeof(actual); ++i) actual[i] = expected[i] = uint8_t(i * 13 + 1);
        std::memmove(expected + dst, expected + src, length);
        require(function(actual + dst, actual + src, length) == actual + dst &&
                    !std::memcmp(actual, expected, sizeof(actual)), "byte copy/move alignment and overlap corpus");
        ++cases;
      }
  }
  std::cout << "M5_BYTE_COPY_PASS cases=" << cases
            << " guard_pages=1 readonly_source=1 overlap=1 zero_null=1 return_arguments=1 scratch_clear=1 untouched_gprs=11 simd_full=32 platform_sp=1 errno=1 host_libc_dispatch=0 app_execution=0\n";
}

int main(int argc, char **argv) {
  try {
    if (argc == 2 && std::strcmp(argv[1], "--malloc-unprepared") == 0)
      return malloc_refusal_child(false);
    if (argc == 2 && std::strcmp(argv[1], "--malloc-foreign-stack") == 0)
      return malloc_refusal_child(true);
    require(argc == 2, "usage: personality-probe fixture-directory");
    bool rejected_object = false, rejected_null = false, rejected_empty = false;
    try {
      Providers invalid;
      int object = 0;
      invalid.add({"libbad_effect.so", {},
                   {{"object", "", reinterpret_cast<uintptr_t>(&object),
                     SymbolKind::Object, "fixture/object", false, true}}, false});
    } catch (const ElfError &) { rejected_object = true; }
    try { (void)explicit_noreturn_export("missing", "", nullptr); }
    catch (const ElfError &) { rejected_null = true; }
    try { (void)explicit_noreturn_closure_export("missing", "", {}); }
    catch (const ElfError &) { rejected_empty = true; }
    require(rejected_object && rejected_null && rejected_empty,
            "nonreturning metadata rejects objects, null pointers and empty closures");
    auto registry = core_personality();
    byte_copy_tests(registry);
    malloc_admission_tests(argv[0], registry);
    scalar_scratch_tests(registry);
    Provider helper{"libfixture_host.so", {}, {}, false};
    helper.exports.push_back(
        fixed_export("host_fail", "", &host_fail, ErrnoPolicy::MinusOne));
    helper.exports.push_back(fixed_export("host_barrier", "", &host_barrier));
    helper.exports.push_back(explicit_noreturn_export("declared_terminal", "", &host_returns));
    auto cookie = std::make_shared<int>(42);
    helper.exports.push_back(explicit_noreturn_closure_export("captured_terminal", "", [cookie] {
      if (*cookie != 42) _exit(95);
    }));
    cookie.reset();
    registry.add(std::move(helper));
    Provider strict{"libstrict.so", {"STRICT_1"}, {}, false};
    strict.exports.push_back(fixed_export("plain", "", &host_fail));
    strict.exports.push_back(fixed_export("named", "STRICT_1", &host_fail));
    registry.add(std::move(strict));
    Loader loader({argv[1]}, std::move(registry));
    auto *strict_module = loader.link("libstrict.so");
    require(loader.symbol(strict_module, "plain").found &&
                loader.symbol(strict_module, "named", "STRICT_1").found,
            "explicit provider default and exact version lookups");
    for (const char *name : {"plain", "named"}) {
      bool rejected = false;
      try { loader.symbol(strict_module, name, "UNKNOWN_VERSION"); }
      catch (const ElfError &) { rejected = true; }
      require(rejected, "versioned provider rejects unknown named version");
    }
    auto *helper_module = loader.link("libfixture_host.so");
    for (const char *name : {"declared_terminal", "captured_terminal"}) {
      auto terminal = loader.symbol(helper_module, name);
      require(terminal.nonreturning &&
                  !loader.symbol(helper_module, "host_fail").nonreturning,
              "selected provider contract is explicit rather than name-inferred");
      pid_t terminal_child = fork();
      require(terminal_child >= 0, "nonreturning contract child");
      if (!terminal_child) {
        std::signal(SIGABRT, [](int) { _exit(96); });
        invoke(reinterpret_cast<void *>(terminal.address), {});
        _exit(98);
      }
      int terminal_status;
      require(waitpid(terminal_child, &terminal_status, 0) == terminal_child &&
                  WIFEXITED(terminal_status) && WEXITSTATUS(terminal_status) == 96,
              "returning pointer and captured implementations cannot violate the terminal contract");
    }
    auto *module = loader.open("libpersonality.so");
    require(loader.image_count() == 1,
            "personality modules must not load link-only ELF stubs");
    auto call = [&](const char *name) {
      return int(
          invoke(reinterpret_cast<void *>(loader.symbol(module, name).address),
                 {})
              .result_integers[0]);
    };
    errno = ELOOP;
    require(call("memory_test") == 73, "memory/allocation personality");
    require(errno == ELOOP, "host errno preserved by memory adapters");
    auto allocation = [&](const char *name, std::vector<Argument> arguments) {
      errno = ELOOP;
      auto result = invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments)
          .result_integers[0];
      require(errno == ELOOP, "managed allocation boundary preserves host errno");
      return result;
    };
    auto integer = [](uint64_t value) { return Argument::integer(value, ArgumentType::Int64); };
    auto checked_errno = [&] {
      require(errno == ELOOP && call("allocation_errno") == 777 && errno == ELOOP,
              "managed allocation success preserves both host and guest errno");
    };
    auto live = [&](uintptr_t pointer, size_t bytes) {
      require(pointer && registered_guest_heap_contains(pointer, bytes),
              "returned allocation is registered before guest return");
      const auto usable = malloc_size(reinterpret_cast<void *>(pointer));
      require(usable >= bytes && registered_guest_heap_contains(pointer, usable) &&
                  !registered_guest_heap_contains(pointer, uint64_t(usable) + 1) &&
                  !registered_guest_heap_contains(pointer, UINT64_MAX),
              "registered extent includes usable storage and refuses crossing/overflow");
    };
    auto release = [&](uintptr_t pointer) {
      require(allocation("allocation_free", {integer(pointer)}) == 73,
              "guest allocator free completes");
      require(!registered_guest_heap_contains(pointer, 1),
              "known free removes the allocation reservation");
      checked_errno();
    };
    auto pointer = uintptr_t(allocation("allocation_malloc", {integer(96)}));
    live(pointer, 96);
    checked_errno();
    uintptr_t transferred[4]{};
    bool transfer_ok[4]{};
    std::vector<std::thread> producers;
    for (int i = 0; i < 4; ++i) producers.emplace_back([&, i] {
      try {
        transferred[i] = allocation("allocation_malloc", {integer(64)});
        live(transferred[i], 64);
        checked_errno();
        transfer_ok[i] = true;
      } catch (...) {}
    });
    for (auto &thread : producers) thread.join();
    for (bool success : transfer_ok) require(success, "guest allocation producer completes");
    std::vector<std::thread> consumers;
    for (int i = 0; i < 4; ++i) consumers.emplace_back([&, i] {
      try { release(transferred[(i + 1) % 4]); }
      catch (...) { transfer_ok[i] = false; }
    });
    for (auto &thread : consumers) thread.join();
    for (bool success : transfer_ok)
      require(success, "managed allocation survives producer TLS exit and another thread frees it");
    auto *payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 96; ++index)
      payload[index] = static_cast<unsigned char>(index * 7 + 3);
    require(!allocation("allocation_realloc", {integer(pointer), integer(UINT64_MAX)}) &&
                call("allocation_errno") == bionic::kENOMEM && errno == ELOOP,
            "unrepresentable realloc size fails before the native allocator");
    live(pointer, 96);
    for (size_t index = 0; index < 96; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "failed realloc retains the old allocation and payload");
    auto previous = pointer;
    pointer = uintptr_t(allocation("allocation_realloc", {integer(pointer), integer(1024 * 1024)}));
    live(pointer, 1024 * 1024);
    checked_errno();
    // A moving realloc may coalesce into a lower block which now contains
    // the old numeric address. The snapshot has no allocation-generation
    // identity, so assert old-address removal only outside the new extent.
    if (pointer != previous &&
        (previous < pointer || previous - pointer >= malloc_size(reinterpret_cast<void *>(pointer))))
      require(!registered_guest_heap_contains(previous, 1),
              "moved realloc removes its previous allocation reservation");
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 96; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "growing realloc preserves the old payload");
    previous = pointer;
    pointer = uintptr_t(allocation("allocation_realloc", {integer(pointer), integer(17)}));
    live(pointer, 17);
    checked_errno();
    if (pointer != previous &&
        (previous < pointer || previous - pointer >= malloc_size(reinterpret_cast<void *>(pointer))))
      require(!registered_guest_heap_contains(previous, 1),
              "shrinking moved realloc removes its previous reservation");
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 17; ++index)
      require(payload[index] == static_cast<unsigned char>(index * 7 + 3),
              "shrinking realloc preserves the retained payload");
    release(pointer);
    require(!allocation("allocation_calloc", {integer(UINT64_MAX), integer(2)}) &&
                call("allocation_errno") == bionic::kENOMEM && errno == ELOOP,
            "calloc multiplication overflow fails before the native allocator");
    pointer = uintptr_t(allocation("allocation_calloc", {integer(8), integer(16)}));
    live(pointer, 128);
    checked_errno();
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 128; ++index)
      require(payload[index] == 0, "managed calloc zeroes the requested payload");
    release(pointer);
    pointer = uintptr_t(allocation("allocation_realloc", {integer(0), integer(48)}));
    live(pointer, 48);
    checked_errno();
    release(pointer);
    // Host-allocated pointers are valid inputs to the existing native-backed
    // personality. They are not described as managed until a successful
    // guest realloc returns them through the managed boundary.
    auto *foreign = static_cast<unsigned char *>(std::malloc(64));
    require(foreign, "host foreign allocation");
    std::memset(foreign, 0x5a, 64);
    auto foreign_address = reinterpret_cast<uintptr_t>(foreign);
    require(!registered_guest_heap_contains(foreign_address, 1),
            "foreign native allocation has no managed reservation");
    pointer = uintptr_t(allocation("allocation_realloc", {integer(foreign_address), integer(4096)}));
    live(pointer, 4096);
    checked_errno();
    payload = reinterpret_cast<unsigned char *>(pointer);
    for (size_t index = 0; index < 64; ++index)
      require(payload[index] == 0x5a, "foreign realloc retains its original payload");
    release(pointer);
    foreign = static_cast<unsigned char *>(std::malloc(64));
    require(foreign, "host foreign allocation for guest free");
    foreign_address = reinterpret_cast<uintptr_t>(foreign);
    require(allocation("allocation_free", {integer(foreign_address)}) == 73 &&
                !registered_guest_heap_contains(foreign_address, 1),
            "guest free preserves compatibility with a foreign native allocation");
    checked_errno();
    require(call("parse_test") == 73 && errno == ELOOP,
            "guest errno input and strtol sentinel semantics");
    require(call("failure_test") == 73 && errno == ELOOP,
            "Darwin EAGAIN translated to bionic errno");
    require(errno_to_guest(EAGAIN) == bionic::kEAGAIN &&
                errno_to_guest(EDEADLK) == bionic::kEDEADLK &&
                errno_to_guest(ENOSYS) == bionic::kENOSYS &&
                errno_to_guest(ELOOP) == bionic::kELOOP,
            "nonmatching host/guest errno values");
    int results[4]{};
    void *function =
        reinterpret_cast<void *>(loader.symbol(module, "thread_errno").address);
    std::vector<std::thread> threads;
    for (int i = 0; i < 4; ++i)
      threads.emplace_back([&, i] {
        results[i] = int(
            invoke(function, {Argument::integer(700 + i)}).result_integers[0]);
      });
    for (auto &thread : threads)
      thread.join();
    for (int i = 0; i < 4; ++i)
      require(results[i] == 700 + i, "guest errno is isolated per thread");
    size_t personality = 0;
    for (const auto &record : loader.bindings()) {
      if (!record.binding.found || record.binding.abi == "guest-AAPCS64")
        continue;
      ++personality;
      require(record.binding.provider == "libc.so" ||
                  record.binding.provider == "libfixture_host.so",
              "explicit personality provider identity");
    }
    require(personality >= 10,
            "binding evidence must cover the actual imported APIs");
    std::cout
        << "M5_PERSONALITY_PASS memory=1 alloc=1 strtol=1 errno_translation=1 "
           "errno_threads=4 host_errno_preserved=1 nonreturning_contract=1 "
           "managed_allocations=1 realloc_null=1 realloc_payload=1 realloc_failure_retained=1 "
           "calloc_overflow=1 foreign_allocations=1 cross_thread_free=4 link_stubs_executed=0\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
