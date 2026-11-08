// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/cxx.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <csignal>
#include <cstring>
#include <iostream>
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
    require(argc == 2, "usage: cxx-probe fixture-directory");
    auto providers = native_personality();
    Loader loader({argv[1]}, std::move(providers));
    auto *module = loader.open("libcxx-fixture.so");
    auto *support = loader.loaded("libstdc++.so");
    auto call = [&](const char *name, const std::vector<Argument> &arguments = {}) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments).result_integers[0];
    };
    auto abi = [&](const char *name, void *guard) {
      return invoke(reinterpret_cast<void *>(loader.symbol(support, name, "LIBC_O").address), {pointer(guard)}).result_integers[0];
    };
    std::atomic<int> passed{0};
    std::vector<std::thread> workers;
    for (int i = 0; i < 8; ++i) workers.emplace_back([&] {
      for (int j = 0; j < 64; ++j) if (int(call("cxx_static_value")) != 73) return;
      ++passed;
    });
    for (auto &worker : workers) worker.join();
    require(passed == 8 && call("cxx_static_constructions") == 1,
            "real compiler guard initializes once with published object contents");
    alignas(8) uint64_t guard = 0xfeedface00000000ull;
    require(abi("__cxa_guard_acquire", &guard) == 1 && guard == 0xfeedface00000100ull,
            "bionic pending word leaves initialized byte clear and upper word intact");
    abi("__cxa_guard_abort", &guard);
    require(guard == 0xfeedface00000000ull && abi("__cxa_guard_acquire", &guard) == 1,
            "aborted initialization can be retried");
    abi("__cxa_guard_release", &guard);
    require(guard == 0xfeedface00000001ull && abi("__cxa_guard_acquire", &guard) == 0,
            "release sets bionic initialized state and preserves upper word");
    // Repeated abort/retry races exercise waiters tied to an attempt rather
    // than a reused guard word, preventing missed wakeups across attempts.
    alignas(8) uint64_t racing = 0;
    std::atomic<int> winners{0}, ready{0};
    require(abi("__cxa_guard_acquire", &racing) == 1, "own initial abort race");
    workers.clear();
    for (int i = 0; i < 8; ++i) workers.emplace_back([&] {
      ++ready;
      if (abi("__cxa_guard_acquire", &racing)) {
        ++winners;
        abi("__cxa_guard_release", &racing);
      }
    });
    while (ready != 8) std::this_thread::yield();
    abi("__cxa_guard_abort", &racing);
    for (auto &worker : workers) worker.join();
    require(winners == 1 && racing == 1, "abort wakes waiters and one retry initializes");
    const char *allocators[] = {"cxx_new", "cxx_new_array", "cxx_nothrow", "cxx_nothrow_array"};
    const char *deleters[] = {"cxx_delete", "cxx_delete_array", "cxx_delete_nothrow", "cxx_delete_nothrow_array"};
    for (int i = 0; i < 4; ++i) {
      auto *memory = reinterpret_cast<void *>(call(allocators[i], {Argument::integer(128, ArgumentType::Int64)}));
      require(memory, "real bionic allocation");
      std::memset(memory, 0xa5, 128);
      call(deleters[i], {pointer(memory)});
      call(deleters[i], {pointer(nullptr)});
    }
    require(!call("cxx_nothrow", {Argument::integer(-1, ArgumentType::Int64)}),
            "nothrow allocation failure returns null");
    auto aborts = [&](const std::function<void()> &action) {
      pid_t child = fork();
      require(child >= 0, "fatal ABI child");
      if (!child) { std::signal(SIGABRT, [](int) { _exit(97); }); action(); _exit(98); }
      int status;
      require(waitpid(child, &status, 0) == child && WIFEXITED(status) && WEXITSTATUS(status) == 97,
              "fatal bionic ABI terminates rather than returning success");
    };
    aborts([&] { call("cxx_new", {Argument::integer(-1, ArgumentType::Int64)}); });
    aborts([&] {
      invoke(reinterpret_cast<void *>(loader.symbol(support, "__cxa_pure_virtual", "LIBC_O").address), {});
    });
    aborts([&] {
      alignas(8) uint64_t recursive = 0;
      abi("__cxa_guard_acquire", &recursive);
      abi("__cxa_guard_acquire", &recursive);
    });
    std::cout << "M5_CXX_PASS compiler_guard=1 threads=8 abort_retry=1 layout=1 allocation=4 nothrow_failure=1 fatal_new=1 pure_virtual=1 recursion_abort=1 host_exception_crossing=0\n";
  } catch (const std::exception &failure) { std::cerr << "FAIL " << failure.what() << '\n'; return 1; }
}
