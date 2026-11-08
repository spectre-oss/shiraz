// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/jump.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <array>
#include <csignal>
#include <cstring>
#include <iostream>
#include <pthread.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: jump-probe fixture-directory");
    auto base = native_personality();
    auto providers = std::move(base);
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libjump-fixture.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return int(int32_t(invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0]));
    };
    for (int value : {0, 37, -7}) {
      errno = 61;
      require(call("jump_value", {Argument::integer(value)}) == (value ? value : 1) && errno == 61,
              "real nested guest longjmp restores stack and coerces zero to one");
    }
    require(call("jump_registers") == 1, "all AAPCS64 preserved GPRs/D registers and SP restored");
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
    auto aborts = [&](const std::function<void()> &action) {
      pid_t child = fork();
      require(child >= 0, "negative jump child");
      if (!child) { std::signal(SIGABRT, [](int) { _exit(97); }); action(); _exit(98); }
      int status;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "unsafe nonlocal jump is rejected before crossing native frames");
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
    require(!capture_boundary_token(), "all successful transitions restore outer boundary state");
    std::cout << "M5_JUMP_PASS nested=1 zero_to_one=1 gpr=11 dreg=8 stack=1 masks=4 unsafe_rejected=5 host_x18_writes=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
