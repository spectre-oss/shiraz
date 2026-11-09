// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/jump.hpp"
#include "shiraz/darwin/guest/boundary_token.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <array>
#include <algorithm>
#include <atomic>
#include <csignal>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <memory>
#include <limits>
#include <pthread.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
extern "C" void shiraz_guest_jump_record(void *, int) noexcept;
extern "C" void shiraz_guest_jump_validate(void *, uintptr_t) noexcept;
static uintptr_t native_sp() {
  uintptr_t value;
  __asm__ volatile("mov %0, sp" : "=r"(value));
  return value;
}
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
static void boundary_reservation_tests() {
  constexpr size_t workers = 8, per_worker = 512, total = workers * per_worker;
  std::atomic<uint64_t> counter{1};
  std::atomic<unsigned> ready{0};
  std::atomic<bool> start{false};
  std::array<std::array<uint64_t, per_worker>, workers> tokens{};
  std::array<std::thread, workers> threads;
  for (size_t worker = 0; worker < workers; ++worker)
    threads[worker] = std::thread([&, worker] {
      ready.fetch_add(1, std::memory_order_release);
      while (!start.load(std::memory_order_acquire)) std::this_thread::yield();
      for (auto &token : tokens[worker]) token = detail::reserve_boundary_token(counter);
    });
  while (ready.load(std::memory_order_acquire) != workers) std::this_thread::yield();
  start.store(true, std::memory_order_release);
  for (auto &thread : threads) thread.join();
  std::array<uint64_t, total> ordered{};
  for (size_t worker = 0; worker < workers; ++worker)
    std::copy(tokens[worker].begin(), tokens[worker].end(), ordered.begin() + worker * per_worker);
  std::sort(ordered.begin(), ordered.end());
  for (size_t index = 0; index < ordered.size(); ++index)
    require(ordered[index] == index + 1, "concurrent boundary reservations are unique without gaps");
  require(counter.load() == total + 1, "boundary reservation counter records every successful token");
  constexpr auto maximum = std::numeric_limits<uint64_t>::max();
  for (uint64_t refused : {uint64_t(0), maximum}) {
    counter.store(refused);
    require(!detail::reserve_boundary_token(counter) && counter.load() == refused,
            "zero/exhausted boundary sequence is unchanged on refusal");
  }
  counter.store(maximum - 1);
  ready.store(0); start.store(false);
  std::array<uint64_t, workers> final{};
  for (size_t worker = 0; worker < workers; ++worker)
    threads[worker] = std::thread([&, worker] {
      ready.fetch_add(1, std::memory_order_release);
      while (!start.load(std::memory_order_acquire)) std::this_thread::yield();
      final[worker] = detail::reserve_boundary_token(counter);
    });
  while (ready.load(std::memory_order_acquire) != workers) std::this_thread::yield();
  start.store(true, std::memory_order_release);
  for (auto &thread : threads) thread.join();
  require(std::count(final.begin(), final.end(), maximum - 1) == 1 &&
              std::count(final.begin(), final.end(), uint64_t(0)) == std::ptrdiff_t(workers - 1) &&
              counter.load() == maximum && !detail::reserve_boundary_token(counter),
          "one final boundary token wins and saturation never wraps");
}
namespace {
static_assert(std::atomic<uint64_t>::is_always_lock_free && std::atomic<unsigned>::is_always_lock_free);
std::array<std::atomic<uint64_t>, 4> signal_tokens{};
std::atomic<unsigned> signal_token_count{0}, signal_calls{0}, signal_errors{0};
void record_signal_token(uint64_t token) noexcept {
  const auto index = signal_token_count.fetch_add(1, std::memory_order_relaxed);
  if (!token || index >= signal_tokens.size()) {
    signal_errors.fetch_add(1, std::memory_order_relaxed);
    return;
  }
  signal_tokens[index].store(token, std::memory_order_relaxed);
}
void boundary_signal_handler(int native) {
  const int saved_errno = errno;
  const auto previous = capture_boundary_token();
  if (!previous) signal_errors.fetch_add(1, std::memory_order_relaxed);
  {
    CaptureBoundary outer;
    const auto outer_token = capture_boundary_token();
    record_signal_token(outer_token);
    if (native == SIGUSR1 && raise(SIGUSR2)) signal_errors.fetch_add(1, std::memory_order_relaxed);
    if (capture_boundary_token() != outer_token) signal_errors.fetch_add(1, std::memory_order_relaxed);
    {
      CaptureBoundary inner;
      const auto token = capture_boundary_token();
      record_signal_token(token);
      if (token == outer_token) signal_errors.fetch_add(1, std::memory_order_relaxed);
    }
    if (capture_boundary_token() != outer_token) signal_errors.fetch_add(1, std::memory_order_relaxed);
  }
  if (capture_boundary_token() != previous) signal_errors.fetch_add(1, std::memory_order_relaxed);
  signal_calls.fetch_add(1, std::memory_order_relaxed);
  errno = saved_errno;
}
struct BoundarySignalScope {
  struct sigaction previous_first{}, previous_second{};
  sigset_t previous_mask{}, selected{};
  bool mask_saved = false, first_installed = false, second_installed = false;
  void install() {
    sigemptyset(&selected); sigaddset(&selected, SIGUSR1); sigaddset(&selected, SIGUSR2);
    require(!pthread_sigmask(SIG_BLOCK, &selected, &previous_mask), "block boundary test signals for setup");
    mask_saved = true;
    struct sigaction action{};
    action.sa_handler = boundary_signal_handler;
    sigemptyset(&action.sa_mask);
    require(!sigaction(SIGUSR1, &action, &previous_first), "install first boundary test signal");
    first_installed = true;
    require(!sigaction(SIGUSR2, &action, &previous_second), "install nested boundary test signal");
    second_installed = true;
    auto active = previous_mask;
    sigdelset(&active, SIGUSR1); sigdelset(&active, SIGUSR2);
    require(!pthread_sigmask(SIG_SETMASK, &active, nullptr), "unblock boundary test signals");
  }
  ~BoundarySignalScope() {
    if (!mask_saved) return;
    if (pthread_sigmask(SIG_BLOCK, &selected, nullptr) ||
        (second_installed && sigaction(SIGUSR2, &previous_second, nullptr)) ||
        (first_installed && sigaction(SIGUSR1, &previous_first, nullptr)) ||
        pthread_sigmask(SIG_SETMASK, &previous_mask, nullptr)) std::abort();
  }
};
} // namespace
static void boundary_signal_tests() {
  // Prewarm this image's native TLV block in ordinary code. The handler below
  // performs no fixture allocation and communicates only through lock-free atoms.
  (void)capture_boundary_storage();
  require(!capture_boundary_token(), "boundary signal test starts outside a guest transition");
  for (auto &token : signal_tokens) token.store(0);
  signal_token_count.store(0); signal_calls.store(0); signal_errors.store(0);
  BoundarySignalScope signals;
  signals.install();
  uint64_t outer_token;
  {
    CaptureBoundary transition;
    outer_token = capture_boundary_token();
    require(!raise(SIGUSR1), "deliver actual nested native boundary signals");
    require(capture_boundary_token() == outer_token, "nested native signals restore the outer boundary");
  }
  require(!capture_boundary_token() && signal_calls.load() == 2 && signal_token_count.load() == 4 &&
              !signal_errors.load(), "nested signal boundaries preserve every interrupted current token");
  std::array<uint64_t, 5> observed{};
  observed[0] = outer_token;
  for (size_t index = 0; index < signal_tokens.size(); ++index) observed[index + 1] = signal_tokens[index].load();
  std::sort(observed.begin(), observed.end());
  require(observed[0] && std::adjacent_find(observed.begin(), observed.end()) == observed.end(),
          "outer and signal boundary tokens are all distinct");
  std::cout << "M5_BOUNDARY_TOKEN_PASS concurrent_threads=8 tokens=4096 saturation=1 "
               "nested_native_signals=2 async_stress=0 lifetime_proof=0\n";
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: jump-probe fixture-directory");
    require(!prepared_guest_stack_contains(native_sp()), "unprepared stack query fails closed");
    boundary_reservation_tests();
    auto base = native_personality();
    auto providers = std::move(base);
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libjump-fixture.so");
    const auto high = reinterpret_cast<uintptr_t>(pthread_get_stackaddr_np(pthread_self()));
    const auto low = high - pthread_get_stacksize_np(pthread_self());
    require(prepared_guest_stack_contains(low) && prepared_guest_stack_contains(high - 1) &&
                !prepared_guest_stack_contains(low - 1) && !prepared_guest_stack_contains(high),
            "prepared native extent has exact inclusive/exclusive endpoints");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return int(int32_t(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0]));
    };
    for (int value : {0, 37, -7}) {
      errno = 61;
      require(call("jump_value", {Argument::integer(value)}) == (value ? value : 1) && errno == 61,
              "real nested guest longjmp restores stack and coerces zero to one");
    }
    require(call("jump_registers") == 1, "all AAPCS64 preserved GPRs/D registers and SP restored");
    int worker_result = 0;
    std::thread worker([&] {
      prepare_guest_tls();
      if (prepared_guest_stack_contains(native_sp()))
        worker_result = call("jump_value", {Argument::integer(37)});
    });
    worker.join();
    require(worker_result == 37, "worker uses its own prepared native stack extent");
    sigset_t original{}, blocked{};
    require(!pthread_sigmask(SIG_SETMASK, nullptr, &original), "read original signal mask");
    blocked = original;
    sigaddset(&blocked, SIGUSR1);
    void *change = closure([](Capture &) {
      HostErrno preserve;
      sigset_t current{};
      require(!pthread_sigmask(SIG_SETMASK, nullptr, &current), "read callback signal mask");
      sigdelset(&current, SIGUSR1);
      require(!pthread_sigmask(SIG_SETMASK, &current, nullptr), "change native signal mask");
    });
    for (int mode = 0; mode < 4; ++mode) {
      require(!pthread_sigmask(SIG_SETMASK, &blocked, nullptr), "set initial blocked mask");
      require(call("jump_mask", {pointer(change), Argument::integer(mode)}) == 41, "signal-mask jump returns requested value");
      sigset_t current{};
      require(!pthread_sigmask(SIG_SETMASK, nullptr, &current) &&
                  sigismember(&current, SIGUSR1) == ((mode == 1 || mode == 3) ? 1 : 0),
              "setjmp/sigsetjmp really restore mask; underscore and mask-zero variants do not");
    }
    require(!pthread_sigmask(SIG_SETMASK, &original, nullptr), "restore original signal mask");
    boundary_signal_tests();
    auto aborts = [&](const std::function<void()> &action, const char *reason = nullptr) {
      int descriptors[2];
      require(!pipe(descriptors), "negative jump diagnostic pipe");
      pid_t child = fork();
      require(child >= 0, "negative jump child");
      if (!child) {
        close(descriptors[0]);
        if (dup2(descriptors[1], STDERR_FILENO) < 0) _exit(96);
        close(descriptors[1]);
        std::signal(SIGABRT, [](int) { _exit(97); }); action(); _exit(98);
      }
      close(descriptors[1]);
      std::string diagnostic;
      char bytes[512];
      ssize_t count;
      while ((count = read(descriptors[0], bytes, sizeof(bytes))) > 0) {
        diagnostic.append(bytes, size_t(count));
        if (diagnostic.size() > 8192) { kill(child, SIGKILL); break; }
      }
      close(descriptors[0]);
      int status;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "unsafe nonlocal jump is rejected before crossing native frames");
      require(count == 0, "bounded complete negative jump diagnostic");
      if (reason)
        require(diagnostic == std::string("SHIRAZ_GUEST_FATAL nonlocal jump: ") + reason + "\n",
                "negative jump reached the specific stack extent refusal");
      std::cerr << diagnostic;
    };
    alignas(8) std::array<uint64_t,32> saved{};
    require(!call("jump_store", {pointer(saved.data())}), "store context from a completed guest invocation");
    aborts([&] { call("jump_target", {pointer(saved.data()), Argument::integer(1)}); });
    aborts([&] {
      std::thread other([&] { call("jump_target", {pointer(saved.data()), Argument::integer(1)}); });
      other.join();
    });
    aborts([&] { saved[2] ^= 4; call("jump_target", {pointer(saved.data()), Argument::integer(1)}); });
    aborts([&] { alignas(8) uint64_t empty[32]{}; call("jump_target", {pointer(empty), Argument::integer(1)}); });
    void *bounce = closure([&](Capture &capture) {
      CallCursor cursor(capture, Convention::Aapcs64);
      void *buffer = cursor.read<void *>();
      // This is deliberately a new guest invocation underneath a live C++
      // provider closure. Jumping to the outer guest would skip its RAII.
      call("jump_target", {pointer(buffer), Argument::integer(3)});
    });
    aborts([&] { call("jump_cross", {pointer(bounce)}); });
    auto heap = std::make_unique<std::array<uint64_t, 32>>();
    for (uintptr_t invalid : {low - 16, high + 16, reinterpret_cast<uintptr_t>(heap.get())}) {
      aborts([&] {
        CaptureBoundary transition;
        alignas(8) uint64_t context[32]{};
        context[2] = 1;
        context[3] = invalid;
        shiraz_guest_jump_record(context, 0);
      }, "saved SP outside prepared native stack extent");
    }
    aborts([&] {
      CaptureBoundary transition;
      alignas(8) uint64_t context[32]{};
      context[2] = 1;
      context[3] = native_sp();
      shiraz_guest_jump_record(context, 0);
      shiraz_guest_jump_validate(context, low - 16);
    }, "saved/current SP outside prepared native stack extent");
    require(!capture_boundary_token(), "all successful transitions restore outer boundary state");
    std::cout << "M5_JUMP_PASS nested=1 zero_to_one=1 gpr=11 dreg=8 stack=1 masks=4 unsafe_rejected=9 native_stack_bounds=1 worker_stack=1 host_x18_writes=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
