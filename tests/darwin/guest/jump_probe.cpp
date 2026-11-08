// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/jump.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <array>
#include <csignal>
#include <cstring>
#include <iostream>
#include <memory>
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
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: jump-probe fixture-directory");
    require(!prepared_guest_stack_contains(native_sp()), "unprepared stack query fails closed");
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
